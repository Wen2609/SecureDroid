package com.armorlab.securedroid.trojan

import android.content.Context
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.scan.ThreatLevel
import java.io.File

/**
 * Rootkit / 提权后门检测,检查项参考开源工具 rkhunter 与 chkrootkit,
 * 并适配 Android 的分区与挂载布局(思路借鉴,未复用代码):
 * 1. 已知 su 二进制路径(rkhunter: known rootkit files)
 * 2. Magisk / KernelSU 隐藏框架指纹
 * 3. /proc/mounts: magisk 覆盖挂载、/system 可写挂载(chkrootkit: suspicious mounts)
 * 4. SELinux 宽容模式
 * 5. 已安装 root 管理类应用
 */
object RootkitDetector {

    data class RootFinding(
        val name: String,
        val level: ThreatLevel,
        val detail: String,
        val suggestion: String
    )

    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/system/sd/xbin/su", "/vendor/bin/su", "/su/bin/su",
        "/data/local/xbin/su", "/data/local/bin/su"
    )

    private val magiskPaths = listOf(
        "/sbin/.magisk", "/debug_ramdisk", "/data/adb/magisk",
        "/cache/.disable_magisk", "/data/adb/ksu", "/data/adb/ksud"
    )

    private val rootManagerPkgs = setOf(
        "com.topjohnwu.magisk", "eu.chainfire.supersu",
        "com.koushikdutta.superuser", "com.thirdparty.superuser",
        "com.noshufou.android.su", "me.weishu.exp"
    )

    fun detect(context: Context): List<RootFinding> {
        val findings = mutableListOf<RootFinding>()

        // 1. su 二进制
        for (p in suPaths) {
            if (p in found) findings.add(
                RootFinding(
                    "Rootkit.SuBinary", ThreatLevel.HIGH,
                    "发现 su 二进制: " + p,
                    "设备已具备 root 能力;确认是否本人操作,并排查未知授权记录"
                )
            )
        }

        // 1+2 预探测:su 路径直接 File.exists,不可读路径批量单次 su 兜底
        val found = HashSet<String>()
        val needSu = mutableListOf<String>()
        for (p in suPaths + magiskPaths) {
            if (exists(p)) found.add(p) else needSu.add(p)
        }
        if (needSu.isNotEmpty()) {
            val probeOut = ShellBridge.runSu(
                needSu.joinToString(" ") { "[ -e '" + it + "' ] && echo '" + it + "';" },
                15_000L
            ) ?: ""
            probeOut.lines().map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { found.add(it) }
        }

        // 2. Magisk / KernelSU 隐藏框架
        for (p in magiskPaths) {
            if (p in found) findings.add(
                RootFinding(
                    "Rootkit.MagiskLike", ThreatLevel.HIGH,
                    "发现 root 框架指纹: " + p,
                    "检测到 Magisk/KernelSU 类框架痕迹;systemless 挂载可隐藏模块,建议审查已装模块"
                )
            )
        }

        // 3. /proc/mounts 可疑挂载
        val mounts = try { File("/proc/mounts").readText() } catch (_: Exception) { "" }
        if (mounts.isNotEmpty()) {
            if (mounts.contains("magisk")) findings.add(
                RootFinding(
                    "Rootkit.MagiskMount", ThreatLevel.CRITICAL,
                    "/proc/mounts 中发现 magisk 挂载痕迹",
                    "存在 systemless 覆盖挂载;检查 Magisk 模块列表,移除未知模块"
                )
            )
            if (Regex("(^|\\s)/system\\s+.*\\boverlay\\b").containsMatchIn(mounts)) findings.add(
                RootFinding(
                    "Rootkit.SystemOverlay", ThreatLevel.CRITICAL,
                    "/system 分区被 overlay 方式覆盖挂载",
                    "系统分区疑似被篡改;建议备份后恢复出厂并刷入官方镜像"
                )
            )
            if (Regex("\\s/system\\s+\\S+\\s+\\S*rw[\\s,]").containsMatchIn(mounts)) findings.add(
                RootFinding(
                    "Policy.SystemWritable", ThreatLevel.MEDIUM,
                    "/system 分区以可写(rw)方式挂载",
                    "系统分区可写将大幅降低篡改门槛;确认是否开启了系统可写模式"
                )
            )
        }

        // 4. SELinux 宽容模式
        val enforce = try { File("/sys/fs/selinux/enforce").readText().trim() } catch (_: Exception) { "1" }
        if (enforce == "0") findings.add(
            RootFinding(
                "Policy.SELinuxPermissive", ThreatLevel.MEDIUM,
                "SELinux 处于宽容(permissive)模式,强制访问控制失效",
                "在开发者选项或通过 root 关闭了 SELinux;建议恢复 Enforcing"
            )
        )

        // 5. root 管理类应用
        val pm = context.packageManager
        for (pkg in rootManagerPkgs) {
            try {
                pm.getPackageInfo(pkg, 0)
                findings.add(
                    RootFinding(
                        "App.RootManager", ThreatLevel.LOW,
                        "安装了 root 管理应用: " + pkg,
                        "root 管理器是提权入口;若非本人安装请立即卸载并全盘查杀"
                    )
                )
            } catch (_: Exception) {
            }
        }

        return findings
    }

    private fun exists(path: String): Boolean =
        try { File(path).exists() } catch (_: Exception) { false }
}
