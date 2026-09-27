package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.ui.TrojanAdapter
import org.json.JSONObject

/** 查杀结果差异对比:保存上次快照,本次对比新增 / 已消除的威胁 */
object ResultDiff {

    private const val KEY = "last_scan_snapshot"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun save(context: Context, results: List<TrojanScanner.Report>) {
        val o = JSONObject()
        for (r in results) {
            o.put(r.packageName, if (r.isInfected) (r.worstLevel?.name ?: "INFECTED") else "CLEAN")
        }
        prefs(context).edit().putString(KEY, o.toString()).apply()
    }

    private fun load(context: Context): Map<String, String> {
        val s = prefs(context).getString(KEY, null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val m = LinkedHashMap<String, String>()
            for (k in o.keys()) m[k] = o.getString(k)
            m
        } catch (_: Exception) { emptyMap() }
    }

    fun compare(context: Context, current: List<TrojanScanner.Report>): List<TrojanAdapter.UiItem> {
        val prev = load(context)
        val nowInfected = current.filter { it.isInfected }
        val prevInfected = prev.filterValues { it != "CLEAN" }
        val newThreats = nowInfected.filter { prevInfected[it.packageName] == null }
        val resolved = prevInfected.keys.filter { key -> current.none { it.packageName == key && it.isInfected } }
        val items = mutableListOf(
            TrojanAdapter.UiItem(
                "ResultDiff.Summary",
                "新增威胁 " + newThreats.size + " 个,已消除 " + resolved.size + " 个",
                "对比快照共覆盖 " + prev.size + " 个应用", ThreatLevel.LOW, null, null
            )
        )
        for (r in newThreats) {
            items.add(
                TrojanAdapter.UiItem(
                    "ResultDiff.NewThreat · " + r.appName, r.packageName,
                    "本次新发现: " + r.detections.joinToString("; ") { it.name },
                    r.worstLevel ?: ThreatLevel.HIGH, null, null, null, null
                )
            )
        }
        for (pkg in resolved) {
            items.add(
                TrojanAdapter.UiItem(
                    "ResultDiff.Resolved · " + pkg, pkg,
                    "相比上次扫描威胁已消除", ThreatLevel.LOW, null, null, null, null
                )
            )
        }
        save(context, current)
        return items
    }
}
