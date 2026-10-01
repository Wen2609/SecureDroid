package com.armorlab.securedroid.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/** SHA-256 十六进制编码:特征库比对依赖该格式,必须与 ClamAV 签名(小写 hex)一致 */
class ScannerEngineTest {

    @Test
    fun encodesBytesAsLowercaseHex() {
        assertEquals("00", ScannerEngine.toHex(byteArrayOf(0)))
        assertEquals("0f", ScannerEngine.toHex(byteArrayOf(15)))
        assertEquals("ff", ScannerEngine.toHex(byteArrayOf(-1)))
        assertEquals("000fff", ScannerEngine.toHex(byteArrayOf(0, 15, -1)))
    }

    @Test
    fun encodesEmptyArrayAsEmptyString() {
        assertEquals("", ScannerEngine.toHex(ByteArray(0)))
    }

    @Test
    fun alwaysProducesTwiceTheByteCount() {
        val bytes = ByteArray(64) { (it * 7).toByte() }
        assertEquals(128, ScannerEngine.toHex(bytes).length)
    }
}
