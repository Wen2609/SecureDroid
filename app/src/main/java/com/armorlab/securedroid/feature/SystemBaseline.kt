package com.armorlab.securedroid.feature

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.provider.Settings
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.scan.ThreatLevel
import java.io.File

/** 系统安全基线检查(参考 CIS Android 基线思路精简) */
object SystemBaseline {

    data class Check(
        val name: String, val ok: Boolean,
        val level: ThreatLevel, val detail: String, val advice: String
    )

    fun checks(context: Context): List<Check> {
        val list = mutableListOf<Check>()
        val props = getProps(listOf("ro.debuggable", "ro.secure", "ro.build.tags"))

        val debuggable = props["ro.debuggable"] == "1"
        list.add(Check("调试内核已关闭(ro.debuggable)", !debuggable,
            if (debuggable) ThreatLevel.HIGH else ThreatLevel.LOW,
            "ro.debuggable = " + props["ro.debuggable"],
            if (debuggable) "调试内核允许任意进程 attach,请刷回 user 版系统" else "保持现状"))

        val secure = props["ro.secure"] == "1"
        list.add(Check("ADB 守护受限(ro.secure)", secure,
            if (secure) ThreatLevel.LOW else ThreatLevel.MEDIUM,
            "ro.secure = " + props["ro.secure"], "建议保持 ro.secure=1"))

        val tags = props["ro.build.tags"] ?: ""
        val testKeys = tags.contains("test-keys")
        list.add(Check("系统签名为正式密钥(release-keys)", !testKeys,
            if (testKeys) ThreatLevel.HIGH else ThreatLevel.LOW,
            "ro.build.tags = " + tags,
            if (testKeys) "test-keys 系统可被随意刷入组件,建议刷入官方 release 镜像" else "保持现状"))

        val enforce = try { File("/sys/fs/selinux/enforce").readText().trim() } catch (_: Exception) { "1" }
        list.add(Check("SELinux 强制模式(Enforcing)", enforce == "1",
            if (enforce == "1") ThreatLevel.LOW else ThreatLevel.HIGH,
            "SELinux = " + (if (enforce == "1") "Enforcing" else "Permissive"),
            if (enforce != "1") "宽容模式大幅削弱安全边界,建议恢复 Enforcing" else "保持现状"))

        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        list.add(Check("USB 调试已关闭", !adb,
            if (adb) ThreatLevel.MEDIUM else ThreatLevel.LOW,
            "USB 调试 = " + (if (adb) "开启" else "关闭"),
            if (adb) "不插电脑时建议关闭 USB 调试,防充电桩攻击" else "保持现状"))

        val dev = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        list.add(Check("开发者选项状态", true,
            ThreatLevel.LOW,
            "开发者选项 = " + (if (dev) "开启" else "关闭"), "日常使用可关闭"))

        val km = context.getSystemService(KeyguardManager::class.java)
        val hasLock = km?.isDeviceSecure == true
        list.add(Check("已设置安全屏幕锁", hasLock,
            if (hasLock) ThreatLevel.LOW else ThreatLevel.HIGH,
            if (hasLock) "已设置 PIN/图案/生物识别" else "未设置屏幕锁",
            if (!hasLock) "设置至少 6 位密码或生物识别,防物理接触解锁" else "保持现状"))

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val encStatus = dpm?.storageEncryptionStatus ?: 0
        val encrypted = encStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
            encStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
        list.add(Check("设备存储已加密", encrypted,
            if (encrypted) ThreatLevel.LOW else ThreatLevel.MEDIUM,
            "加密状态 = " + encStatus, "现代设备默认开启,如未开启请加密存储"))

        // Android 8.0(API 26)起全局"未知来源"开关已移除,安装未知应用改为按应用授予
        // REQUEST_INSTALL_PACKAGES;全局设置 INSTALL_NON_MARKET_APPS 同版废弃(恒读 0,
        // 旧检查在 minSdk 26 上形同虚设)。改为审计声明该权限的第三方应用面。
        val installers = PackageSnapshot.installedPackages(context, PackageManager.GET_PERMISSIONS)
            .filter { info ->
                val app = info.applicationInfo ?: return@filter false
                (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    info.requestedPermissions?.contains(
                        android.Manifest.permission.REQUEST_INSTALL_PACKAGES
                    ) == true
            }
        val installerNames = installers.take(3)
            .mapNotNull { it.applicationInfo?.let { app -> PackageSnapshot.label(context, app) } }
        list.add(
            Check(
                "安装未知应用权限收口", installers.isEmpty(),
                if (installers.isEmpty()) ThreatLevel.LOW else ThreatLevel.MEDIUM,
                "声明 REQUEST_INSTALL_PACKAGES 的第三方应用 " + installers.size + " 个" +
                    (if (installerNames.isEmpty()) "" else ":" + installerNames.joinToString("、")),
                if (installers.isEmpty()) "保持现状"
                else "持有该权限的应用可绕过商店直接安装 APK,是恶意软件常见投递通道;在 系统设置 → 应用 → 特殊权限 中逐个核减"
            )
        )

        return list
    }

    private fun getProps(names: List<String>): Map<String, String> {
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java)
            names.associateWith { (get.invoke(null, it) as? String) ?: "" }
        } catch (_: Exception) {
            val out = HashMap<String, String>()
            for (n in names) {
                var p: Process? = null
                try {
                    p = ProcessBuilder("getprop", n).start()
                    val text = p.inputStream.bufferedReader().use { it.readText() }
                    if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) out[n] = text.trim()
                    else { p.destroy(); out[n] = "" }
                } catch (_: Exception) {
                    out[n] = ""
                    p?.destroy()
                }
            }
            out
        }
    }
}
