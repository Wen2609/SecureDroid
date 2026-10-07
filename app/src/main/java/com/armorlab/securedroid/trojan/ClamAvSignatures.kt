package com.armorlab.securedroid.trojan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import java.io.BufferedReader
import java.io.File
import java.util.zip.ZipFile

/**
 * ClamAV 签名格式兼容层。
 *
 * 仅兼容 ClamAV 的签名【文件格式】,不含也不复用 ClamAV 任何代码
 * (ClamAV 为 GPL,格式互操作不受影响)。格式定义依据 ClamAV 官方文档:
 * https://docs.clamav.net/manual/Signatures/ExtendedSignatures.html
 * https://docs.clamav.net/manual/Signatures/HashSignatures.html
 *
 * 支持的数据库文件:
 * - .hsb / .hsu : 整文件 SHA-256 哈希签名,行格式  hash:文件大小:名称  (大小为 0 表示忽略大小);
 * - .hdb / .hdu : 整文件 MD5 哈希签名,行格式  md5:文件大小:名称(大小为 0 表示忽略大小);
 * - .ndb / .ndu : 扩展字节特征签名,行格式
 *     名称:目标类型:偏移:HEX特征[:min_flevel[:max_flevel]]
 *   其中偏移支持 * (任意位置)、绝对偏移 n、(EOF-n)(文件尾往前 n 字节)
 *   以及浮动偏移 n,MaxShift(表示 n..n+MaxShift 区间内匹配)。
 *
 * 特征来源:
 * 1. assets/signatures/ 内置演示签名;
 * 2. 应用私有目录 files/clamav/ 下的外部特征文件(如 ClamAV daily.cvd 解包结果),支持热加载。
 *
 * 兼容历史:本项目早期使用自定义分号分隔格式(名称;偏移;HEX;严重度;目标),仍可解析,
 * 便于旧演示特征继续工作。
 */
object ClamAvSignatures {

    /** 特征在目标中的位置语义 */
    enum class OffsetKind { ANY, ABS, EOF }

    class ByteSig(
        val name: String,
        val lo: ByteArray,
        val hi: ByteArray,
        val offsetKind: OffsetKind = OffsetKind.ANY,
        val offset: Int = 0,
        /** 浮动偏移上限:匹配 offset..offset+maxShift 区间(ClamAV 的 MaxShift) */
        val maxShift: Int = 0,
        /** ClamAV TargetType(0 = 任意;1 = PE;6 = ELF;…),仅用于展示与判读 */
        val targetType: Int = 0
    ) {
        /** 位置是否固定(绝对偏移或文件尾偏移),固定位置无需全量扫描 */
        val isPositional: Boolean get() = offsetKind != OffsetKind.ANY
    }

    /** 2 字节窗口条目:签名 + 窗口在签名内的起始偏移 */
    private class Window(val sig: ByteSig, val offsetInSig: Int)

    /**
     * 不可变签名索引:多字节窗口直接索引 + 位置固定型直接定位。
     *
     * 设计取舍(有实测数据支撑,不要凭直觉改回去):
     * 曾按教科书实现 Aho-Corasick 自动机(CSR 存 goto 边 + 每字节二分查找 + 失效链回溯),
     * 在"200 特征 × 1MB"实测中反而比单字节锚点分桶慢 3.6 倍(127ms vs 35ms):
     * AC 的复杂度优势在于与特征条数无关,但每字节的二分查找与失效链回溯常数开销过大,
     * 在数百条特征这个量级得不偿失。改用"2 字节窗口 + 65536 直接表"后,
     * 每字节只做一次数组寻址,候选数由 ~N/256 降到 ~N/65536。
     * 若将来特征库增长到数万条,应改用带密集转移表的 AC 或双数组 Trie。
     *
     * pairHead / byteHead 为链表头(值为条目下标,-1 表示空),next 为链表后继;
     * 全部为原始数组:无装箱、无 HashMap,扫描期零分配。
     */
    private class SigIndex(
        val pairHead: IntArray,
        val byteHead: IntArray,
        val next: IntArray,
        val sigOf: Array<ByteSig?>,
        val offsetOf: IntArray,
        val hasByte: Boolean,
        val loose: List<ByteSig>,
        val positional: List<ByteSig>
    )

