package com.armorlab.securedroid.root

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * su 命令桥接:
 * - /data/adb 目录仅 root 可读;本应用先尝试直接读,失败后通过 su 执行
 *   只读命令(cat / ls / test)完成检测,首次会弹出管理器授权;
 * - 处置命令(禁用模块 / 删除脚本 / 隔离文件 / iptables)仅在用户于界面确认、
 *   或用户显式开启自动处置后由上层调用;
 * - 所有命令限时执行,超时即销毁进程。
 *
 * 成功判定的硬约定(修复“假成功”):
 * 由于 redirectErrorStream(true) 把 stderr 并进 stdout,一次失败的 su 调用(命令不存在、
 * 文件不存在、用户拒绝授权)同样会返回一段文本,最坏情况是空字符串——**空字符串也是非 null**。
 * 所以“返回值 != null”绝不能当作成功:
 *  - [runSu]        只读查询用,返回合并输出,null 仅表示进程无法启动或超时;
 *  - [runSuChecked] 一切会改动系统的命令用,先过 [PrivilegedPolicy],再要求退出码为 0,
 *                   并拒绝输出里出现 Permission denied / not found 之类的拒绝标记;
 *  - [quote]        任何拼进 shell 命令的路径/参数都必须过它,否则文件名里的单引号
 *                   会越出引号,变成以 root 身份执行的任意命令。
 */
object ShellBridge {

    /** 一次 su 执行的结果:退出码 + 合并后的输出 */
    data class Exec(val exitCode: Int, val output: String)

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "shell-bridge").apply { isDaemon = true }
    }

    /** su 管理器拒绝或命令缺失时,即使退出码异常也按失败处理的输出标记 */
    private val DENY_HINTS = listOf(
        "permission denied", "not found", "no such file", "operation not permitted",
        "read-only file system", "access denied", "not permitted"
    )

    /** POSIX 单引号安全转义:把参数整体包进单引号,内部单引号写成 '\'' */
    fun quote(raw: String): String = "'" + raw.replace("'", "'\\''") + "'"

    /** [quote] 的逆运算:把命令里还原成原始参数(供“拿去调用 Quarantine”之类需要原始路径的场景) */
    fun unquote(quoted: String): String {
        val s = quoted.trim()
        if (s.length < 2 || s.first() != '\'' || s.last() != '\'') return s
        return s.substring(1, s.length - 1).replace("'\\''", "'")
    }

    private fun start(cmd: String): Process? = try {
        ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
    } catch (_: Exception) {
        null
    }

    private fun run(cmd: String, timeoutMs: Long): Exec? {
        val proc = start(cmd) ?: return null
        val future = executor.submit(Callable {
            proc.inputStream.bufferedReader().readText()
        })
        return try {
            val out = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            val code = try {
                proc.waitFor()
            } catch (_: Exception) {
                proc.destroy()
                return null
            }
            Exec(code, out)
        } catch (_: Exception) {
            proc.destroy()
            null
        }
    }

    /** 执行 su -c 命令,返回合并输出;进程无法启动 / 超时返回 null(调用方不得据此判定成功) */
    fun runSu(cmd: String, timeoutMs: Long = 10_000L): String? = run(cmd, timeoutMs)?.output

    /** 执行 su -c 命令并返回退出码与输出;进程无法启动 / 超时返回 null */
    fun runSuResult(cmd: String, timeoutMs: Long = 10_000L): Exec? = run(cmd, timeoutMs)

    /**
     * 会改动系统的命令统一走这里:策略拒绝、启动失败、超时、退出码非 0、
     * 或输出里带拒绝标记,一律返回 false。
     */
    fun runSuChecked(cmd: String, timeoutMs: Long = 10_000L): Boolean {
        val verdict = PrivilegedPolicy.check(cmd)
        if (verdict is PolicyVerdict.Deny) return false
        val result = run(cmd, timeoutMs) ?: return false
        if (result.exitCode != 0) return false
        val lower = result.output.lowercase()
        return DENY_HINTS.none { lower.contains(it) }
    }

    /** 检测设备上是否存在可用的 su(不触发授权弹窗,仅探测二进制) */
    fun suBinaryExists(): Boolean = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su",
        "/su/bin/su", "/data/local/xbin/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su"
    ).any { p -> try { File(p).exists() } catch (_: Exception) { false } }

    /** 读文件:直接读 → su cat 兜底 */
    fun readFileBestEffort(path: String): String? {
        try {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                val t = f.readText()
                if (t.isNotBlank()) return t
            }
        } catch (_: Exception) {
        }
        return runSu("cat " + quote(path) + " 2>/dev/null")
    }

    /** 列目录:直接列 → su ls 兜底 */
    fun listDirBestEffort(path: String): List<String>? {
        try {
            val f = File(path)
            if (f.isDirectory && f.canRead()) {
                val names = f.listFiles()?.map { it.name }
                if (!names.isNullOrEmpty()) return names
            }
        } catch (_: Exception) {
        }
        val out = runSu("ls -1 " + quote(path) + " 2>/dev/null") ?: return null
        val lines = out.lines().filter { it.isNotBlank() }
        return if (lines.isEmpty()) null else lines
    }

    /** 远程存在性判断(支持 root-only 路径) */
    fun existsBestEffort(path: String): Boolean {
        try {
            if (File(path).exists()) return true
        } catch (_: Exception) {
        }
        return runSu("[ -e " + quote(path) + " ] && echo __yes__ 2>/dev/null")
            ?.contains("__yes__") == true
    }
}
