package com.armorlab.securedroid.root

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * su 命令桥接:
 * - /data/adb 目录仅 root 可读;本应用先尝试直接读,失败后通过 su 执行
 *   只读命令(cat / ls / test)完成检测,首次会弹出管理器授权;
 * - 处置命令(禁用模块 / 删除脚本)仅在用户于界面确认后由上层调用;
 * - 所有命令限时执行,超时即销毁进程。
 */
object ShellBridge {

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "shell-bridge").apply { isDaemon = true }
    }

    /** 执行 su -c 命令,返回标准输出;失败 / 超时 / 未授权返回 null */
    fun runSu(cmd: String, timeoutMs: Long = 10_000L): String? {
        val proc = try {
            ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return null
        }
        val future = executor.submit(Callable {
            proc.inputStream.bufferedReader().readText()
        })
        return try {
            val out = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            proc.waitFor()
            out
        } catch (_: Exception) {
            proc.destroy()
            null
        }
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
        return runSu("cat '" + path + "' 2>/dev/null")
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
        val out = runSu("ls -1 '" + path + "' 2>/dev/null") ?: return null
        val lines = out.lines().filter { it.isNotBlank() }
        return if (lines.isEmpty()) null else lines
    }

    /** 远程存在性判断(支持 root-only 路径) */
    fun existsBestEffort(path: String): Boolean {
        try {
            if (File(path).exists()) return true
        } catch (_: Exception) {
        }
        return runSu("[ -e '" + path + "' ] && echo __yes__ 2>/dev/null")
            ?.contains("__yes__") == true
    }
}
