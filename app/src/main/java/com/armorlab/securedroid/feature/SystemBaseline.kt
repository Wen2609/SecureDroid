package com.armorlab.securedroid.feature

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.KeyguardManager
import android.provider.Settings
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

        val unknown = Settings.Secure.getInt(context.contentResolver, "install_non_market_apps", 0) == 1
        list.add(Check("未放宽未知来源安装", !unknown,
            if (unknown) ThreatLevel.MEDIUM else ThreatLevel.LOW,
            "未知来源 = " + (if (unknown) "允许" else "禁止"),
            if (unknown) "按应用粒度授予安装权限,避免全局放开" else "保持现状"))

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
                try {
                    val p = ProcessBuilder("getprop", n).start()
                    out[n] = p.inputStream.bufferedReader().readText().trim()
                    p.waitFor()
                } catch (_: Exception) { out[n] = "" }
            }
            out
        }
    }
}
