package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.root.ShellBridge
import java.io.File

/**
 * 隔离区(root):把可疑文件移动进应用私有目录并收紧权限,
 * 使其脱离执行路径;支持销毁,记录元数据。
 */
object Quarantine {

    data class Item(val originalPath: String, val quarantinedPath: String, val time: Long)

    private const val KEY = "quarantine_items"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun dir(context: Context) =
        File(context.filesDir, "quarantine").apply { mkdirs() }

    fun quarantine(context: Context, path: String): Boolean {
        val dst = File(dir(context),
            path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_") +
                "." + System.currentTimeMillis() + ".qtn").absolutePath
        val ok = ShellBridge.runSu(
            "mv '" + path + "' '" + dst + "' && chmod 600 '" + dst + "'"
        ) != null
        if (ok) {
            val p = prefs(context)
            val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
            set.add(path + "|" + dst + "|" + System.currentTimeMillis())
            p.edit().putStringSet(KEY, set).apply()
        }
        return ok
    }

    fun items(context: Context): List<Item> {
        return (prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()).map { s ->
            val f = s.split('|')
            Item(
                originalPath = f.getOrElse(0) { "" },
                quarantinedPath = f.getOrElse(1) { "" },
                time = f.getOrElse(2) { "0" }.toLongOrNull() ?: 0L
            )
        }.sortedByDescending { it.time }
    }

    fun destroy(context: Context, item: Item): Boolean {
        val ok = ShellBridge.runSu("rm -f '" + item.quarantinedPath + "'") != null
        if (ok) removeRecord(context, item)
        return ok
    }

    private fun removeRecord(context: Context, item: Item) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.remove(item.originalPath + "|" + item.quarantinedPath + "|" + item.time)
        p.edit().putStringSet(KEY, set).apply()
    }
}
