package com.armorlab.securedroid.smoke

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.realtime.BootReceiver
import com.armorlab.securedroid.root.PrivLevel
import com.armorlab.securedroid.root.PrivilegeManager
import com.armorlab.securedroid.root.SystemIntegrity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room 数据库与关键管理器冒烟测试。
 *
 * 覆盖:数据库可建表并读写、提权层在无 root 环境下的降级行为、
 * 完整性模块在无 root 时给出明确提示、开机广播在开关关闭时安全无操作。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseAndManagersTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun databaseOpensAndRoundTripsRecords() = runBlocking {
        val dao = AppDatabase.get(ctx).scanRecordDao()
        dao.clear()
        dao.insertAll(
            listOf(
                ScanRecordEntity(
                    packageName = "com.example.bad",
                    appName = "Bad App",
                    sha256 = "deadbeef",
                    threatName = "Test.Trojan",
                    riskScore = 90,
                    scannedAt = System.currentTimeMillis()
                )
            )
        )
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("com.example.bad", all.first().packageName)
        assertEquals(1, dao.threatCount())
        dao.clear()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun privilegeLayerDegradesSafelyWithoutRoot() {
        // 测试环境无 su:层级必须落在 NONE,而不是崩溃或谎报 ROOT
        assertEquals(PrivLevel.NONE, PrivilegeManager.probe(ctx))
        assertFalse(PrivilegeManager.isRoot(ctx))

        val denied = PrivilegeManager.exec(ctx, "id", requireRoot = true)
        assertFalse("无 root 时提权执行必须失败", denied.ok)
        assertTrue("必须给出权限不足原因", denied.denied)
    }

    @Test
    fun integrityModuleReportsMissingRootInsteadOfCrashing() {
        val items = SystemIntegrity.scan(ctx)
        assertTrue("无 root 时应返回明确提示项", items.isNotEmpty())
        assertTrue(
            "提示项应说明需要最高权限",
            items.first().title.contains("NeedRoot") || items.first().detail.contains("最高权限")
        )
    }

    @Test
    fun bootReceiverIsSafeWhenRealtimeDisabled() {
        // 实时防护默认关闭:开机广播必须安全无操作(否则每次开机都崩)
        BootReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))
    }
}
