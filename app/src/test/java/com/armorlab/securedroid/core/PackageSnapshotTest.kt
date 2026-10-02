package com.armorlab.securedroid.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PackageManager 快照层测试(真实 Context)。
 *
 * 优化前每次全盘扫描会遇到大量"列表 + 逐包 getPackageInfo + loadLabel"的绑定器往返,
 * 这里验证快照层三件事:同一 flags 复用同一份列表对象、标签进程内记忆化、
 * 非法 UID / 缺失包名安全降级。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageSnapshotTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun installedPackagesAreReusedWithinTtl() {
        PackageSnapshot.invalidate()
        val a = PackageSnapshot.installedPackages(ctx)
        val b = PackageSnapshot.installedPackages(ctx)
        assertTrue("Robolectric 环境应至少有一个已安装包", a.isNotEmpty())
        assertSame("TTL 内必须复用同一份快照,不能重复跨进程查询", a, b)
        assertTrue(PackageSnapshot.stats(), PackageSnapshot.stats().contains("size=1"))
        assertEquals(PackageSnapshot.packageNames(ctx).size, a.size)
    }

    @Test
    fun packageInfoIsCachedAndMissingPackageReturnsNull() {
        PackageSnapshot.invalidate()
        val all = PackageSnapshot.installedPackages(ctx)
        val pkg = all.first().packageName
        val first = PackageSnapshot.packageInfo(ctx, pkg, 0)
        assertNotNull(first)
        assertSame(first, PackageSnapshot.packageInfo(ctx, pkg, 0))
        assertEquals(null, PackageSnapshot.packageInfo(ctx, "com.armorlab.definitely.missing", 0))
    }

    @Test
    fun labelIsMemoizedAndFallsBackToPackageName() {
        PackageSnapshot.invalidate()
        val info = PackageSnapshot.installedPackages(ctx).firstNotNullOfOrNull { it.applicationInfo }
        assertNotNull(info)
        val label = PackageSnapshot.label(ctx, info)
        assertTrue(label.isNotBlank())
        assertEquals(label, PackageSnapshot.label(ctx, info))
        assertEquals("空 ApplicationInfo 应返回空串", "", PackageSnapshot.label(ctx, null))
        assertEquals(
            "查不到的包应回退包名",
            "com.armorlab.definitely.missing",
            PackageSnapshot.labelFor(ctx, "com.armorlab.definitely.missing")
        )
    }

    @Test
    fun systemUidIsNotQueried() {
        assertTrue(PackageSnapshot.packagesForUid(ctx, 0).isEmpty())
        assertTrue(PackageSnapshot.packagesForUid(ctx, 1).isEmpty())
    }

    @Test
    fun invalidateDropsCachedSnapshot() {
        PackageSnapshot.installedPackages(ctx)
        PackageSnapshot.invalidate()
        assertTrue(PackageSnapshot.stats(), PackageSnapshot.stats().contains("size=0"))
    }
}
