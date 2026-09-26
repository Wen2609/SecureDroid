package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.root.ShellBridge

/**
 * 进程基线:学习当前正常进程集合,后续检测"基线外新增进程"
 * (新型木马常驻的第一信号)。
 */
object ProcessBaseline {

    private const val KEY = "process_baseline"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun currentNames(): Set<String> {
        val out = ShellBridge.runSu("ps -A", 20_000L) ?: return emptySet()
        val names = HashSet<String>()
        var first = true
        for (line0 in out.lines()) {
            val line = line0.trim()
            if (line.isEmpty()) continue
            if (first) { first = false; if (line.startsWith("USER")) continue }
            val f = line.split(Regex("\\s+"), limit = 9)
            if (f.size < 9) continue
            names.add(f[8])
        }
        return names
    }

    fun hasBaseline(context: Context): Boolean =
        prefs(context).getStringSet(KEY, null) != null

    /** 学习基线,返回进程数;-1 表示失败 */
    fun learn(context: Context): Int {
        val names = currentNames()
        if (names.isEmpty()) return -1
        prefs(context).edit().putStringSet(KEY, names).apply()
        return names.size
    }

    fun newProcesses(context: Context): List<String> {
        val base = prefs(context).getStringSet(KEY, null) ?: return emptyList()
        return (currentNames() - base).sorted()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
