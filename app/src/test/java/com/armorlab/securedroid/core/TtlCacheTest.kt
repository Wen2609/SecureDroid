package com.armorlab.securedroid.core

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TtlCache TTL 缓存测试。
 *
 * 这个缓存是第三轮性能优化的地基(包列表快照 / 权限审计 / DEX 判定都建在它上面),
 * 因此必须钉住三件事:命中不重复加载、过期后重载、同 key 并发只加载一次。
 */
class TtlCacheTest {

    private var now = 1_000L

    private fun cache(ttl: Long = 100L, max: Int = 256) = TtlCache<String, String>(ttl, { now }, max)

    @Test
    fun valueIsCachedWithinTtl() {
        val c = cache()
        val loads = AtomicInteger(0)
        repeat(3) { assertEquals("v", c.getOrLoad("k") { loads.incrementAndGet(); "v" }) }
        assertEquals("TTL 内只应加载一次", 1, loads.get())
        assertEquals(2L, c.stats().hits)
        assertEquals(1L, c.stats().loads)
    }

    @Test
    fun expiryTriggersReload() {
        val c = cache(ttl = 100L)
        c.getOrLoad("k") { "a" }
        now += 100L
        assertEquals("b", c.getOrLoad("k") { "b" })
        assertEquals(2L, c.stats().loads)
    }

    @Test
    fun peekNeverLoads() {
        val c = cache()
        assertNull(c.peek("missing"))
        c.put("k", "v")
        assertEquals("v", c.peek("k"))
        now += 100L
        assertNull("过期后 peek 必须返回 null,不得触发加载", c.peek("k"))
    }

    @Test
    fun loaderFailureIsNotCached() {
        val c = cache()
        var calls = 0
        try {
            c.getOrLoad("k") { calls++; throw IllegalStateException("boom") }
        } catch (_: IllegalStateException) {
        }
        assertEquals("ok", c.getOrLoad("k") { calls++; "ok" })
        assertEquals("失败的加载不应写入缓存", 2, calls)
        assertEquals(1, c.size())
    }

    @Test
    fun invalidateForcesReloadAndClearEmpties() {
        val c = cache(ttl = 60_000L)
        c.getOrLoad("k") { "a" }
        c.invalidate("k")
        assertEquals("b", c.getOrLoad("k") { "b" })
        c.clear()
        assertEquals(0, c.size())
    }

    @Test
    fun capacityIsBoundedAndNeverEmptied() {
        val c = cache(ttl = 60_000L, max = 8)
        for (i in 1..40) c.put("k" + i, "v" + i)
        assertTrue("超出上限应淘汰而不是无限增长: " + c.size(), c.size() <= 8)
        assertTrue("淘汰后仍应保留最近条目", c.size() > 0)
    }

    @Test
    fun concurrentSameKeyLoadsOnce() {
        val c = cache(ttl = 60_000L)
        val loads = AtomicInteger(0)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        val results = Collections.synchronizedList(mutableListOf<String>())
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) {
            pool.execute {
                try {
                    start.await()
                    results.add(c.getOrLoad("k") { loads.incrementAndGet(); Thread.sleep(40); "v" })
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue("并发加载超时", done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals("同 key 并发只应加载一次(单飞)", 1, loads.get())
        assertEquals(8, results.size)
        assertTrue(results.all { it == "v" })
    }
}
