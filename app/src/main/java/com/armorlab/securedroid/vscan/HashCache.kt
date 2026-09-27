package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.SharedPreferences

/**
 * APK 哈希缓存:key = 路径|最后更新时间|大小,三者不变即复用 SHA-256,
 * 让全盘扫描从"逐个读 APK 哈希"降为"只哈希变更过的应用"(差异扫描基础)。
 *
 * 性能设计(第二轮优化):
 * - 内存 LRU 命中即返回,避免每次 SharedPreferences 读取(原实现每包一次 IO);
 * - 写入合并为批量 apply(每 16 条或显式 flush),降低磁盘写入次数;
 * - 超限时按 LRU 淘汰,不再整表清空(避免缓存全丢导致下轮全量重算哈希)。
 */
object HashCache {

    private const val FILE = "sha_cache"
    private const val MAX_ENTRIES = 800
    private const val FLUSH_THRESHOLD = 16

    private val lock = Any()

    /** 访问序 LRU:命中即刷新顺序,超出上限淘汰最久未用 */
    private val mem = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_ENTRIES
    }

    private val pending = LinkedHashMap<String, String>()
    private var prefs: SharedPreferences? = null
    private var loaded = false

    private fun key(path: String, lastUpdate: Long, size: Long) =
        path + "|" + lastUpdate + "|" + size

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            for ((k, v) in p.all) {
                if (v is String) mem[k] = v
            }
            prefs = p
            loaded = true
        }
    }

    /** 批量落盘:一次 apply 写入全部待存条目;超限时裁剪最旧半边 */
    fun flush(context: Context) {
        ensureLoaded(context)
        synchronized(lock) {
            val p = prefs ?: return
            if (pending.isEmpty()) return
            val editor = p.edit()
            for ((k, v) in pending) editor.putString(k, v)
            editor.apply()
            pending.clear()
            if (p.all.size > MAX_ENTRIES) {
                // 磁盘超限:保留内存中最近使用的条目,重建存储
                val keep = LinkedHashMap(mem)
                p.edit().clear().apply()
                val rebuild = p.edit()
                for ((k, v) in keep) rebuild.putString(k, v)
                rebuild.apply()
            }
        }
    }

    fun cachedSha256(context: Context, path: String, lastUpdate: Long, size: Long): String {
        ensureLoaded(context)
        val k = key(path, lastUpdate, size)
        synchronized(lock) {
            mem[k]?.let { return it }
            prefs?.getString(k, null)?.let {
                mem[k] = it
                return it
            }
        }
        val sha = com.armorlab.securedroid.scan.ScannerEngine.hashFile(path)
        synchronized(lock) {
            mem[k] = sha
            pending[k] = sha
        }
        if (pendingSize() >= FLUSH_THRESHOLD) flush(context)
        return sha
    }

    private fun pendingSize(): Int = synchronized(lock) { pending.size }

    /** 差异扫描:该应用 APK 是否相对上次扫描发生过变化 */
    fun isChanged(context: Context, path: String, lastUpdate: Long, size: Long): Boolean {
        ensureLoaded(context)
        val k = key(path, lastUpdate, size)
        synchronized(lock) {
            if (mem.containsKey(k)) return false
            return prefs?.getString(k, null) == null
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            mem.clear()
            pending.clear()
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** 已缓存条目数(统计用) */
    fun size(context: Context): Int {
        ensureLoaded(context)
        return synchronized(lock) { mem.size }
    }
}
