package com.armorlab.securedroid.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.vscan.Quarantine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 隔离区记录解析测试:恢复/销毁动作依赖记录字符串的精确往返(raw),
 * 旧格式(3 字段,无原因)必须向后兼容 —— 记录字段错位会导致恢复/销毁
 * 操作清错记录或永远清不掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuarantineRecordTest {

    @Test
    fun parsesLegacyAndReasonRecords() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putStringSet(
                "quarantine_items",
                setOf(
                    "/data/old|/data/data/x/files/quarantine/old.123.qtn|1700000000000",
                    "/data/new|/data/data/x/files/quarantine/new.124.qtn|1700000001000|恶意模块启动脚本"
                )
            )
            .commit()

        val items = Quarantine.items(ctx)
        assertEquals(2, items.size)

        val legacy = items.first { it.originalPath == "/data/old" }
        assertEquals("", legacy.reason)
        assertEquals(1700000000000L, legacy.time)

        val withReason = items.first { it.originalPath == "/data/new" }
        assertEquals("恶意模块启动脚本", withReason.reason)
        assertEquals("/data/data/x/files/quarantine/new.124.qtn", withReason.quarantinedPath)

        // 原因里的 '|' 必须在写入侧清洗,否则解析错位
        assertEquals(4, withReason.raw.split('|', limit = 4).size)
    }
}
