package com.armorlab.securedroid.root

import com.armorlab.securedroid.core.PackageSnapshot
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.armorlab.securedroid.scan.MultiPatternMatcher
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.scan.TokenMatch
import com.armorlab.securedroid.trojan.DexScanner

/**
 * 锁机软件检测器。
 *
 * 锁机木马的标准手法:诱导激活设备管理员(Device Admin)后调用
 * lockNow() 锁屏、resetPassword() 重置锁屏密码进行勒索。
 *
 * 检测逻辑:
 * 1. 枚举当前所有设备管理员(DevicePolicyManager.activeAdmins);
 * 2. 系统内置管理员(如企业/厂商组件)跳过;
 * 3. 第三方管理员:扫描其 DEX,必须命中 resetPassword(重置锁屏密码勒索)才判锁机木马
 *    (CRITICAL),否则标记为可疑第三方管理员(MEDIUM);
 * 4. 生成处置命令:dpm remove-active-admin + pm uninstall --user 0(root 可用)。
 */
object LockerDetector {

    data class LockerFinding(
        val title: String,
        val sub: String,
        val detail: String,
        val level: ThreatLevel,
        val suggestion: String,
        val fixCommand: String?,
        val fixLabel: String?
    )

    /** 真正的勒索落点:重置锁屏密码。lockNow / wipeData 是正规设备管理 API,单独出现不算锁机 */
    private const val RESET_PASSWORD = "resetPassword"

    private val lockApis = listOf("lockNow", RESET_PASSWORD, "wipeData")

    /**
     * 锁机判定:必须命中 resetPassword(重置密码勒索),而不是"锁机 API 命中 2 项"。
     * 实测:厂商设备管理 / 找回类组件同时带 lockNow + wipeData,按数量判定会直接误报。
     */
    internal fun lockerVerdict(hits: List<String>): Boolean = hits.contains(RESET_PASSWORD)

    /** 性能:单遍多模式匹配,替代"每个 API 都对整个 dex 字符串集做一次全量扫描" */
    private val lockMatcher = MultiPatternMatcher(lockApis)

    fun scan(context: Context): List<LockerFinding> {
        val findings = mutableListOf<LockerFinding>()
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
            ?: return findings
        val admins = try { dpm.activeAdmins } catch (_: Exception) { null } ?: return findings
        val pm = context.packageManager

        for (comp in admins) {
            val pkg = comp.packageName ?: continue
            if (pkg == context.packageName) continue

            // 系统内置管理员跳过(厂商/企业组件)
            val isSystem = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) { false }
            if (isSystem) continue

            val label = try {
                PackageSnapshot.labelFor(context, pkg)
            } catch (_: Exception) { pkg }

            // 扫描该管理员的 DEX,判定锁机行为组合
            val hits = mutableListOf<String>()
            try {
                val apk = pm.getApplicationInfo(pkg, 0).sourceDir
                if (apk != null) {
                    val strings = DexScanner.dexStringsFromApk(apk)
                    val matched = lockMatcher.scan(strings)
                    for (i in lockApis.indices) {
                        // 位图预筛 + 词边界复核:lockNow 不再命中 lockNowInternal 之类的子串
                        if (matched[i] && TokenMatch.occursIn(strings, lockApis[i])) hits.add(lockApis[i])
                    }
                }
            } catch (_: Exception) {
            }

            val isLocker = lockerVerdict(hits)
            val compName = comp.flattenToShortString()
            findings.add(
                LockerFinding(
                    title = (if (isLocker) "Locker.Ransom.Admin" else "Admin.ThirdParty") +
                        " · " + label,
                    sub = pkg + " · " + compName,
                    detail = if (hits.isEmpty())
                        "第三方应用持有设备管理员权限"
                    else
                        "设备管理员: " + compName + " · 锁屏相关 API: " + hits.joinToString(", "),
                    level = if (isLocker) ThreatLevel.CRITICAL else ThreatLevel.MEDIUM,
                    suggestion = if (isLocker)
                        "疑似锁机木马:锁屏后重置密码进行勒索;命令将先解除管理员再卸载(root)"
                    else
                        "确认是否本人启用的找回/管控类应用;非本人启用请移除管理员并卸载",
                    fixCommand = "dpm remove-active-admin " + ShellBridge.quote(compName) +
                        " ; pm uninstall --user 0 " + ShellBridge.quote(pkg),
                    fixLabel = "解除管理员并卸载"
                )
            )
        }
        return findings
    }
}
