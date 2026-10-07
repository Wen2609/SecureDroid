package com.armorlab.securedroid.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 崩溃捕获:接管未捕获异常,把线程与调用栈落到 files/crash/(保留最近 10 份),
 * 然后交回系统默认处理(照常崩溃,不吞异常)。
 *
 * 边界:纯本地记录,不做任何网络上报 —— 崩溃文件只能经用户主动
 * 「崩溃日志导出」分享出去,避免把堆栈里的潜在敏感信息静默外传。
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val KEEP = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(appContext, thread.name, throwable)
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 写入一份崩溃记录并裁剪到 KEEP 份(测试与安装路径共用) */
    fun write(context: Context, threadName: String, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        val text = buildString {
            append("SecureDroid 崩溃报告\n")
            append("时间: ").append(System.currentTimeMillis()).append('\n')
            append("版本: ").append(version).append('\n')
            append("线程: ").append(threadName).append('\n')
            append("异常: ").append(throwable.javaClass.name).append(": ")
                .append(throwable.message ?: "").append("\n\n")
            append(stack)
        }
        File(dir, "crash-" + System.currentTimeMillis() + ".txt").writeText(text)
        trim(dir)
    }

    /** 崩溃记录文件(新→旧) */
    fun logs(context: Context): List<File> {
        val dir = File(context.filesDir, DIR)
        return dir.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    /** 最新一份的文本;无记录返回 null */
    fun latestText(context: Context): String? =
        logs(context).firstOrNull()?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") }
            ?.sortedByDescending { it.name } ?: return
        files.drop(KEEP).forEach { it.delete() }
    }
}
