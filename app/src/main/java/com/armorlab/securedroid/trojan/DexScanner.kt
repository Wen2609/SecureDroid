package com.armorlab.securedroid.trojan

import java.util.zip.ZipFile

/**
 * 从 APK 的 classes*.dex 中提取可读字符串,供行为规则匹配。
 * 轻量实现:滑动提取连续可打印 ASCII 序列,无需解析 dex 结构。
 */
object DexScanner {

    private const val MIN_LEN = 6
    private const val MAX_ENTRY = 12L * 1024 * 1024
    private const val CAP_TOTAL = 24L * 1024 * 1024

    fun dexStringsFromApk(apkPath: String): Set<String> {
        val out = HashSet<String>()
        try {
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                var used = 0L
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!e.name.endsWith(".dex")) continue
                    if (e.size > MAX_ENTRY || used + e.size > CAP_TOTAL) continue
                    val bytes = zip.getInputStream(e).use { it.readBytes() }
                    used += bytes.size
                    extractStrings(bytes, out)
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun extractStrings(bytes: ByteArray, out: MutableSet<String>) {
        val sb = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7E) {
                sb.append(v.toChar())
            } else {
                if (sb.length >= MIN_LEN) out.add(sb.toString())
                sb.setLength(0)
            }
        }
        if (sb.length >= MIN_LEN) out.add(sb.toString())
    }
}