    /** 签名中最长的连续固定字节片段(起始下标, 长度);整条全通配时返回 null */
    private fun fixedRun(sig: ByteSig): Pair<Int, Int>? {
        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < sig.lo.size) {
            if (sig.lo[i].toInt() >= 0 && sig.hi[i].toInt() >= 0) {
                val s = i
                while (i < sig.lo.size && sig.lo[i].toInt() >= 0 && sig.hi[i].toInt() >= 0) i++
                if (i - s > bestLen) {
                    bestLen = i - s
                    bestStart = s
                }
            } else {
                i++
            }
        }
        return if (bestStart < 0) null else Pair(bestStart, bestLen)
    }

    /** 构建不可变索引(调用方需持有 lock);整表原子替换,扫描侧无需加锁 */
    private fun buildIndex(): SigIndex {
        val loose = mutableListOf<ByteSig>()
        val positional = mutableListOf<ByteSig>()
        val pairEntries = ArrayList<Window>()
        val pairKeys = ArrayList<Int>()
        val byteEntries = ArrayList<Window>()
        val byteKeys = ArrayList<Int>()

        for (s in byteSigs) {
            if (s.isPositional) {
                positional.add(s)
                continue
            }
            val run = fixedRun(s)
            if (run == null) {
                loose.add(s)
                continue
            }
            val at = run.first
            if (run.second >= 2) {
                val b0 = (s.lo[at].toInt() shl 4) or s.hi[at].toInt()
                val b1 = (s.lo[at + 1].toInt() shl 4) or s.hi[at + 1].toInt()
                pairKeys.add((b0 shl 8) or b1)
                pairEntries.add(Window(s, at))
            } else {
                byteKeys.add((s.lo[at].toInt() shl 4) or s.hi[at].toInt())
                byteEntries.add(Window(s, at))
            }
        }

        val total = pairEntries.size + byteEntries.size
        val sigOf = arrayOfNulls<ByteSig>(total)
        val offsetOf = IntArray(total)
        val next = IntArray(total) { -1 }
        val pairHead = IntArray(65536) { -1 }
        val byteHead = IntArray(256) { -1 }

        for (i in pairEntries.indices) {
            val w = pairEntries[i]
            sigOf[i] = w.sig
            offsetOf[i] = w.offsetInSig
            val key = pairKeys[i]
            next[i] = pairHead[key]
            pairHead[key] = i
        }
        val base = pairEntries.size
        for (i in byteEntries.indices) {
            val id = base + i
            val w = byteEntries[i]
            sigOf[id] = w.sig
            offsetOf[id] = w.offsetInSig
            val key = byteKeys[i]
            next[id] = byteHead[key]
            byteHead[key] = id
        }

        return SigIndex(pairHead, byteHead, next, sigOf, offsetOf, byteEntries.isNotEmpty(), loose, positional)
    }
    private val lock = Any()
    private val hashSigs = HashMap<String, Pair<Long, String>>()
    private val md5Sigs = HashMap<String, Pair<Long, String>>()
    private val byteSigs = mutableListOf<ByteSig>()

    /**
     * 总条数预算(哈希 + MD5 + 字节):官方 daily.cvd 解包可达百万级,
     * 全量进 HashMap 在低内存设备会 OOM。超预算即停止载入并置截断标记,
     * 统计与更新结果会把截断状态亮给用户。测试可调小触发。
     */
    internal var entryBudget = 1_500_000

    /** 载入时堆余量(含未分配堆)低于该值即截断:堆水位兜底,防 OOM 崩溃 */
    private val minFreeBytes = 24L * 1024 * 1024

    @Volatile
    private var truncated = false

    @Volatile
    private var index: SigIndex? = null

    private var loaded = false

    /** 重建不可变索引(调用方需持有 lock);整表原子替换,扫描侧无需加锁 */
    private fun rebuildIndex() {
        index = buildIndex()
    }

    fun ensureLoaded(context: Context) {
        synchronized(lock) {
            if (loaded) return
            try {
                val names = context.assets.list("signatures") ?: arrayOf<String>()
                for (name in names) {
                    context.assets.open("signatures/" + name).bufferedReader().use {
                        parseFileLines(it, name)
                    }
                }
            } catch (_: Exception) {
            }
            try {
                val dir = File(context.filesDir, "clamav")
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { f ->
                        f.bufferedReader().use { parseFileLines(it, f.name) }
                    }
                }
            } catch (_: Exception) {
            }
            rebuildIndex()
            loaded = true
        }
    }

    private fun parseFileLines(reader: BufferedReader, fileName: String) {
        val lower = fileName.lowercase()
        reader.forEachLine { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEachLine
            when {
                lower.endsWith(".hsb") || lower.endsWith(".hsu") -> parseHashLine(t)
                lower.endsWith(".hdb") || lower.endsWith(".hdu") -> parseMd5Line(t)
                lower.endsWith(".ndb") || lower.endsWith(".ndu") -> parseByteLine(t)
            }
        }
    }

    /** 追加预算检查(调用方需持有 lock):超条数上限或堆余量过低即截断 */
    private fun budgetExhausted(): Boolean {
        if (truncated) return true
        if (hashSigs.size + md5Sigs.size + byteSigs.size >= entryBudget) return true
        val rt = Runtime.getRuntime()
        if (rt.maxMemory() - rt.totalMemory() + rt.freeMemory() < minFreeBytes) {
            // 先触发一次回收再判,避免把可回收垃圾当成堆余量不足(只在低水位才会走到,非热路径)
            System.gc()
            if (rt.maxMemory() - rt.totalMemory() + rt.freeMemory() < minFreeBytes) return true
        }
        return false
    }

    /** .hsb:hash:文件大小:名称 */
    private fun parseHashLine(t: String) {
        val parts = t.split(':', limit = 3)
        if (parts.size < 3 || parts[0].length != 64) return
        if (parts[0].any { Character.digit(it, 16) < 0 }) return
        val size = parts[1].toLongOrNull() ?: 0L
        synchronized(lock) {
            if (budgetExhausted()) {
                truncated = true
                return
            }
            hashSigs[parts[0].lowercase()] = Pair(size, parts[2])
        }
    }

    /** .hdb:md5:文件大小:名称 */
    private fun parseMd5Line(t: String) {
        val parts = t.split(':', limit = 3)
        if (parts.size < 3 || parts[0].length != 32) return
        if (parts[0].any { Character.digit(it, 16) < 0 }) return
        val size = parts[1].toLongOrNull() ?: 0L
        synchronized(lock) {
            if (budgetExhausted()) {
                truncated = true
                return
            }
            md5Sigs[parts[0].lowercase()] = Pair(size, parts[2])
        }
    }

    /**
     * .ndb:优先按 ClamAV 官方冒号格式解析:
     *   名称:目标类型:偏移:HEX[:min_flevel[:max_flevel]]
     * 无法匹配时回退到历史分号格式(名称;偏移;HEX;严重度;目标)。
     */
    private fun parseByteLine(t: String) {
        parseOfficialByteLine(t)?.let { sig ->
            synchronized(lock) {
                if (budgetExhausted()) truncated = true else byteSigs.add(sig)
            }
            return
        }
        parseLegacyByteLine(t)?.let { sig ->
            synchronized(lock) {
                if (budgetExhausted()) truncated = true else byteSigs.add(sig)
            }
        }
    }

    private fun parseOfficialByteLine(t: String): ByteSig? {
        val parts = t.split(':')
        if (parts.size < 4) return null
        val targetType = parts[1].trim().toIntOrNull() ?: return null
        val (kind, offset, shift) = parseOffset(parts[2]) ?: return null
        val nib = parseHex(parts[3]) ?: return null
        val name = parts[0].trim()
        if (name.isEmpty()) return null
        return ByteSig(name, nib.first, nib.second, kind, offset, shift, targetType)
    }

    private fun parseLegacyByteLine(t: String): ByteSig? {
        val parts = t.split(';')
        if (parts.size < 3) return null
        val nib = parseHex(parts[2]) ?: return null
        return ByteSig(parts[0].trim(), nib.first, nib.second)
    }

    /**
     * 解析 ClamAV 偏移字段。
     * 支持:* (任意) | n (绝对偏移) | EOF-n (文件尾往前 n) | Offset,MaxShift (浮动偏移)。
     * EP+/Sx+/SL+ 等仅对 PE/ELF/Mach-O 生效的语义无法在通用字节流上判定,
     * 为保证不漏报,统一按"任意位置"处理。
     */
    private fun parseOffset(raw: String): Triple<OffsetKind, Int, Int>? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s == "*") return Triple(OffsetKind.ANY, 0, 0)
        val comma = s.split(',')
        val base = comma[0].trim()
        val shift = comma.getOrNull(1)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        return when {
            base.startsWith("EOF-") -> {
                val n = base.removePrefix("EOF-").trim().toIntOrNull() ?: return null
                Triple(OffsetKind.EOF, n, shift)
            }
            base.toIntOrNull() != null -> Triple(OffsetKind.ABS, base.toInt(), shift)
            // EP+n / Sx+n / SL+n 等:退化为任意位置
            else -> Triple(OffsetKind.ANY, 0, 0)
        }
    }

    /** 解析十六进制串为半字节期望值数组,-1 表示该半字节为通配(?) */
    private fun parseHex(hex: String): Pair<ByteArray, ByteArray>? {
        val s = hex.replace(" ", "")
        if (s.isEmpty() || s.length % 2 != 0) return null
        val lo = ByteArray(s.length / 2)
        val hi = ByteArray(s.length / 2)
        for (i in s.indices step 2) {
            val a = s[i]
            val b = s[i + 1]
            val loV = if (a == '?') -1 else Character.digit(a, 16)
            val hiV = if (b == '?') -1 else Character.digit(b, 16)
            if (loV > 15 || hiV > 15) return null
            lo[i / 2] = loV.toByte()
            hi[i / 2] = hiV.toByte()
        }
        return Pair(lo, hi)
    }

    fun matchHash(sha256: String, fileSize: Long): Pair<String, String>? =
        synchronized(lock) {
            hashSigs[sha256.lowercase()]?.let { (wantSize, name) ->
                if (wantSize == 0L || wantSize == fileSize)
                    Pair(name, "APK 整文件 SHA-256 命中 ClamAV 特征")
                else null
            }
        }

    fun matchMd5(md5: String, fileSize: Long): Pair<String, String>? =
        synchronized(lock) {
            md5Sigs[md5.lowercase()]?.let { (wantSize, name) ->
                if (wantSize == 0L || wantSize == fileSize)
                    Pair(name, "APK 整文件 MD5 命中 ClamAV 特征")
                else null
            }
        }

    /** 扫描 APK 内 dex / so 文件的字节特征(单文件 8MB、单包 24MB 上限) */
    fun scanApk(apkPath: String): List<TrojanScanner.Detection> {
        val hits = mutableListOf<TrojanScanner.Detection>()
        val sigs = synchronized(lock) { byteSigs.size }
        if (sigs == 0) return hits
        try {
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                var used = 0L
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val n = e.name
                    if (!(n.endsWith(".dex") || n.endsWith(".so"))) continue
                    if (e.size > 8L * 1024 * 1024 || used > 24L * 1024 * 1024) continue
                    val data = zip.getInputStream(e).use { it.readBytes() }
                    used += data.size
                    for (name in scanBytes(data)) {
                        hits.add(
                            TrojanScanner.Detection(
                                "ClamAV 字节码", name, ThreatLevel.HIGH,
                                "在 " + n + " 中命中字节特征"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return hits
    }

    /** 半字节级通配校验:在 start 处整条匹配 */
    private fun matchesAt(data: ByteArray, start: Int, sig: ByteSig): Boolean {
        val n = sig.lo.size
        for (j in 0 until n) {
            val v = data[start + j].toInt() and 0xFF
            if (sig.lo[j].toInt() >= 0 && (v ushr 4) != sig.lo[j].toInt()) return false
            if (sig.hi[j].toInt() >= 0 && (v and 0x0F) != sig.hi[j].toInt()) return false
        }
        return true
    }

    /** 单签名匹配(内部使用,任意位置) */
    private fun contains(data: ByteArray, sig: ByteSig): Boolean {
        val n = sig.lo.size
        if (data.size < n) return false
        for (i in 0..data.size - n) {
            if (matchesAt(data, i, sig)) return true
        }
        return false
    }

    /**
     * 对任意字节流做字节码特征匹配(内存 / 分区扫描用)。
     *
     * 三类签名分别走不同路径,兼顾正确性与性能:
     * 1. 位置固定型(绝对偏移 / EOF 偏移):直接按偏移定位,零扫描开销;
     * 2. 任意偏移型:单遍扫描 + 2 字节窗口直接索引,每字节 O(1) 寻址,
     *    仅窗口命中的少量候选才做半字节级复核;
     * 3. 全通配型(无固定字节):逐位置校验(极少,通常仅演示特征)。
     */
    fun scanBytes(data: ByteArray): List<String> {
        val idx = index ?: return emptyList()
        val hits = LinkedHashSet<String>()

        for (sig in idx.positional) {
            val base = when (sig.offsetKind) {
                OffsetKind.ABS -> sig.offset
                OffsetKind.EOF -> data.size - sig.offset
                OffsetKind.ANY -> continue
            }
            if (base < 0) continue
            var shift = 0
            while (shift <= sig.maxShift) {
                val at = base + shift
                if (at + sig.lo.size <= data.size && matchesAt(data, at, sig)) {
                    hits.add(sig.name)
                    break
                }
                shift++
            }
        }

        val n = data.size
        // 任意偏移型(有 ≥2 字节固定窗口):按窗口值直接命中候选链表
        for (i in 0 until n - 1) {
            val key = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            var id = idx.pairHead[key]
            while (id >= 0) {
                val sig = idx.sigOf[id]
                if (sig != null) {
                    val at = i - idx.offsetOf[id]
                    if (at >= 0 && at + sig.lo.size <= n && matchesAt(data, at, sig)) hits.add(sig.name)
                }
                id = idx.next[id]
            }
        }

        // 固定窗口只有 1 字节的签名(如 4?4?4?):单独一趟,通常为空
        if (idx.hasByte) {
            for (i in data.indices) {
                var id = idx.byteHead[data[i].toInt() and 0xFF]
                while (id >= 0) {
                    val sig = idx.sigOf[id]
                    if (sig != null) {
                        val at = i - idx.offsetOf[id]
                        if (at >= 0 && at + sig.lo.size <= n && matchesAt(data, at, sig)) hits.add(sig.name)
                    }
                    id = idx.next[id]
                }
            }
        }

        for (sig in idx.loose) {
            if (contains(data, sig)) hits.add(sig.name)
        }
        return hits.toList()
    }

    fun hashCount(): Int = synchronized(lock) { hashSigs.size }

    fun md5Count(): Int = synchronized(lock) { md5Sigs.size }

    fun byteCount(): Int = synchronized(lock) { byteSigs.size }

    /** 位置固定型签名数量(统计与展示用) */
    fun positionalCount(): Int = synchronized(lock) { byteSigs.count { it.isPositional } }

    /**
     * 从文本导入特征库(不落盘)。文件名决定解析格式(.ndb/.ndu → 字节特征,
     * .hsb/.hsu → 哈希特征)。供特征库更新器校验后热加载,也便于测试注入合成特征。
     */
    fun importText(fileName: String, text: String) {
        synchronized(lock) {
            val lower = fileName.lowercase()
            text.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) return@forEach
                when {
                    lower.endsWith(".hsb") || lower.endsWith(".hsu") -> parseHashLine(t)
                    lower.endsWith(".hdb") || lower.endsWith(".hdu") -> parseMd5Line(t)
                    else -> parseByteLine(t)
                }
            }
            rebuildIndex()
            loaded = true
        }
    }

    /** 清空内存中的特征库(测试、切换特征库时使用) */
    fun unload() {
        synchronized(lock) {
            hashSigs.clear()
            md5Sigs.clear()
            byteSigs.clear()
            truncated = false
            rebuildIndex()
            loaded = false
        }
    }

    /** 本次载入是否因条数预算/堆水位被截断(统计与更新结果展示用) */
    fun isTruncated(): Boolean = synchronized(lock) { truncated }

    /** 热重载:清空已加载签名并重新从 assets / files/clamav 加载 */
    fun reload(context: Context) {
        unload()
        ensureLoaded(context)
    }

    fun signatureCount(): Int = synchronized(lock) { hashSigs.size + md5Sigs.size + byteSigs.size }
}
