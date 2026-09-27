package com.armorlab.securedroid.feature

import android.content.Context
import com.armorlab.securedroid.root.ShellBridge

/** 应用缓存清理:du 统计各应用 cache 目录大小(root),一键 rm 清理 */
object CleanerTool {

    data class Entry(val label: String, val pkg: String, val sizeKb: Long)

    fun stat(context: Context): List<Entry> {
        val out = ShellBridge.runSu("du -sk /data/data/*/cache 2>/dev/null") ?: return emptyList()
        val pm = context.packageManager
        val entries = mutableListOf<Entry>()
        for (line in out.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val parts = t.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) continue
            val size = parts[0].toLongOrNull() ?: continue
            val pkg = parts[1].removePrefix("/data/data/").removeSuffix("/cache")
            if (size <= 0) continue
            val label = try {
                pm.getApplicationInfo(pkg, 0)?.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) { pkg }
            entries.add(Entry(label, pkg, size))
        }
        return entries.sortedByDescending { it.sizeKb }
    }

    fun cleanAll(): Boolean = ShellBridge.runSu("rm -rf /data/data/*/cache/* 2>/dev/null") != null
}
