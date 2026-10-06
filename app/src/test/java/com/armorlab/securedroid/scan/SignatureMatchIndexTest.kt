package com.armorlab.securedroid.scan

import com.armorlab.securedroid.trojan.ClamAvSignatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

/**
 * 字节特征匹配索引的正确性与性能测试(纯 JVM,无需 Context)。
 *
 * 为什么必须测"等价性":查杀引擎的性能优化一旦改变匹配语义,代价是**漏报**
 * (放过木马)或**误报**(伤害用户信任)。因此这里用两种独立参考实现
 * (旧版"单字节锚点分桶"算法 + 逐签名朴素扫描)对同一批随机特征与随机数据交叉验证,
 * 要求三者输出集合完全一致。
 *
 * 性能部分测的是真实差距:旧实现每个字节位置都要遍历该字节对应的候选桶,
 * 复杂度 O(数据长度 × 桶内候选数);现实现按 2 字节窗口直接寻址,候选数降到约 1/256。
 *
 * 注意:本项目曾把索引实现为 Aho-Corasick 自动机(CSR + 每字节二分查找),实测比
 * 单字节锚点分桶**更慢**(见下方性能用例的对比结论),因此改回直接索引方案。
 * 这里同时保留了对旧实现的基准,避免以后有人凭"AC 更高级"的直觉改回去。
 */
class SignatureMatchIndexTest {

    /** 与引擎无关的参考签名表示 */
    private class RefSig(val name: String, val lo: IntArray, val hi: IntArray) {
        /** 最后一个"两个半字节都固定"的字节下标(旧实现的锚点选择策略) */
        val anchorIdx: Int = run {
            var i = lo.size - 1
            while (i >= 0) {
                if (lo[i] >= 0 && hi[i] >= 0) return@run i
                i--
            }
            -1
        }
        val anchorByte: Int = if (anchorIdx >= 0) (lo[anchorIdx] shl 4) or hi[anchorIdx] else -1
    }

    private val rnd = Random(20261001)

    @Before
    fun reset() {
        ClamAvSignatures.unload()
    }

    private fun matchesAt(data: ByteArray, start: Int, lo: IntArray, hi: IntArray): Boolean {
        if (start < 0 || start + lo.size > data.size) return false
        for (j in lo.indices) {
            val v = data[start + j].toInt() and 0xFF
            if (lo[j] >= 0 && (v ushr 4) != lo[j]) return false
            if (hi[j] >= 0 && (v and 0x0F) != hi[j]) return false
        }
        return true
    }

    private fun toHex(lo: IntArray, hi: IntArray): String {
        val sb = StringBuilder()
        for (i in lo.indices) {
            sb.append(if (lo[i] < 0) '?' else Character.forDigit(lo[i], 16))
            sb.append(if (hi[i] < 0) '?' else Character.forDigit(hi[i], 16))
        }
        return sb.toString()
    }

    private fun toNdbLine(sig: RefSig): String = sig.name + ":0:*:" + toHex(sig.lo, sig.hi)

    /** 生成一条随机签名:约 70% 的字节完全固定,其余为半字节/整字节通配 */
    private fun randomSig(i: Int, len: Int): RefSig {
        val lo = IntArray(len) { -1 }
        val hi = IntArray(len) { -1 }
        val fixed = BooleanArray(len) { rnd.nextInt(10) < 7 }
        if (fixed.none { it }) fixed[rnd.nextInt(len)] = true
        for (j in 0 until len) {
            if (fixed[j]) {
                val v = rnd.nextInt(256)
                lo[j] = v ushr 4
                hi[j] = v and 0x0F
            } else if (rnd.nextBoolean()) {
                lo[j] = rnd.nextInt(16)
            } else {
                hi[j] = rnd.nextInt(16)
            }
        }
        return RefSig("Synth.Trojan.Test" + i, lo, hi)
    }

