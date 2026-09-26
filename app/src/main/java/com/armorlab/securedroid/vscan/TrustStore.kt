package com.armorlab.securedroid.vscan

import android.content.Context

/**
 * 信任列表(白名单):被信任的应用在所有病毒扫描链路中跳过
 * (TrojanScanner / 定时查杀 / 深扫均经 isTrusted 过滤)。
 */
object TrustStore {

    private const val KEY = "trusted_pkgs"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun trusted(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    fun isTrusted(context: Context, pkg: String): Boolean =
        trusted(context).contains(pkg)

    fun setTrusted(context: Context, pkg: String, trusted: Boolean) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        if (trusted) set.add(pkg) else set.remove(pkg)
        p.edit().putStringSet(KEY, set).apply()
    }
}
