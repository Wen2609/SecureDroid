package com.armorlab.securedroid.scan

enum class ThreatLevel { LOW, MEDIUM, HIGH, CRITICAL }

data class ThreatInfo(
    val name: String,
    val level: ThreatLevel,
    val description: String
)

/**
 * 本地病毒特征库:内置少量演示特征用于自检。
 * 真实特征经「特征库在线更新」(URL + SHA-256)加载到 ClamAvSignatures,见 vscan/FeatureUpdater。
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

    fun size(): Int = synchronized(lock) { signatures.size }
}
