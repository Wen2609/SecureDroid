package com.armorlab.securedroid.scan

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单遍多模式匹配器测试。
 *
 * 它替换的是 DEX 行为扫描里"每条规则的每个模式都跑一遍 strings.any { contains }"
 * 的暴力实现(单个应用约 40 遍全量扫描)。这里用随机语料做**等价性**验证:
 * 优化只允许变快,不允许改变命中结果。
 */
class MultiPatternMatcherTest {

    private val patterns = listOf(
        "Runtime.exec", "http://", "/system/bin/su", "getDeviceId", "中文恶意", "", "Runtime.exec"
    )

    private fun brute(strings: Collection<String>, p: String): Boolean = strings.any { it.contains(p) }

    @Test
    fun duplicatesAreDedupedAndIndexable() {
        val m = MultiPatternMatcher(patterns)
        assertEquals(6, m.size)
        assertEquals("Runtime.exec", m.patternAt(0))
        assertEquals(0, m.indexOf("Runtime.exec"))
        assertEquals(-1, m.indexOf("nope"))
    }

    @Test
    fun scanIsEquivalentToBruteForceOnSeededCorpus() {
        val m = MultiPatternMatcher(patterns)
        val rnd = Random(20261002)
        val alphabet = listOf(
            "a", "R", "u", "n", "t", "i", "m", "e", ".", "x", "c", "h", "p", ":", "/", "s",
            "D", "v", "I", "d", "g", "中", "文", "恶", "意", " "
        )
        val corpus = mutableListOf<List<String>>()
        corpus.add(emptyList())
        corpus.add(listOf(""))
        repeat(80) {
            corpus.add(
                (0 until rnd.nextInt(1, 6)).map {
                    (0 until rnd.nextInt(0, 48)).joinToString("") { alphabet[rnd.nextInt(alphabet.size)] }
                }
            )
        }
        for (texts in corpus) {
            val hits = m.scan(texts)
            for (i in m.patterns().indices) {
                assertEquals(
                    "模式#" + i + "(" + m.patternAt(i) + ") 在 " + texts + " 上结果与暴力实现不一致",
                    brute(texts, m.patternAt(i)),
                    hits[i]
                )
            }
        }
    }

    @Test
    fun emptyCorpusMatchesNothingIncludingBlankPattern() {
        val m = MultiPatternMatcher(listOf("", "ab"))
        assertFalse(m.scan(emptyList()).any { it })
        val hits = m.scan(listOf(""))
        assertTrue("文本集合非空时空模式恒命中(与暴力实现的 strings.any { contains(空串) } 等价)", hits[0])
        assertFalse(hits[1])
    }

    @Test
    fun perTextEntryPoints() {
        val m = MultiPatternMatcher(listOf("ab", "cd"))
        assertTrue(m.matchesAny("xxab"))
        assertFalse(m.matchesAny("xx"))
        val hits = m.matchAt("xxab")
        assertTrue(hits[0])
        assertFalse(hits[1])
    }

    @Test
    fun nonAsciiPatternsAreMatchedToo() {
        val m = MultiPatternMatcher(listOf("中文恶意", "abc"))
        assertTrue(m.matchesAny("前缀中文恶意后缀"))
        assertFalse(m.matchesAny("前缀中恶意"))
        assertTrue(m.matchesAny("xxabcxx"))
    }
}
