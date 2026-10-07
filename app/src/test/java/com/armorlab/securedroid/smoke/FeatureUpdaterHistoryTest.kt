package com.armorlab.securedroid.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.vscan.FeatureUpdater
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 特征库更新落盘链路测试:备份 → 写入 → 热重载 → 历史 → 回滚。
 * 不走网络;applyUpdate 与 update() 共用同一段落盘代码,校验通过即视为真实更新。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeatureUpdaterHistoryTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun applyUpdateWritesFileRecordsHistoryAndBacksUp() {
        ClamAvSignatures.unload()
        val dir = File(ctx.filesDir, "clamav").apply { mkdirs() }
        File(dir, "test.hsb").writeText(
            "# old\n0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef:0:Old.Sig\n"
        )
        val newHex = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        val newBytes = "# new\n$newHex:0:New.Sig\n".toByteArray()

        val result = FeatureUpdater.applyUpdate(ctx, "test.hsb", newBytes)
        assertTrue("更新应成功: " + result.message, result.ok)
        assertTrue(
            "正式文件应被替换",
            String(File(dir, "test.hsb").readBytes()).contains("New.Sig")
        )
        assertTrue(
            "旧文件应进入 backup/",
            File(dir, "backup/test.hsb").readText().contains("Old.Sig")
        )
        assertTrue("历史应记录本次文件", FeatureUpdater.historyJson(ctx).contains("test.hsb"))
        assertTrue(
            "热重载后新特征应可查询",
            ClamAvSignatures.matchHash(newHex, 0L) != null
        )
    }

    @Test
    fun rollbackRestoresPreviousFileAndReloads() {
        ClamAvSignatures.unload()
        val dir = File(ctx.filesDir, "clamav").apply { mkdirs() }
        File(dir, "test.ndb").writeText("Old.Marker:0:*:4141414141414141\n")
        val newBytes = "New.Marker:0:*:4242424242424242\n".toByteArray()

        assertTrue(FeatureUpdater.applyUpdate(ctx, "test.ndb", newBytes).ok)
        val message = FeatureUpdater.rollbackUpdate(ctx)
        assertTrue("回滚结果应包含还原文件名", message.contains("test.ndb"))
        assertTrue(
            "旧特征文件应被还原",
            String(File(dir, "test.ndb").readBytes()).contains("Old.Marker")
        )
        val hits = ClamAvSignatures.scanBytes(ByteArray(8) { 0x41 })
        assertTrue("回滚后热重载应恢复旧特征命中", hits.contains("Old.Marker"))
        assertTrue("回滚事件应写入历史", FeatureUpdater.historyJson(ctx).contains("rollback"))
    }
}