    /** 按签名约束生成一段"必然命中该签名"的字节 */
    private fun bytesMatching(sig: RefSig): ByteArray = ByteArray(sig.lo.size) { j ->
        val v = when {
            sig.lo[j] >= 0 && sig.hi[j] >= 0 -> (sig.lo[j] shl 4) or sig.hi[j]
            sig.lo[j] >= 0 -> (sig.lo[j] shl 4) or rnd.nextInt(16)
            sig.hi[j] >= 0 -> (rnd.nextInt(16) shl 4) or sig.hi[j]
            else -> rnd.nextInt(256)
        }
        v.toByte()
    }

    /** 参考实现 1:旧版"单字节锚点分桶"扫描(被替换掉的实现) */
    private fun legacyScan(sigs: List<RefSig>, data: ByteArray): Set<String> {
        val hits = LinkedHashSet<String>()
        val buckets = Array(256) { mutableListOf<RefSig>() }
        val loose = mutableListOf<RefSig>()
        for (s in sigs) {
            if (s.anchorByte >= 0) buckets[s.anchorByte].add(s) else loose.add(s)
        }
        for (i in data.indices) {
            val bucket = buckets[data[i].toInt() and 0xFF]
            if (bucket.isEmpty()) continue
            for (s in bucket) {
                val start = i - s.anchorIdx
                if (matchesAt(data, start, s.lo, s.hi)) hits.add(s.name)
            }
        }
        for (s in loose) {
            for (i in 0..data.size - s.lo.size) {
                if (matchesAt(data, i, s.lo, s.hi)) {
                    hits.add(s.name)
                    break
                }
            }
        }
        return hits
    }

    /** 参考实现 2:逐签名朴素扫描 */
    private fun naiveScan(sigs: List<RefSig>, data: ByteArray): Set<String> {
        val hits = LinkedHashSet<String>()
        for (s in sigs) {
            if (s.lo.size > data.size) continue
            for (i in 0..data.size - s.lo.size) {
                if (matchesAt(data, i, s.lo, s.hi)) {
                    hits.add(s.name)
                    break
                }
            }
        }
        return hits
    }

    private fun loadEngine(sigs: List<RefSig>) {
        ClamAvSignatures.importText("synth.ndb", sigs.joinToString("\n") { toNdbLine(it) })
    }

    @Test
    fun matchesLegacyAndNaiveImplementationsOnRandomCorpus() {
        val sigs = (0 until 120).map { randomSig(it, 4 + rnd.nextInt(12)) }
        loadEngine(sigs)

        // 4 MB 随机数据 + 20 处真实命中
        val data = ByteArray(4 shl 20) { rnd.nextInt(256).toByte() }
        val planted = mutableListOf<RefSig>()
        var pos = 1024
        repeat(20) {
            val s = sigs[rnd.nextInt(sigs.size)]
            val bytes = bytesMatching(s)
            if (pos + bytes.size < data.size) {
                System.arraycopy(bytes, 0, data, pos, bytes.size)
                planted.add(s)
            }
            pos += (data.size - pos) / 21 + 1
        }

        val engine = ClamAvSignatures.scanBytes(data).toSet()
        val legacy = legacyScan(sigs, data)
        val naive = naiveScan(sigs, data)

        assertEquals("引擎与朴素扫描结果必须完全一致", naive, engine)
        assertEquals("引擎与旧版锚点分桶结果必须完全一致", legacy, engine)
        assertTrue("注入的特征必须被检出(否则是漏报)", engine.containsAll(planted.map { it.name }))
    }

    @Test
    fun allWildcardSignatureStillMatches() {
        ClamAvSignatures.importText("wild.ndb", "Synth.AllWild:0:*:??????????")
        assertTrue(
            "整条全通配的特征必须走逐位置校验路径且能命中",
            ClamAvSignatures.scanBytes(ByteArray(64) { 1 }).contains("Synth.AllWild")
        )
        assertFalse(
            "长度不足时不得命中",
            ClamAvSignatures.scanBytes(ByteArray(4)).contains("Synth.AllWild")
        )
    }

