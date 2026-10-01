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
 * - .ndb / .ndu : 扩展字节特征签名,行格式
 *     名称:目标类型:偏移:HEX特征[:min_flevel[:max_flevel]]
 *   其中偏移支持 * (任意位置)、绝对偏移 n、(EOF-n)(文件尾往前 n 字节)
 *   以及浮动偏移 n,MaxShift(表示 n..n+MaxShift 区间内匹配);
 * - .hdb (MD5 整文件哈希)暂不支持:本引擎按 SHA-256 计算文件指纹。
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
        /** 最后一个两个半字节都固定(非通配)的字节下标;整条全通配时为 -1 */
        val anchorIdx: Int = run {
            var i = lo.size - 1
            while (i >= 0) {
                if (lo[i].toInt() >= 0 && hi[i].toInt() >= 0) return@run i
                i--
            }
            -1
        }

        /** 锚点字节值;无锚点返回 -1 */
        val anchorByte: Int =
            if (anchorIdx >= 0) ((lo[anchorIdx].toInt() shl 4) or hi[anchorIdx].toInt()) else -1

        /** 位置是否固定(绝对偏移或文件尾偏移),固定位置无需全量扫描 */
        val isPositional: Boolean get() = offsetKind != OffsetKind.ANY
    }

    /** 不可变签名索引:任意偏移型按锚点分桶单遍匹配,位置固定型直接定位 |
     * loose 为整条全通配(无锚点)的签名,需逐位置校验。
     */
    private class SigIndex(
        val byAnchor: Array<List<ByteSig>>,
        val loose: List<ByteSig>,
        val positional: List<ByteSig>
    )

    private val lock = Any()
    private val hashSigs = HashMap<String, Pair<Long, String>>()
    private val byteSigs = mutableListOf<ByteSig>()

    @Volatile
    private var index: SigIndex? = null

    private var loaded = false

    /** 重建不可变索引(调用方需持有 lock);整表原子替换,扫描侧无需加锁 */
    private fun rebuildIndex() {
        val buckets = Array(256) { mutableListOf<ByteSig>() }
        val loose = mutableListOf<ByteSig>()
        val positional = mutableListOf<ByteSig>()
        for (s in byteSigs) {
            when {
                s.isPositional -> positional.add(s)
                s.anchorByte >= 0 -> buckets[s.anchorByte].add(s)
                else -> loose.add(s)
            }
        }
        index = SigIndex(Array(256) { buckets[it].toList() }, loose.toList(), positional.toList())
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
                lower.endsWith(".ndb") || lower.endsWith(".ndu") -> parseByteLine(t)
                // .hdb 为 MD5 整文件哈希,当前引擎计算 SHA-256,不加载
            }
        }
    }

    /** .hsb:hash:文件大小:名称 */
    private fun parseHashLine(t: String) {
        val parts = t.split(':', limit = 3)
        if (parts.size < 3 || parts[0].length != 64) return
        if (parts[0].any { Character.digit(it, 16) < 0 }) return
        val size = parts[1].toLongOrNull() ?: 0L
        synchronized(lock) { hashSigs[parts[0].lowercase()] = Pair(size, parts[2]) }
    }

    /**
     * .ndb:优先按 ClamAV 官方冒号格式解析:
     *   名称:目标类型:偏移:HEX[:min_flevel[:max_flevel]]
     * 无法匹配时回退到历史分号格式(名称;偏移;HEX;严重度;目标)。
     */
    private fun parseByteLine(t: String) {
        parseOfficialByteLine(t)?.let { sig ->
            synchronized(lock) { byteSigs.add(sig) }
            return
        }
        parseLegacyByteLine(t)?.let { sig ->
            synchronized(lock) { byteSigs.add(sig) }
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
     * 2. 任意偏移型:单遍扫描 + 锚点分桶,复杂度 O(数据长度 × 命中桶内候选数),
     *    相比"逐签名全 buffer 扫描"在数百签名 × 数 MB 数据下减少 1~2 个数量级比较;
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
                val start = base + shift
                if (start + sig.lo.size <= data.size && matchesAt(data, start, sig)) {
                    hits.add(sig.name)
                    break
                }
                shift++
            }
        }

        for (i in data.indices) {
            val bucket = idx.byAnchor[data[i].toInt() and 0xFF]
            if (bucket.isEmpty()) continue
            for (sig in bucket) {
                val start = i - sig.anchorIdx
                if (start < 0 || start + sig.lo.size > data.size) continue
                if (matchesAt(data, start, sig)) hits.add(sig.name)
            }
        }

        for (sig in idx.loose) {
            if (contains(data, sig)) hits.add(sig.name)
        }
        return hits.toList()
    }

    fun hashCount(): Int = synchronized(lock) { hashSigs.size }

    fun byteCount(): Int = synchronized(lock) { byteSigs.size }

    /** 位置固定型签名数量(统计与展示用) */
    fun positionalCount(): Int = synchronized(lock) { byteSigs.count { it.isPositional } }

    /** 热重载:清空已加载签名并重新从 assets / files/clamav 加载 */
    fun reload(context: Context) {
        synchronized(lock) {
            hashSigs.clear()
            byteSigs.clear()
            rebuildIndex()
            loaded = false
        }
        ensureLoaded(context)
    }

    fun signatureCount(): Int = synchronized(lock) { hashSigs.size + byteSigs.size }
}
