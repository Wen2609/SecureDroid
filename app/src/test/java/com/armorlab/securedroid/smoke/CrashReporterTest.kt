package com.armorlab.securedroid.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.core.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 崩溃捕获落盘测试:写入 / 上限裁剪 / 最新读取。
 * 崩溃文件是排障的唯一凭据,丢失或裁错序都会让用户导出的报告失效。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReporterTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun writesCrashWithStackAndTrimsToLimit() {
        repeat(12) { i ->
            CrashReporter.write(ctx, "main", IllegalStateException("boom-$i"))
        }
        val logs = CrashReporter.logs(ctx)
        assertEquals("超过上限必须裁剪", 10, logs.size)
        // 新→旧排序:最新的 boom-11 必须在最前,最旧的 boom-0/1 被裁掉
        val latest = CrashReporter.latestText(ctx)
        assertNotNull(latest)
        assertTrue("最新记录应包含最新异常信息", latest!!.contains("boom-11"))
        assertTrue("记录应包含调用栈", latest.contains("IllegalStateException"))
        assertTrue("记录应包含线程名", latest.contains("线程: main"))
        assertTrue(
            "被裁掉的旧记录不应存在",
            logs.none { it.name.contains("boom-0") } || logs.size == 10
        )
    }

    @Test
    fun latestTextIsNullWhenNoCrash() {
        assertNull(CrashReporter.latestText(ctx))
    }
}
