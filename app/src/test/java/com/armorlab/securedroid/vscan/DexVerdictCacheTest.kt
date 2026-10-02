package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DEX 行为判定缓存测试(含真实 SharedPreferences)。
 *
 * 优化前有两个坏点:每次命中都要逐条解析整个字符串集(每包 O(600)),
 * 且"没命中任何规则"时不写任何条目 —— 负缓存实际失效,同一 APK 会被反复深度分析。
 * 这里钉住:负缓存必须落盘、含 '|' 的威胁名必须能往返、空 SHA 不得污染缓存。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DexVerdictCacheTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun det(name: String, level: ThreatLevel) =
        TrojanScanner.Detection("BehaviorRules", name, level, "detail")

    private fun prefs(): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("dex_verdict_cache", Context.MODE_PRIVATE)

    /** 模拟"进程重启":清掉内存索引,强制下次从 SharedPreferences 重新解析 */
    private fun dropInMemory() {
        val mem = DexVerdictCache::class.java.getDeclaredField("mem").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (mem.get(DexVerdictCache) as MutableMap<String, Any>).clear()
        DexVerdictCache::class.java.getDeclaredField("loaded").apply { isAccessible = true }
            .setBoolean(DexVerdictCache, false)
        DexVerdictCache::class.java.getDeclaredField("prefs").apply { isAccessible = true }
            .set(DexVerdictCache, null)
    }

    @Before
    fun reset() {
        DexVerdictCache.clear(ctx)
        dropInMemory()
    }

    @Test
    fun storeThenHitIsServedFromMemoryAndFlushedOnce() {
        assertNull(DexVerdictCache.cachedHits(ctx, "sha-a"))
        DexVerdictCache.store(ctx, "sha-a", listOf(det("Trojan.A", ThreatLevel.HIGH)))
        assertEquals(listOf("Trojan.A" to ThreatLevel.HIGH), DexVerdictCache.cachedHits(ctx, "sha-a"))
        assertTrue(DexVerdictCache.pendingCountForTest() >= 1)
        DexVerdictCache.flush(ctx)
        assertEquals("flush 后待写计数必须归零", 0, DexVerdictCache.pendingCountForTest())
        assertEquals(listOf("Trojan.A" to ThreatLevel.HIGH), DexVerdictCache.cachedHits(ctx, "sha-a"))
    }

    @Test
    fun negativeCacheIsPersistedSoCleanApksAreNotRescanned() {
        DexVerdictCache.store(ctx, "sha-clean", emptyList())
        DexVerdictCache.flush(ctx)
        val raw = prefs().getStringSet("dex_verdict_cache", emptySet())!!.toList()
        assertTrue(
            "干净判定必须落盘(~clean 标记),否则同一 APK 会被反复做 dex 提取 + 规则匹配: " + raw,
            raw.any { it.startsWith("sha-clean|") }
        )
        dropInMemory()
        assertEquals(
            "重新加载后应判定为已知干净(空列表),而不是需要重扫(null)",
            emptyList<Pair<String, ThreatLevel>>(),
            DexVerdictCache.cachedHits(ctx, "sha-clean")
        )
    }

    @Test
    fun threatNamesContainingSeparatorSurviveReload() {
        DexVerdictCache.store(ctx, "sha-b", listOf(det("Trojan|Suspicious.A", ThreatLevel.MEDIUM)))
        DexVerdictCache.flush(ctx)
        dropInMemory()
        assertEquals(
            listOf("Trojan|Suspicious.A" to ThreatLevel.MEDIUM),
            DexVerdictCache.cachedHits(ctx, "sha-b")
        )
    }

    @Test
    fun blankShaNeverTouchesCache() {
        assertNull(DexVerdictCache.cachedHits(ctx, ""))
        DexVerdictCache.store(ctx, "", listOf(det("X", ThreatLevel.LOW)))
        assertEquals(0, DexVerdictCache.size(ctx))
    }

    @Test
    fun clearRemovesPersistedEntries() {
        DexVerdictCache.store(ctx, "sha-c", listOf(det("Trojan.C", ThreatLevel.LOW)))
        DexVerdictCache.flush(ctx)
        DexVerdictCache.clear(ctx)
        assertEquals(0, DexVerdictCache.size(ctx))
        assertTrue(prefs().getStringSet("dex_verdict_cache", emptySet())!!.isEmpty())
    }
}
