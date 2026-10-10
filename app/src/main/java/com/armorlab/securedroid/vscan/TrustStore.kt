package com.armorlab.securedroid.vscan

import android.content.Context
import java.util.Collections

/**
 * 信任列表(白名单,读取侧):被信任的应用在所有病毒扫描链路中跳过
 * (TrojanScanner / 定时查杀 / 深扫均经 isTrusted 过滤)。
 *
 * 性能:原实现每次 isTrusted 都 getStringSet + 复制整个集合,全盘扫描时每个应用一次;
 * 这里改为进程内不可变快照。SharedPreferences 的 getStringSet 本身也会
 * 防御性复制,所以快照是必需的,而不是"再复制一次"。
 */
object TrustStore {

    private const val KEY = "trusted_pkgs"

    private val lock = Any()

    @Volatile
    private var snapshot: Set<String>? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun trusted(context: Context): Set<String> {
        snapshot?.let { return it }
        synchronized(lock) {
            snapshot?.let { return it }
            val loaded = prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()
            val copy: Set<String> = Collections.unmodifiableSet(LinkedHashSet(loaded))
            snapshot = copy
            return copy
        }
    }

    fun isTrusted(context: Context, pkg: String): Boolean = trusted(context).contains(pkg)
}
