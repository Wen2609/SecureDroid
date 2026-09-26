package com.armorlab.securedroid.root

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.armorlab.securedroid.scan.ThreatLevel
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
 * 3. 第三方管理员:扫描其 DEX 是否组合使用 lockNow / resetPassword / wipeData,
 *    组合命中(>=2)判定为锁机木马(CRITICAL),否则标记为可疑第三方管理员(MEDIUM);
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

    private val lockApis = listOf("lockNow", "resetPassword", "wipeData")

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
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (_: Exception) { pkg }

            // 扫描该管理员的 DEX,判定锁机行为组合
            var hits = mutableListOf<String>()
            try {
                val apk = pm.getApplicationInfo(pkg, 0).sourceDir
                if (apk != null) {
                    val strings = DexScanner.dexStringsFromApk(apk)
                    for (api in lockApis) {
                        if (strings.any { it.contains(api) }) hits.add(api)
                    }
                }
            } catch (_: Exception) {
            }

            val isLocker = hits.size >= 2
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
                    fixCommand = "dpm remove-active-admin '" + compName +
                        "' ; pm uninstall --user 0 '" + pkg + "'",
                    fixLabel = "解除管理员并卸载"
                )
            )
        }
        return findings
    }
}
