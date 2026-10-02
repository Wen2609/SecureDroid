package com.armorlab.securedroid.scan

import android.content.Context
import java.io.File

enum class ThreatLevel { LOW, MEDIUM, HIGH, CRITICAL }

data class ThreatInfo(
    val name: String,
    val level: ThreatLevel,
    val description: String
)

/**
 * 本地病毒特征库。
 *
 * 内置少量演示特征用于自检;真实部署时通过受签名保护的私有通道下发特征更新,
 * 更新文件写入应用私有目录 signatures.txt,格式:hash|名称|级别(0-3)|描述
 */
object SignatureDatabase {

    private val lock = Any()
    private val signatures = LinkedHashMap<String, ThreatInfo>()

    init {
        // 演示特征:全零哈希(仅用于验证扫描链路是否工作)
        signatures["0000000000000000000000000000000000000000000000000000000000000000"] =
            ThreatInfo(
                "TestStub.Demo",
                ThreatLevel.HIGH,
                "演示特征:全零 SHA-256(用于自检扫描链路)"
            )
    }

    fun lookup(sha256: String): ThreatInfo? =
        synchronized(lock) { signatures[sha256.lowercase()] }

    fun add(hash: String, info: ThreatInfo) {
        synchronized(lock) { signatures[hash.lowercase()] = info }
    }

    fun size(): Int = synchronized(lock) { signatures.size }

    private var loadedPath: String? = null
    private var loadedLength = -1L
    private var loadedMtime = -1L

    /**
     * 载入本地特征库更新(files/signatures.txt)。
     *
     * 性能:以 (路径, 大小, mtime) 记忆化。扫描链路里每个应用都会调用一次本方法
     * (ScannerEngine.scanAll / TrojanScanner 前置),原实现每次都重读并重新解析整份文件,
     * 特征库越大越亏;现在文件未变化时直接返回。
     */
    fun loadLocalUpdate(context: Context) {
        val file = File(context.filesDir, "signatures.txt")
        if (!file.exists()) return
        val length = file.length()
        val mtime = file.lastModified()
        synchronized(lock) {
            if (file.path == loadedPath && length == loadedLength && mtime == loadedMtime) return
        }
        val parsed = ArrayList<Pair<String, ThreatInfo>>()
        try {
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split('|')
                if (parts.size >= 2 && parts[0].length == 64) {
                    val levelIdx = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 3) ?: 3
                    parsed.add(
                        Pair(
                            parts[0],
                            ThreatInfo(
                                parts[1],
                                ThreatLevel.entries[levelIdx],
                                parts.getOrNull(3) ?: ""
                            )
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return
        }
        synchronized(lock) {
            for ((hash, info) in parsed) signatures[hash.lowercase()] = info
            loadedPath = file.path
            loadedLength = length
            loadedMtime = mtime
        }
    }
}