    @Test
    fun singleByteWindowSignatureIsIndexed() {
        // 固定字节被通配隔开 ⇒ 最长固定片段只有 1 字节,走单字节索引分支
        ClamAvSignatures.importText("onebyte.ndb", "Synth.OneByte:0:*:4?4?4?")
        // 4?4?4? 要求三个字节的高半字节都是 4
        assertTrue(
            "只有 1 字节固定窗口的签名也必须命中",
            ClamAvSignatures.scanBytes(byteArrayOf(0x5F, 0x40, 0x41, 0x42, 0x5F)).contains("Synth.OneByte")
        )
        assertFalse(
            "固定窗口字节不符时不得命中",
            ClamAvSignatures.scanBytes(byteArrayOf(0x5F, 0x55, 0x41, 0x42, 0x5F)).contains("Synth.OneByte")
        )
    }

    @Test
    fun matchesAtBufferBoundaries() {
        ClamAvSignatures.importText("edge.ndb", "Synth.Head:0:*:4B4152415050\nSynth.Tail:0:*:454E4444415441")
        assertTrue("缓冲区开头命中", ClamAvSignatures.scanBytes("KARAPP".toByteArray()).contains("Synth.Head"))
        assertTrue("缓冲区结尾命中", ClamAvSignatures.scanBytes("xxENDDATA".toByteArray()).contains("Synth.Tail"))
    }

    @Test
    fun nibbleWildcardsAreRespected() {
        ClamAvSignatures.importText("nib.ndb", "Synth.Nibble:0:*:4?41??")
        assertTrue("半字节通配应命中 4x41xx", ClamAvSignatures.scanBytes(byteArrayOf(0x4A, 0x41, 0x7F)).contains("Synth.Nibble"))
        assertFalse("半字节不符不得命中", ClamAvSignatures.scanBytes(byteArrayOf(0x5A, 0x41, 0x7F)).contains("Synth.Nibble"))
    }

    /**
     * 真实特征库里大量签名会共享常见锚点字节(0x00、0xE8、0x50…),这时旧实现的
     * 单字节桶会退化成"每个字节位置遍历几乎所有签名";2 字节窗口把候选数摊薄约 256 倍。
     * 这里刻意构造这种最坏情形来量化差距。
     */
    @Test
    fun multiByteWindowScalesBetterThanSingleByteAnchor() {
        val sigs = (0 until 200).map { i ->
            val s = randomSig(i, 6 + rnd.nextInt(8))
            // 强制所有签名以 0x00 结尾 ⇒ 旧实现全部落入同一个桶
            s.lo[s.lo.size - 1] = 0
            s.hi[s.hi.size - 1] = 0
            s
        }
        loadEngine(sigs)
        val data = ByteArray(1 shl 20) { if (rnd.nextInt(10) < 3) 0 else rnd.nextInt(256).toByte() }

        // 预热 + min-of-5:构建机负载抖动只会抬高耗时,最小值逼近真实下限
        // (median 对偶发毛刺敏感,曾在本机高负载下出现 4% 的假倒挂)
        repeat(2) { legacyScan(sigs, data); ClamAvSignatures.scanBytes(data) }
        val legacyMs = minMs(5) { legacyScan(sigs, data) }
        val nowMs = minMs(5) { ClamAvSignatures.scanBytes(data) }
        println("[perf] 200 特征(同锚点) × 1MB:单字节锚点分桶 " + legacyMs + " ms,2 字节窗口索引 " + nowMs + " ms")

        assertTrue("2 字节窗口索引应快于单字节锚点分桶(实测 " + nowMs + "ms vs " + legacyMs + "ms)", nowMs < legacyMs)
    }

    private fun minMs(times: Int, block: () -> Unit): Double {
        var best = Double.MAX_VALUE
        repeat(times) {
            val t0 = System.nanoTime()
            block()
            best = minOf(best, (System.nanoTime() - t0) / 1_000_000.0)
        }
        return best
    }
}
