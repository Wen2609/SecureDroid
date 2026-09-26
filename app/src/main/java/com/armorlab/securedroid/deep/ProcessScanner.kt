package com.armorlab.securedroid.deep

import android.content.Context
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter
import java.io.File
import java.util.regex.Pattern

/**
 * 运行内存进程检测(Root 下做到极致,无 Root 降级为本应用可见范围):
 * 1. 双视角枚举:普通视角 /proc 列表 + root ps -A 全量列表,差集 = 隐匿进程(Rootkit);
 * 2. 每进程 exe 解析(readlink /proc/N/exe):已删除可执行文件、临时目录可执行、
 *    root 身份运行 /data 程序、伪装核心系统进程 / com.android 包名;
 * 3. 匿名可执行内存段(rwxp):系统进程出现即疑似代码注入;
 * 4. CPU 双采样:/proc/N/stat utime+stime,高占用提示挖矿。
 */
object ProcessScanner {

    private val coreNames = setOf(
        "init", "system_server", "zygote", "zygote64", "surfaceflinger",
        "servicemanager", "logd", "vold", "netd", "installd", "lmkd",
        "audioserver", "cameraserver", "hwservicemanager", "ueventd"
    )

    private val suspiciousExeDirs = listOf(
        "/data/local/tmp", "/data/local", "/sdcard",
        "/storage/emulated/0", "/dev"
    )

    private class Proc(val pid: Int, val user: String, val ppid: Int, val name: String)

    fun scan(context: Context): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()
        val rootMode = RootGuard.isRootMode(context)

        val directPids = try {
            File("/proc").list { _, n -> n.all { c -> c.isDigit() } }
                ?.map { it.toInt() }?.toSet()
        } catch (_: Exception) { null } ?: emptySet()

        val psOut = if (rootMode) ShellBridge.runSu("ps -A", 20_000L) ?: "" else localPs()
        val procs = parsePs(psOut)
        val allPids = procs.map { it.pid }.toSet()

        // 隐匿进程:root 视角存在、普通视角不可见
        val hidden = allPids - directPids

        val exeMap = if (rootMode) readlinkBatch() else emptyMap()
        val rwxSet = if (rootMode) rwxBatch() else emptySet()

        val cpuA = if (rootMode) statSample() else emptyMap()
        try { Thread.sleep(800) } catch (_: Exception) { }
        val cpuB = if (rootMode) statSample() else emptyMap()

