package com.armorlab.securedroid.trojan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import java.io.BufferedReader
import java.io.File
import java.util.zip.ZipFile

/**
 * ClamAV 签名格式兼容层。
 *
 * 仅兼容 ClamAV 的签名【文件格式】(.hsb 整文件 SHA-256 / .ndb 十六进制字节特征),
 * 不包含也不复用 ClamAV 的任何代码(ClamAV 为 GPL,格式互操作不受影响)。
 *
 * 特征来源:
 * 1. assets/signatures/ 内置演示签名;
 * 2. 应用私有目录 files/clamav/ 下的外部特征文件(如从 ClamAV daily.cvd 解包出的
 *    daily.hsb / daily.ndb),可用 adb push 放入,支持热加载。
 *
 * .hsb 行格式:  hash(64位hex):文件大小:名称          (大小为 0 表示忽略)
 * .ndb 行格式:  名称;偏移;HEX特征;严重度;目标类型     (HEX 中 ? 为半字节通配)
 */
object ClamAvSignatures {

    class ByteSig(val name: String, val lo: ByteArray, val hi: ByteArray)

    private val lock = Any()
    private val hashSigs = HashMap<String, Pair<Long, String>>()
    private val byteSigs = mutableListOf<ByteSig>()
    private var loaded = false

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
            loaded = true
        }
    }

    private fun parseFileLines(reader: BufferedReader, fileName: String) {
        val lower = fileName.lowercase()
        reader.forEachLine { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEachLine
            when {
                lower.endsWith(".hsb") -> parseHashLine(t)
                lower.endsWith(".ndb") -> parseByteLine(t)
                // .hdb 为 MD5 整文件哈希,当前引擎计算 SHA-256,暂不加载
            }
        }
    }

    private fun parseHashLine(t: String) {
        val parts = t.split(':', limit = 3)
        if (parts.size < 3 || parts[0].length != 64) return
        val size = parts[1].toLongOrNull() ?: 0L
        synchronized(lock) { hashSigs[parts[0].lowercase()] = Pair(size, parts[2]) }
    }

    private fun parseByteLine(t: String) {
        val parts = t.split(';')
        if (parts.size < 3) return
        val nib = parseHex(parts[2]) ?: return
        synchronized(lock) { byteSigs.add(ByteSig(parts[0], nib.first, nib.second)) }
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
        val sigs = synchronized(lock) { byteSigs.toList() }
        if (sigs.isEmpty()) return hits
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
                    for (sig in sigs) {
                        if (contains(data, sig)) {
                            hits.add(
                                TrojanScanner.Detection(
                                    "ClamAV 字节码", sig.name, ThreatLevel.HIGH,
                                    "在 " + n + " 中命中字节特征"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return hits
    }

    /** 半字节级通配匹配 */
    private fun contains(data: ByteArray, sig: ByteSig): Boolean {
        val n = sig.lo.size
        if (data.size < n) return false
        outer@ for (i in 0..data.size - n) {
            for (j in 0 until n) {
                val v = data[i + j].toInt() and 0xFF
                if (sig.lo[j].toInt() >= 0 && (v ushr 4) != sig.lo[j].toInt()) continue@outer
                if (sig.hi[j].toInt() >= 0 && (v and 0x0F) != sig.hi[j].toInt()) continue@outer
            }
            return true
        }
        return false
    }

    /** 对任意字节流做字节码特征匹配(内存 / 分区扫描用) */
    fun scanBytes(data: ByteArray): List<String> {
        val sigs = synchronized(lock) { byteSigs.toList() }
        val hits = mutableListOf<String>()
        for (sig in sigs) {
            if (contains(data, sig)) hits.add(sig.name)
        }
        return hits
    }

    fun signatureCount(): Int = synchronized(lock) { hashSigs.size + byteSigs.size }
}
