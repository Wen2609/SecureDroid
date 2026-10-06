package com.armorlab.securedroid.smoke

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.lock.AppLockStore
import com.armorlab.securedroid.realtime.BootReceiver
import com.armorlab.securedroid.root.PrivLevel
import com.armorlab.securedroid.root.PrivilegeManager
import com.armorlab.securedroid.root.SystemIntegrity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    /**
     * Robolectric 每个用例重建运行环境,而上一个用例留下的 Room 单例仍指向旧环境的
     * SQLite 连接(报 "Illegal connection pointer")。这里每个用例都从干净实例开始。
     */
    @Before
    fun freshDatabase() {
        AppDatabase.resetForTest()
    }

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
        // 测试环境无 su:绝不能判为 ROOT。LOCAL_SHELL 是合法结果 —— 探测的是
        // 本地 sh 管道,在装有 Git Bash / WSL 的宿主上真实可用;断言环境无关的不变量。
        val probed = PrivilegeManager.probe(ctx)
        assertTrue(
            "无 su 环境不得探测为 ROOT,实际 " + probed,
            probed != PrivLevel.ROOT
        )
        assertEquals("probe 结果必须落库缓存", probed, PrivilegeManager.level(ctx))

        val denied = PrivilegeManager.exec(ctx, "id", requireRoot = true)
        assertFalse("非 ROOT 层级提权执行必须失败", denied.ok)
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

    @Test
    fun dashboardAggregateQueriesMatchTableContent() = runBlocking {
        val dao = AppDatabase.get(ctx).scanRecordDao()
        dao.clear()
        // 空表:计数为 0、最近扫描时间为 null(桥层转成 0)
        assertEquals(0, dao.countAll())
        assertEquals(null, dao.lastScannedAt())

        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(
                ScanRecordEntity(
                    packageName = "com.a", appName = "A", sha256 = "aa",
                    threatName = null, riskScore = 0, scannedAt = now - 1000
                ),
                ScanRecordEntity(
                    packageName = "com.b", appName = "B", sha256 = "bb",
                    threatName = "X", riskScore = 50, scannedAt = now
                )
            )
        )
        assertEquals(2, dao.countAll())
        assertEquals("lastScannedAt 必须等于最大 scannedAt", now, dao.lastScannedAt())
        dao.clear()
    }

    @Test
    fun appLockStoreDegradesSafelyAndRepeatedly() {
        // 测试环境 AndroidKeyStore 通常不可用:必须安全降级(不崩溃、不落明文),
        // 且连续调用走失败冷却路径依旧安全 —— 这同时覆盖了单例缓存的降级分支
        repeat(3) {
            assertFalse(AppLockStore.hasPin(ctx))
            assertFalse(AppLockStore.isLocked(ctx, "com.example.any"))
            assertEquals(emptySet<String>(), AppLockStore.lockedApps(ctx))
        }
    }
}
