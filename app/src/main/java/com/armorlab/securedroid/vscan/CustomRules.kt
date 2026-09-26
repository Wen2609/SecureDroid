package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.BehaviorRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自定义行为规则编辑器:用户自建 YARA 风格规则(名称/级别/模式/最小命中),
 * 持久化为 JSON,并在 TrojanScanner 扫描链路中与内置规则合并生效。
 */
object CustomRules {

    data class Rule(
        val name: String,
        val level: ThreatLevel,
        val patterns: List<String>,
        val minHits: Int
    )

    private const val KEY = "custom_rules"
    private const val KEY_VERSION = "custom_rules_version"

    @Volatile private var cacheVersion = -1
    @Volatile private var cacheList: List<Rule> = emptyList()

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 记忆化:仅在规则版本变更时重新解析 JSON(并行扫描每应用调用,避免重复解析) */
    fun list(context: Context): List<Rule> {
        val version = prefs(context).getInt(KEY_VERSION, 0)
        if (version == cacheVersion) return cacheList
        val json = prefs(context).getString(KEY, null) ?: ""
        val parsed = if (json.isEmpty()) emptyList() else try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pats = mutableListOf<String>()
                val pa = o.getJSONArray("patterns")
                for (j in 0 until pa.length()) pats.add(pa.getString(j))
                Rule(
                    o.getString("name"),
                    ThreatLevel.entries[o.getInt("level").coerceIn(0, 3)],
                    pats,
                    o.optInt("minHits", 2)
                )
            }
        } catch (_: Exception) { emptyList() }
        cacheVersion = version
        cacheList = parsed
        return parsed
    }

    private fun bump(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_VERSION, p.getInt(KEY_VERSION, 0) + 1).apply()
    }

    fun add(context: Context, name: String, level: ThreatLevel, patterns: List<String>, minHits: Int): Boolean {
        if (name.isBlank() || patterns.isEmpty()) return false
        val arr = JSONArray()
        for (r in list(context)) {
            if (r.name == name) return false
            val o = JSONObject()
            o.put("name", r.name)
            o.put("level", r.level.ordinal)
            o.put("patterns", JSONArray(r.patterns))
            o.put("minHits", r.minHits)
            arr.put(o)
        }
        val o = JSONObject()
        o.put("name", name)
        o.put("level", level.ordinal)
        o.put("patterns", JSONArray(patterns))
        o.put("minHits", minHits)
        arr.put(o)
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        bump(context)
        return true
    }

    fun remove(context: Context, name: String) {
        val arr = JSONArray()
        for (r in list(context)) {
            if (r.name == name) continue
            val o = JSONObject()
            o.put("name", r.name)
            o.put("level", r.level.ordinal)
            o.put("patterns", JSONArray(r.patterns))
            o.put("minHits", r.minHits)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        bump(context)
    }

    /** 转为内置规则引擎格式,供 TrojanScanner 合并 */
    fun asRules(context: Context): List<BehaviorRules.Rule> =
        list(context).map {
            BehaviorRules.Rule(it.name, it.level, "自定义规则: " + it.patterns.joinToString("/"), it.patterns, it.minHits)
        }
}
