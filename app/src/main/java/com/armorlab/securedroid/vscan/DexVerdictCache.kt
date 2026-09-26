package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner

/**
 * DEX 行为判定缓存:以 APK SHA-256 为指纹缓存行为规则命中结果,
 * APK 未更新时跳过"读 dex + 提取字符串 + 规则匹配"三个最重环节。
 */
object DexVerdictCache {

    private const val KEY = "dex_verdict_cache"
    private const val MAX = 600

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "dex_verdict_cache"

    fun cachedHits(context: Context, sha: String): List<Pair<String, ThreatLevel>>? {
        val out = mutableListOf<Pair<String, ThreatLevel>>()
        for (e in prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()) {
            if (e.startsWith(sha + "|")) {
                val parts = e.split('|')
                val level = parts.getOrNull(2)?.toIntOrNull()?.let { idx ->
                    ThreatLevel.entries.getOrNull(idx)
                } ?: continue
                out.add(Pair(parts[1], level))
            }
        }
        return if (prefs(context).getStringSet(KEY, emptySet())!!.any { it.startsWith(sha + "|") }) out else null
    }

    fun store(context: Context, sha: String, detections: List<TrojanScanner.Detection>) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        for (d in detections) {
            set.add(sha + "|" + d.name + "|" + d.level.ordinal)
        }
        if (set.size > MAX) set.clear()
        p.edit().putStringSet(KEY, set).apply()
    }
}