        val pm = context.packageManager
        for (p in procs) {
            val exe = exeMap[p.pid]
            val label = resolveLabel(pm, p)
            val dets = mutableListOf<Triple<String, ThreatLevel, String>>()

            if (p.pid in hidden) {
                dets.add(Triple("Proc.Hidden", ThreatLevel.CRITICAL,
                    "进程对普通视角隐藏(root ps 可见),疑似 Rootkit 隐匿"))
            }
            if (exe != null) {
                if (exe.contains("(deleted)")) {
                    dets.add(Triple("Proc.DeletedExe", ThreatLevel.CRITICAL,
                        "可执行文件已被删除但仍驻留内存运行: " + exe))
                } else if (suspiciousExeDirs.any { exe.startsWith(it) }) {
                    dets.add(Triple("Proc.TmpExec", ThreatLevel.CRITICAL,
                        "临时目录中的可执行文件正在运行: " + exe))
                }
                if (p.user == "root" && exe.startsWith("/data")) {
                    dets.add(Triple("Proc.RootDataExec", ThreatLevel.HIGH,
                        "root 身份运行 /data 下程序: " + exe))
                }
                if (p.name in coreNames &&
                    !(exe.startsWith("/system") || exe.startsWith("/apex"))) {
                    dets.add(Triple("Proc.SpoofedCore", ThreatLevel.HIGH,
                        "伪装核心系统进程名但可执行文件不在系统分区: " + exe))
                }
                if (p.name.startsWith("com.android.") &&
                    !(exe.startsWith("/system") || exe.startsWith("/apex"))) {
                    dets.add(Triple("Proc.SpoofedPackage", ThreatLevel.HIGH,
                        "伪装 com.android 包名但可执行文件不在系统分区: " + exe))
                }
                if (p.pid in rwxSet && exe.startsWith("/system")) {
                    dets.add(Triple("Proc.RwxInject", ThreatLevel.HIGH,
                        "系统进程出现匿名可执行内存段(rwxp),疑似代码注入"))
                }
            }
            val a = cpuA[p.pid]
            val b = cpuB[p.pid]
            if (a != null && b != null && b > a) {
                val pct = (b - a) * 100.0 / 80.0
                if (pct > 40) {
                    dets.add(Triple("Proc.HighCpu", ThreatLevel.MEDIUM,
                        "CPU 持续占用约 " + pct.toInt() + "%(疑似挖矿或异常任务)"))
                }
            }

            for (d in dets) {
                val fixable = d.second == ThreatLevel.CRITICAL || d.second == ThreatLevel.HIGH
                items.add(
                    TrojanAdapter.UiItem(
                        title = d.first + " · " + (label ?: p.name),
                        sub = "PID " + p.pid + " · USER " + p.user + " · " + (exe ?: p.name),
                        detail = d.third,
                        level = d.second,
                        suggestion = if (fixable) "确认后可终止该进程;若反复出现请执行全盘文件系统查杀" else null,
                        uninstallPkg = null,
                        fixCommand = if (fixable) "kill -9 " + p.pid else null,
                        fixLabel = "终止进程"
                    )
                )
            }
        }
        return items
    }

    private fun parsePs(out: String): List<Proc> {
        val list = mutableListOf<Proc>()
        var first = true
        for (line0 in out.lines()) {
            val line = line0.trim()
            if (line.isEmpty()) continue
            if (first) { first = false; if (line.startsWith("USER")) continue }
            val f = line.split(Regex("\\s+"), limit = 9)
            if (f.size < 9) continue
            val pid = f[1].toIntOrNull() ?: continue
            list.add(Proc(pid, f[0], f[2].toIntOrNull() ?: 0, f[8]))
        }
        return list
    }

    private fun localPs(): String = try {
        val p = ProcessBuilder("ps", "-A").start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (_: Exception) { "" }

    private fun readlinkBatch(): Map<Int, String> {
        val cmd = "for p in \$(ls /proc); do case \"\$p\" in *[!0-9]*) continue;; esac; " +
            "printf \"%s=\" \"\$p\"; readlink \"/proc/\$p/exe\" 2>/dev/null || echo \"-\"; done"
        val out = ShellBridge.runSu(cmd, 30_000L) ?: return emptyMap()
        val map = HashMap<Int, String>()
        for (line in out.lines()) {
            val i = line.indexOf('=')
            if (i <= 0) continue
            val pid = line.substring(0, i).toIntOrNull() ?: continue
            val exe = line.substring(i + 1).trim()
            if (exe != "-" && exe.isNotEmpty()) map[pid] = exe
        }
        return map
    }

    private fun rwxBatch(): Set<Int> {
        val cmd = "for p in \$(ls /proc); do case \"\$p\" in *[!0-9]*) continue;; esac; " +
            "grep -q \"rwxp\" \"/proc/\$p/maps\" 2>/dev/null && echo \"R\$p\"; done"
        val out = ShellBridge.runSu(cmd, 30_000L) ?: return emptySet()
        return out.lines()
            .filter { it.startsWith("R") }
            .mapNotNull { it.removePrefix("R").toIntOrNull() }
            .toSet()
    }

    private fun statSample(): Map<Int, Long> {
        val cmd = "for p in \$(ls /proc); do case \"\$p\" in *[!0-9]*) continue;; esac; " +
            "head -1 \"/proc/\$p/stat\" 2>/dev/null | sed \"s/^/P\$p /\"; done"
        val out = ShellBridge.runSu(cmd, 20_000L) ?: return emptyMap()
        val map = HashMap<Int, Long>()
        for (line in out.lines()) {
            if (!line.startsWith("P")) continue
            val sp = line.split(' ', limit = 2)
            val pid = sp[0].removePrefix("P").toIntOrNull() ?: continue
            val rest = sp.getOrNull(1)?.substringAfterLast(')') ?: continue
            val f = rest.trim().split(Regex("\\s+"))
            if (f.size < 13) continue
            val ut = f[11].toLongOrNull() ?: 0L
            val st = f[12].toLongOrNull() ?: 0L
            map[pid] = ut + st
        }
        return map
    }

    private fun resolveLabel(pm: android.content.pm.PackageManager, p: Proc): String? {
        try {
            if (p.name.contains(".")) {
                pm.getApplicationInfo(p.name, 0)?.let {
                    return it.loadLabel(pm).toString()
                }
            }
        } catch (_: Exception) {
        }
        try {
            val m = Pattern.compile("u(\\d+)a(\\d+)").matcher(p.user)
            if (m.matches()) {
                val uid = m.group(1)!!.toInt() * 100000 + 10000 + m.group(2)!!.toInt()
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return null
                return pm.getApplicationInfo(pkg, 0)?.loadLabel(pm)?.toString()
            }
        } catch (_: Exception) {
        }
        return null
    }
}
