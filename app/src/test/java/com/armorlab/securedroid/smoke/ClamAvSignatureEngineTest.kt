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
        assertTrue("应加载 MD5 整文件哈希签名(.hdb)", ClamAvSignatures.md5Count() >= 1)
        assertTrue("应加载字节特征(.ndb)", ClamAvSignatures.byteCount() >= 4)
        assertTrue("应加载逻辑签名(.ldb)", ClamAvSignatures.logicalCount() >= 1)
        assertTrue("应识别位置固定型签名(绝对偏移 + 文件尾)", ClamAvSignatures.positionalCount() >= 2)
    }

    @Test
    fun logicalSignatureRequiresAllSubsignatures() {
        // 演示 .ldb:0&1 —— 两个子签名(hex 414141414141 / 424242424242)必须同时出现才判定
        fun hexBytes(hex: String): ByteArray =
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val subA = hexBytes("41".repeat(6))
        val subB = hexBytes("42".repeat(6))

        assertTrue("双子签名齐备应命中逻辑签名", ClamAvSignatures.scanBytes(subA + subB).contains("Test.Trojan.Logical"))

        assertFalse("缺任一子签名不应命中", ClamAvSignatures.scanBytes(subA).contains("Test.Trojan.Logical"))
        // 顺序无关:后一个子签名在前也应命中(两处均为任意位置匹配)
        assertTrue("子签名顺序无关", ClamAvSignatures.scanBytes(subB + subA).contains("Test.Trojan.Logical"))
        // 隐藏子签名不得作为独立命中上报
        val hitsA = ClamAvSignatures.scanBytes(subA)
        assertTrue(
            "隐藏子签名不应出现在命中列表",
            hitsA.none { it.startsWith("\u0000") } && !hitsA.contains("Test.Trojan.Logical")
        )
    }

    @Test
    fun unsupportedLogicalSyntaxIsRejectedNotApproximated() {
        // 计数/PCRE 等不支持的语义必须整行拒绝(近似实现会误报)
        ClamAvSignatures.importText(
            "bad.ldb",
            listOf(
                "Test.Bad.Count;Target:0;(0>5,2)&1;4141;4242;4343",
                "Test.Bad.Pcre;Target:0;0&1;4141;/4444/i"
            ).joinToString("\n")
        )
        val data = "AA".repeat(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray() +
            "BB".repeat(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray() +
            "CC".repeat(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertTrue("被拒绝的语义不得产生任何命中", ClamAvSignatures.scanBytes(data).isEmpty())
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
        // 自检用唯一标记 "APK-HEADER-MARKER"。
        // 注意:绝不能拿 dex\n035 这类通用魔数当特征 —— 实测那会把所有同版本 dex 的
        // 正常应用(含一加厂商"备份与恢复")整片判成木马。
        val marker = "APK-HEADER-MARKER".toByteArray()
        val atZero = marker + ByteArray(64)
        assertTrue(
            "绝对偏移签名应在偏移 0 处命中",
            ClamAvSignatures.scanBytes(atZero).contains("Test.Trojan.HeaderMarker")
        )
        val shifted = ByteArray(8) + marker + ByteArray(64)
        assertFalse(
            "同样的字节出现在偏移 8 时,绝对偏移签名不应命中",
            ClamAvSignatures.scanBytes(shifted).contains("Test.Trojan.HeaderMarker")
        )
    }

    @Test
    fun genericFileMagicIsNeverASignature() {
        // 回归(误报修复 v1.7.3):任何通用文件魔数都不允许出现在内置特征库里。
        // 一旦命中判定建立在"这是 dex / zip / elf"这种无差别事实上,误报面就是全部正常应用。
        val magics = mapOf(
            "dex\n035" to byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35),
            "dex\n038" to byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x38),
            "zip" to byteArrayOf(0x50, 0x4B, 0x03, 0x04),
            "elf" to byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
        )
        for ((label, magic) in magics) {
            val data = magic + ByteArray(64)
            assertTrue(
                "通用魔数(" + label + ")不得命中任何内置特征",
                ClamAvSignatures.scanBytes(data).isEmpty()
            )
        }
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

    @Test
    fun md5SignatureMatchesByExactMd5() {
        // 演示库中登记的是空文件 MD5(d41d8cd9…),大小为 0 表示忽略长度
        val emptyMd5 = "d41d8cd98f00b204e9800998ecf8427e"
        val hit = ClamAvSignatures.matchMd5(emptyMd5, 0L)
        assertTrue("整文件 MD5 哈希签名应按 MD5 命中", hit != null)
        assertTrue("命中名称应来自特征库", hit!!.first.contains("Trojan.Test.EmptyMd5"))
    }

    @Test
    fun md5SignatureIgnoresWrongHashOrSize() {
        assertTrue("错误 MD5 不应命中", ClamAvSignatures.matchMd5("00000000000000000000000000000000", 0L) == null)
        // 演示库登记大小为 0(忽略大小),但真实库会带大小;此处直接注入一条带大小的特征验证
        ClamAvSignatures.importText(
            "test.hdb",
            "0123456789abcdef0123456789abcdef:1234:Test.Md5.Sized"
        )
        assertTrue("同大小应命中", ClamAvSignatures.matchMd5("0123456789abcdef0123456789abcdef", 1234L) != null)
        assertTrue("大小不符不应命中", ClamAvSignatures.matchMd5("0123456789abcdef0123456789abcdef", 1235L) == null)
    }

    @Test
    fun oversizedLibraryIsTruncatedNotOom() {
        // 百万级官方库在低内存设备会 OOM:条数超预算必须截断并亮出标记,而不是无限吃内存
        val saved = ClamAvSignatures.entryBudget
        ClamAvSignatures.entryBudget = 4
        try {
            ClamAvSignatures.unload()
            val many = (1..50).joinToString("\n") { i ->
                String.format("%064x:0:Test.Hash.Budget%d", i.toLong(), i)
            }
            ClamAvSignatures.importText("budget.hsb", many)
            assertTrue("超预算应置截断标记", ClamAvSignatures.isTruncated())
            assertTrue(
                "载入条数应停在预算内而不是全量 50 条",
                ClamAvSignatures.hashCount() <= 4
            )
        } finally {
            ClamAvSignatures.entryBudget = saved
            ClamAvSignatures.unload()
        }
    }
}
