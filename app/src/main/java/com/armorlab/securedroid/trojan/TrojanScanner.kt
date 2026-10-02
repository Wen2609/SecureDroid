package com.armorlab.securedroid.trojan

import android.content.Context
import android.content.pm.PackageManager
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.scan.SignatureDatabase
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.vscan.CustomRules
import com.armorlab.securedroid.vscan.DexVerdictCache
import com.armorlab.securedroid.vscan.HashCache
import java.io.File

/**
 * 木马查杀编排器,四引擎并行结论:
 * 1. 内置哈希特征库(SignatureDatabase)
 * 2. ClamAV 兼容整文件哈希签名(.hsb)
 * 3. DEX 行为规则(YARA 风格)
 * 4. ClamAV 兼容字节码特征(.ndb,扫描 dex / so)
 */
object TrojanScanner {

    data class Detection(
        val engine: String,
        val name: String,
        val level: ThreatLevel,
        val detail: String
    )

    data class Report(
        val packageName: String,
        val appName: String,
        val detections: List<Detection>,
        /** APK 指纹(供查杀历史落库,原实现写空串) */
        val sha256: String = "",
        /** 权限风险分 0-100(与 ScannerEngine.permissionRiskScore 同一语义) */
        val riskScore: Int = 0
    ) {
        val isInfected: Boolean get() = detections.isNotEmpty()
        val worstLevel: ThreatLevel?
            get() = detections.maxByOrNull { it.level.ordinal }?.level
    }

    fun scanPackage(context: Context, pkg: String): Report {
        // 性能:单包信息 / 应用标签走快照层,避免同一应用在一次扫描中被反复查询
        val info = PackageSnapshot.packageInfo(context, pkg, PackageManager.GET_PERMISSIONS)
            ?: return Report(pkg, pkg, emptyList())
        val appInfo = info.applicationInfo
        val appName = PackageSnapshot.label(context, appInfo)
        val detections = mutableListOf<Detection>()
        val apkPath = appInfo.sourceDir
        var sha = ""
        if (apkPath != null) {
            val apkFile = File(apkPath)
            // 哈希缓存:APK 未更新直接复用指纹,免重复读取大文件
            sha = com.armorlab.securedroid.vscan.HashCache.cachedSha256(
                context, apkPath, info.lastUpdateTime, apkFile.length()
            )
            val isSystemApp = (appInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

            // 引擎 1:内置哈希特征库
            SignatureDatabase.lookup(sha)?.let {
                detections.add(Detection("内置特征库", it.name, it.level, it.description))
            }

            // 引擎 2:ClamAV .hsb 整文件哈希
            ClamAvSignatures.matchHash(sha, apkFile.length())?.let {
                detections.add(Detection("ClamAV 签名", it.first, ThreatLevel.HIGH, it.second))
            }

            if (!isSystemApp) {
                // 引擎 3:DEX 行为规则(按指纹缓存;系统应用跳过以降噪提速)
                val cached = DexVerdictCache.cachedHits(context, sha)
                if (cached != null) {
                    for ((name, level) in cached) {
                        detections.add(Detection("行为规则", name, level, "(指纹缓存)历史行为命中"))
                    }
                } else {
                    val strings = DexScanner.dexStringsFromApk(apkPath)
                    val dets = BehaviorRules.match(strings, CustomRules.asRules(context))
                    // 引擎 4:ClamAV .ndb 字节码特征(与行为判定一并纳入指纹缓存)
                    val clam = ClamAvSignatures.scanApk(apkPath)
                    detections.addAll(dets)
                    detections.addAll(clam)
                    DexVerdictCache.store(context, sha, dets + clam)
                }
            }
        }
        // 历史记录要真实指纹 + 风险分:定时查杀与手动查杀共用本路径,不再落空值
        return Report(pkg, appName, detections, sha, PermissionAuditor.scoreFor(info).first)
    }
}
