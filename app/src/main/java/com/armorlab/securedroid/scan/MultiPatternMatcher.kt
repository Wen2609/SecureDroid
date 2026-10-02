package com.armorlab.securedroid.scan

/**
 * 多模式字面量匹配器(单遍扫描)。
 *
 * 背景:行为规则引擎原来对"每个规则的每个模式"都做一次
 * strings.any { it.contains(p) },也就是对每个应用提取出的整个 dex 字符串集合
 * 反复全量扫描 ~35-45 遍,这是深度扫描里最热的 CPU 循环。
 *
 * 本实现按"模式首字符"分桶:扫描文本时只在首字符命中桶的位置做 startsWith 校验,
 * 把复杂度从 O(模式数 × 文本长度) 降到 O(文本长度 × 平均桶宽)。ASCII 走数组索引,
 * 非 ASCII(自定义规则里可能出现中文)走哈希表回退,两者语义一致。
 *
 * 语义与暴力实现严格等价(单元测试与暴力法逐例对比):
 * - 纯字面量,不是正则;
 * - 模式在多个文本中任意位置出现即命中;
 * - 空模式等价于 contains("")(文本集合非空时恒命中);
 * - 重复模式去重,但 [indexOf] 对每个不同模式都给出稳定下标。
 */
class MultiPatternMatcher(patterns: Collection<String>) {

    private val keys: List<String> = LinkedHashSet(patterns).toList()
    private val index = HashMap<String, Int>(keys.size * 2)
    private val asciiBuckets = IntArray(128) { -1 }
    private val wideBuckets = HashMap<Char, Int>(4)
    private val bucketPatterns: Array<IntArray>
    private val hasBlank: Boolean

    init {
        for (i in keys.indices) index[keys[i]] = i
        hasBlank = keys.any { it.isEmpty() }
        val bucketOf = HashMap<Char, Int>(keys.size * 2)
        val tmp = ArrayList<MutableList<Int>>()
        for (i in keys.indices) {
            val p = keys[i]
            if (p.isEmpty()) continue
            val c = p[0]
            var bi = bucketOf[c]
            if (bi == null) {
                tmp.add(ArrayList())
                bi = tmp.size - 1
                bucketOf[c] = bi
            }
            tmp[bi].add(i)
        }
        bucketPatterns = Array(tmp.size) { IntArray(tmp[it].size) { j -> tmp[it][j] } }
        for ((c, bi) in bucketOf) {
            if (c.code < 128) asciiBuckets[c.code] = bi else wideBuckets[c] = bi
        }
    }

    val size: Int get() = keys.size

    fun patternAt(i: Int): String = keys[i]

    /** 模式(去重后)在命中数组中的下标;不存在返回 -1 */
    fun indexOf(pattern: String): Int = index[pattern] ?: -1

    fun patterns(): List<String> = keys

    /** 单段文本是否命中任意模式 */
    fun matchesAny(text: String): Boolean {
        if (hasBlank && text.isNotEmpty()) return true
        val hits = BooleanArray(keys.size)
        hitIn(text, hits)
        for (h in hits) if (h) return true
        return false
    }

    /** 命中某模式的全部文本位置(测试与调试用) */
    fun matchAt(text: String): BooleanArray {
        val hits = BooleanArray(keys.size)
        if (text.isNotEmpty() && hasBlank) {
            for (i in keys.indices) if (keys[i].isEmpty()) hits[i] = true
        }
        hitIn(text, hits)
        return hits
    }

    /**
     * 扫描整个文本集合,返回与 [patterns] 对齐的命中标记。
     * 文本集合为空时不做任何匹配(等价于暴力实现的 strings.any {} 恒为 false)。
     */
    fun scan(texts: Iterable<String>): BooleanArray {
        val hits = BooleanArray(keys.size)
        var nonEmpty = false
        for (t in texts) {
            nonEmpty = true
            hitIn(t, hits)
        }
        if (nonEmpty && hasBlank) {
            for (i in keys.indices) if (keys[i].isEmpty()) hits[i] = true
        }
        return hits
    }

    private fun hitIn(text: String, hits: BooleanArray) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            val bucket = if (c.code < 128) asciiBuckets[c.code] else (wideBuckets[c] ?: -1)
            if (bucket >= 0) {
                val arr = bucketPatterns[bucket]
                for (k in arr.indices) {
                    val pi = arr[k]
                    if (!hits[pi] && text.startsWith(keys[pi], i)) hits[pi] = true
                }
            }
            i++
        }
    }
}
