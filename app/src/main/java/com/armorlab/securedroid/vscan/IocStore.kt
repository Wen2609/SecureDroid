package com.armorlab.securedroid.vscan

import android.content.Context

/** IOC 模式库:文件名/进程名子串模式,可由用户扩展,进程与文件扫描链路均匹配 */
object IocStore {

    private const val KEY = "ioc_patterns"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun patterns(context: Context): List<String> =
        (prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()).toList()

    fun add(context: Context, pattern: String): Boolean {
        if (pattern.isBlank()) return false
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        val added = set.add(pattern.trim())
        if (added) p.edit().putStringSet(KEY, set).apply()
        return added
    }

    fun remove(context: Context, pattern: String) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.remove(pattern)
        p.edit().putStringSet(KEY, set).apply()
    }

    fun matches(context: Context, name: String): Boolean {
        val lower = name.lowercase()
        return patterns(context).any { lower.contains(it.lowercase()) }
    }
}
