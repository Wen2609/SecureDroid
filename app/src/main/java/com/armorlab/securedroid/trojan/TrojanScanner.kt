package com.armorlab.securedroid.trojan

import android.content.Context
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.scan.SignatureDatabase
import com.armorlab.securedroid.scan.ThreatLevel
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
        val detections: List<Detection>
    ) {
        val isInfected: Boolean get() = detections.isNotEmpty()
        val worstLevel: ThreatLevel?
            get() = detections.maxByOrNull { it.level.ordinal }?.level
    }

    fun scanPackage(context: Context, pkg: String): Report {
        val pm = context.packageManager
        val info = try {
            pm.getPackageInfo(pkg, 0)
        } catch (e: Exception) {
            return Report(pkg, pkg, emptyList())
        }
        val appInfo = info.applicationInfo
        val appName = appInfo?.loadLabel(pm)?.toString() ?: pkg
        val detections = mutableListOf<Detection>()
        val apkPath = appInfo?.sourceDir
        if (apkPath != null) {
            val apkFile = File(apkPath)
            val sha = ScannerEngine.hashFile(apkPath)

            // 引擎 1:内置哈希特征库
            SignatureDatabase.lookup(sha)?.let {
                detections.add(Detection("内置特征库", it.name, it.level, it.description))
            }

            // 引擎 2:ClamAV .hsb 整文件哈希
            ClamAvSignatures.matchHash(sha, apkFile.length())?.let {
                detections.add(Detection("ClamAV 签名", it.first, ThreatLevel.HIGH, it.second))
            }

            // 引擎 3:DEX 行为规则
            val strings = DexScanner.dexStringsFromApk(apkPath)
            detections.addAll(BehaviorRules.match(strings))

            // 引擎 4:ClamAV .ndb 字节码特征
            detections.addAll(ClamAvSignatures.scanApk(apkPath))
        }
        return Report(pkg, appName, detections)
    }
}
