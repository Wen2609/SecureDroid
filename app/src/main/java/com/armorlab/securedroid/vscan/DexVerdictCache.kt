package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.SharedPreferences
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner

/**
 * DEX 行为判定缓存(按 APK SHA-256 指纹)。
 *
 * 性能(第三轮优化):
 * - 进程内内存索引,命中 O(1);原实现每次命中都要解析整个 SharedPreferences 字符串集合,
 *   ≤600 条逐条 startsWith + split,即每个应用 O(600);
 * - 负缓存生效:行为与字节码都没命中时写入 "~clean" 标记,同一 APK 不再重复做
 *   "读 dex + 提取字符串 + 规则匹配"三个最重环节(原实现不写空结果,等于负缓存失效);
 * - 批量落盘:新增条目先累积,满 32 条或扫描结束显式 flush 时整表重写一次 apply
 *   (原实现每包整集复制 + apply,约 200 次)。
 *
 * 持久化格式保持向后兼容:sha|name|levelIdx,负缓存为 sha|~clean|0;
 * name 里若含 '|' 也能正确解析(取首段为 sha、末段为 level,中间合并回 name)。
 */
object DexVerdictCache {

    private const val FILE = "dex_verdict_cache"
    private const val KEY = "dex_verdict_cache"
    private const val MAX_ENTRIES = 600
    private const val MAX_APPS = 300
    private const val FLUSH_THRESHOLD = 32
    private const val CLEAN = "~clean"
    private const val KEY_VERSION = "dex_verdict_cache_version"

    /**
     * 判定语义版本号。行为规则 / 特征库语义变化(如 v1.7.3 的误报修复)后必须自增:
     * 旧版本写进缓存的 HIGH 判定是按老规则得出的,继续复用会让修复在老设备上完全不生效。
     */
    private const val VERSION = 2

    private class Acc {
        var clean = false
        val hits = mutableListOf<Pair<String, ThreatLevel>>()
    }

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var loaded = false

    /** 访问序 LRU:sha -> 命中列表(空列表 = 已知干净) */
    private val mem = object : LinkedHashMap<String, List<Pair<String, ThreatLevel>>>(128, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<Pair<String, ThreatLevel>>>?
        ): Boolean = size > MAX_APPS
    }

    private var pending = 0

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            if (p.getInt(KEY_VERSION, 0) != VERSION) {
                // 规则语义升级:整表丢弃(旧判定不再可信),只写入新版本号
                p.edit().remove(KEY).putInt(KEY_VERSION, VERSION).apply()
                prefs = p
                loaded = true
                return
            }
            val acc = HashMap<String, Acc>()
            for (e in p.getStringSet(KEY, emptySet()) ?: emptySet()) {
                val parts = e.split('|')
                if (parts.size < 3) continue
                val level = parts[parts.size - 1].toIntOrNull()?.let { idx ->
                    ThreatLevel.entries.getOrNull(idx)
                } ?: continue
                val sha = parts[0]
                val name = parts.subList(1, parts.size - 1).joinToString("|")
                val a = acc.getOrPut(sha) { Acc() }
                if (name == CLEAN) a.clean = true else a.hits.add(Pair(name, level))
            }
            for ((sha, a) in acc) mem[sha] = if (a.clean) emptyList() else a.hits
            prefs = p
            loaded = true
        }
    }

    /** 命中返回列表(空列表 = 已知干净);未知返回 null(需要真正扫描) */
    fun cachedHits(context: Context, sha: String): List<Pair<String, ThreatLevel>>? {
        if (sha.isEmpty()) return null
        ensureLoaded(context)
        return synchronized(lock) { mem[sha] }
    }

    fun store(context: Context, sha: String, detections: List<TrojanScanner.Detection>) {
        if (sha.isEmpty()) return
        ensureLoaded(context)
        val value = detections.map { Pair(it.name, it.level) }
        synchronized(lock) {
            mem[sha] = value
            pending++
        }
        if (pendingCount() >= FLUSH_THRESHOLD) flush(context)
    }

    /** 整表重写:一次 apply,避免逐包写盘;同时裁剪到条目上限 */
    fun flush(context: Context) {
        ensureLoaded(context)
        val p = prefs ?: return
        val entries = synchronized(lock) {
            if (pending == 0) return
            pending = 0
            LinkedHashSet(snapshotEntries())
        }
        p.edit().putStringSet(KEY, entries).apply()
    }

    fun clear(context: Context) {
        synchronized(lock) {
            mem.clear()
            pending = 0
        }
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun size(context: Context): Int {
        ensureLoaded(context)
        return synchronized(lock) { mem.size }
    }

    fun pendingCountForTest(): Int = pendingCount()

    private fun pendingCount(): Int = synchronized(lock) { pending }

    /** LRU 顺序遍历(最久未用在前),超限时保留最近使用的尾部 */
    private fun snapshotEntries(): List<String> {
        val all = ArrayList<String>(mem.size + 8)
        for ((sha, list) in mem) {
            if (list.isEmpty()) {
                all.add(sha + "|" + CLEAN + "|0")
            } else {
                for ((name, level) in list) all.add(sha + "|" + name + "|" + level.ordinal)
            }
        }
        return if (all.size <= MAX_ENTRIES) all else all.subList(all.size - MAX_ENTRIES, all.size)
    }
}
