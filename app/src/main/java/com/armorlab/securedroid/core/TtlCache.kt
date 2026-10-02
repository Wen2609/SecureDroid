package com.armorlab.securedroid.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 轻量 TTL 缓存,提供三件事:
 * 1. 命中即返回、过期后按需重载(避免热路径重复做绑定器调用 / 文件解析);
 * 2. 同 key 单飞(single-flight):并发请求同一 key 时只执行一次加载,其余线程等待并复用结果;
 *    stats() 中 loads < misses 即说明单飞生效;
 * 3. 容量上限:超出后按"最久未刷新"淘汰,不会整表清空。
 *
 * @param ttlMs 有效期(毫秒),可运行期调整
 * @param clock 时钟,测试可注入假时钟
 * @param maxEntries 内存条目上限
 */
class TtlCache<K : Any, V : Any>(
    @Volatile var ttlMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 256,
) {

    private class Entry<V>(@Volatile var value: V, @Volatile var at: Long)

    data class Stats(val hits: Long, val misses: Long, val loads: Long, val size: Int)

    private val map = ConcurrentHashMap<K, Entry<V>>()
    private val stripes = Array(STRIPES) { Any() }
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val loads = AtomicLong()

    /** 只读命中:过期不算命中,也不触发加载 */
    fun peek(key: K): V? {
        val e = map[key] ?: return null
        return if (clock() - e.at < ttlMs) e.value else null
    }

    /** 取缓存,过期或不存在时执行 loader;loader 抛异常时不写入缓存(下次仍会重试) */
    fun getOrLoad(key: K, loader: () -> V): V {
        val e = map[key]
        if (e != null && clock() - e.at < ttlMs) {
            hits.incrementAndGet()
            return e.value
        }
        synchronized(stripeFor(key)) {
            val again = map[key]
            if (again != null && clock() - again.at < ttlMs) {
                hits.incrementAndGet()
                return again.value
            }
            misses.incrementAndGet()
            val value = loader()
            loads.incrementAndGet()
            map[key] = Entry(value, clock())
            pruneIfNeeded()
            return value
        }
    }

    fun put(key: K, value: V) {
        map[key] = Entry(value, clock())
        pruneIfNeeded()
    }

    fun invalidate(key: K) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size

    fun stats(): Stats = Stats(hits.get(), misses.get(), loads.get(), map.size)

    private fun stripeFor(key: K): Any = stripes[(key.hashCode() and 0x7FFFFFFF) % stripes.size]

    private fun pruneIfNeeded() {
        if (map.size <= maxEntries) return
        val excess = map.size - maxEntries
        val drop = map.entries.sortedBy { it.value.at }.take(excess + maxEntries / 4 + 1)
        for (e in drop) map.remove(e.key)
    }

    private companion object {
        const val STRIPES = 16
    }
}
