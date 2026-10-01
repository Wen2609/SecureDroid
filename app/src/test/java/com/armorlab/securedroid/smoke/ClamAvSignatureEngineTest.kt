package com.armorlab.securedroid.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.trojan.ClamAvSignatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ClamAV 签名引擎端到端测试(真实加载 assets/signatures 下的特征文件)。
 *
 * 覆盖官方 .ndb 的三种偏移语义 —— 任意位置、绝对偏移、文件尾偏移 ——
 * 以及历史自定义格式的向后兼容。这些语义若解析错误,会直接导致**漏报或误报**:
 * 漏报让木马通过,误报让用户对安全工具失去信任。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClamAvSignatureEngineTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun loadSignatures() {
        ClamAvSignatures.reload(ctx)
    }

    @Test
    fun loadsBundledSignatures() {
        assertTrue("应加载哈希签名(.hsb)", ClamAvSignatures.hashCount() >= 1)
        assertTrue("应加载字节特征(.ndb)", ClamAvSignatures.byteCount() >= 4)
        assertTrue("应识别位置固定型签名(绝对偏移 + 文件尾)", ClamAvSignatures.positionalCount() >= 2)
    }

    @Test
    fun detectsAnyOffsetSignature() {
        val data = "prefix".toByteArray() + "APK-TROJAN-TEST-SIGNATURE".toByteArray() + "suffix".toByteArray()
        assertTrue(
            "任意偏移签名应在数据中段命中",
            ClamAvSignatures.scanBytes(data).contains("Test.Trojan.Marker")
        )
    }

    @Test
    fun absoluteOffsetSignatureMatchesOnlyAtThatOffset() {
        val dexMagic = byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35)
        val atZero = dexMagic + ByteArray(64)
        assertTrue(
            "绝对偏移签名应在偏移 0 处命中",
            ClamAvSignatures.scanBytes(atZero).contains("Test.Trojan.DexHeader")
        )
        val shifted = ByteArray(8) + dexMagic + ByteArray(64)
        assertFalse(
            "同样的字节出现在偏移 8 时,绝对偏移签名不应命中",
            ClamAvSignatures.scanBytes(shifted).contains("Test.Trojan.DexHeader")
        )
    }

    @Test
    fun endOfFileSignatureMatchesOnlyAtTail() {
        val atTail = ByteArray(64) + "EVIL".toByteArray()
        assertTrue(
            "文件尾偏移签名应在末尾 4 字节命中",
            ClamAvSignatures.scanBytes(atTail).contains("Test.Trojan.TailMarker")
        )
        val atHead = "EVIL".toByteArray() + ByteArray(64)
        assertFalse(
            "同样字节出现在开头时,EOF 签名不应命中",
            ClamAvSignatures.scanBytes(atHead).contains("Test.Trojan.TailMarker")
        )
    }

    @Test
    fun legacySemicolonFormatStillSupported() {
        val data = "xxAPK-LEGACY-TESTxx".toByteArray()
        assertTrue(
            "历史分号格式特征应继续可用(向后兼容)",
            ClamAvSignatures.scanBytes(data).contains("Test.Trojan.LegacyMarker")
        )
    }

    @Test
    fun cleanBufferProducesNoHits() {
        val clean = ByteArray(4096) { (it * 7).toByte() }
        assertTrue("无特征数据不应产生命中", ClamAvSignatures.scanBytes(clean).isEmpty())
    }

    @Test
    fun hashSignatureMatchesByExactSha256() {
        // 演示库中登记的是空文件 SHA-256(e3b0c442…),大小为 0 表示忽略长度
        val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val hit = ClamAvSignatures.matchHash(emptySha, 0L)
        assertTrue("整文件哈希签名应按 SHA-256 命中", hit != null)
        assertTrue("命中名称应来自特征库", hit!!.first.contains("Trojan.Test.EmptySha256"))
    }
}
