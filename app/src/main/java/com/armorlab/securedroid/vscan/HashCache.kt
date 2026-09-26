package com.armorlab.securedroid.vscan

import android.content.Context

/**
 * APK 哈希缓存:key = 路径|最后更新时间|大小,三者不变即复用 SHA-256,
 * 让全盘扫描从"逐个读 APK 哈希"降为"只哈希变更过的应用"(差异扫描基础)。
 */
object HashCache {

    private const val FILE = "sha_cache"
    private const val MAX_ENTRIES = 800

    private fun key(path: String, lastUpdate: Long, size: Long) =
        path + "|" + lastUpdate + "|" + size

    fun cachedSha256(context: Context, path: String, lastUpdate: Long, size: Long): String {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val k = key(path, lastUpdate, size)
        prefs.getString(k, null)?.let { return it }
        val sha = com.armorlab.securedroid.scan.ScannerEngine.hashFile(path)
        prefs.edit().putString(k, sha).apply()
        if (prefs.all.size > MAX_ENTRIES) {
            // 超限整表重建(简单粗暴但安全)
            prefs.edit().clear().putString(k, sha).apply()
        }
        return sha
    }

    /** 差异扫描:该应用 APK 是否相对上次扫描发生过变化 */
    fun isChanged(context: Context, path: String, lastUpdate: Long, size: Long): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return prefs.getString(key(path, lastUpdate, size), null) == null
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
