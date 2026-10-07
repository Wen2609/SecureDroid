package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.core.Re
import android.content.Context
import com.armorlab.securedroid.root.ShellBridge
import java.io.File

/**
 * 隔离区(root):把可疑文件移动进应用私有目录并收紧权限,
 * 使其脱离执行路径;支持恢复(还原到原路径)与销毁,记录元数据与隔离原因。
 *
 * 记录格式(偏好 StringSet,每条一项):
 *   原路径|隔离路径|时间|[原因]  —— 原因是后加字段,旧记录按 3 字段向后兼容。
 */
object Quarantine {

    data class Item(
        val originalPath: String,
        val quarantinedPath: String,
        val time: Long,
        val reason: String,
        /** 原始记录串(移除记录用,避免重组时字段不一致) */
        val raw: String
    )

    private const val KEY = "quarantine_items"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun dir(context: Context) =
        File(context.filesDir, "quarantine").apply { mkdirs() }

    fun quarantine(context: Context, path: String, reason: String = ""): Boolean {
        val dst = File(dir(context),
            path.substringAfterLast('/').replace(Re.UNSAFE_FILENAME, "_") +
                "." + System.currentTimeMillis() + ".qtn").absolutePath
        val ok = ShellBridge.runSuChecked("mv " + ShellBridge.quote(path) + " " + ShellBridge.quote(dst) + " && chmod 600 " + ShellBridge.quote(dst)) && ShellBridge.existsBestEffort(dst)
        if (ok) {
            val p = prefs(context)
            val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
            set.add(path + "|" + dst + "|" + System.currentTimeMillis() + "|" + reason.replace('|', ' '))
            p.edit().putStringSet(KEY, set).apply()
        }
        return ok
    }

    fun items(context: Context): List<Item> {
        return (prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()).map { raw ->
            val f = raw.split('|', limit = 4)
            Item(
                originalPath = f.getOrElse(0) { "" },
                quarantinedPath = f.getOrElse(1) { "" },
                time = f.getOrElse(2) { "0" }.toLongOrNull() ?: 0L,
                reason = f.getOrElse(3) { "" },
                raw = raw
            )
        }.sortedByDescending { it.time }
    }

    /** 恢复:把隔离文件移回原路径(权限 600),成功后清除记录 */
    fun restore(context: Context, item: Item): Boolean {
        val ok = ShellBridge.runSuChecked(
            "mv " + ShellBridge.quote(item.quarantinedPath) + " " + ShellBridge.quote(item.originalPath) +
                " && chmod 600 " + ShellBridge.quote(item.originalPath)
        ) && ShellBridge.existsBestEffort(item.originalPath)
        if (ok) removeRecord(context, item.raw)
        return ok
    }

    fun destroy(context: Context, item: Item): Boolean {
        val ok = ShellBridge.runSuChecked("rm -f " + ShellBridge.quote(item.quarantinedPath)) && !File(item.quarantinedPath).exists()
        if (ok) removeRecord(context, item.raw)
        return ok
    }

    private fun removeRecord(context: Context, raw: String) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.remove(raw)
        p.edit().putStringSet(KEY, set).apply()
    }
}
