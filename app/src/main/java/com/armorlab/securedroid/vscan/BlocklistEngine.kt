package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.feature.NetAudit
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/**
 * 黑名单连接检测:当前 TCP 连接与恶意 IP/域名模式列表交叉比对。
 * 预置演示条目 + 用户可扩展(持久化)。
 */
object BlocklistEngine {

    private const val KEY = "net_blocklist"

    private val preset = listOf(
        "tor2web", "pastebin.com/raw", ".onion", "duckdns.org"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun patterns(context: Context): List<String> =
        preset + (prefs(context).getStringSet(KEY, emptySet()) ?: emptySet())

    fun add(context: Context, pattern: String): Boolean {
        if (pattern.isBlank()) return false
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        val ok = set.add(pattern.trim())
        if (ok) p.edit().putStringSet(KEY, set).apply()
        return ok
    }

    fun remove(context: Context, pattern: String) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.remove(pattern)
        p.edit().putStringSet(KEY, set).apply()
    }

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        val pats = patterns(context)
        val conns = NetAudit.established(context, 200)
        val hits = mutableListOf<TrojanAdapter.UiItem>()
        var checked = 0
        for (c in conns) {
            checked++
            val matched = pats.firstOrNull { c.remote.contains(it, ignoreCase = true) }
            if (matched != null) {
                hits.add(
                    TrojanAdapter.UiItem(
                        "Net.Blacklist · " + c.appLabel,
                        c.pkg + " → " + c.remote,
                        "命中黑名单模式: " + matched,
                        ThreatLevel.HIGH,
                        "连接命中恶意模式,建议断网应急或卸载该应用",
                        null, null, null, null
                    )
                )
            }
        }
        val summary = mutableListOf(
            TrojanAdapter.UiItem(
                "Net.BlacklistSummary",
                "已比对 " + checked + " 条连接 × " + pats.size + " 个黑名单模式",
                "命中 " + hits.size + " 条;黑名单可在\"管理黑名单\"中扩展",
                if (hits.isEmpty()) ThreatLevel.LOW else ThreatLevel.HIGH, null, null
            )
        )
        return summary + hits
    }
}
