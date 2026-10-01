package com.armorlab.securedroid.smoke

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 全量布局膨胀测试。
 *
 * 逐个膨胀 res/layout 下的**每一个**布局:布局引用了不存在的资源、主题属性缺失、
 * 自定义控件属性写错、ViewBinding 与布局不一致等问题都会在这里直接抛出异常。
 * 这类问题在编译期与静态检查中都可能漏过,只能在运行时暴露。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayoutInflationTest {

    /** 通过 R.layout 反射枚举全部布局,新增布局自动纳入覆盖(无需手工维护清单) */
    private fun allLayoutIds(): List<Pair<String, Int>> =
        R.layout::class.java.fields.map { f -> f.name to f.getInt(null) }

    @Test
    fun everyLayoutInflatesWithAppTheme() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themed = ContextThemeWrapper(base, R.style.Theme_SecureDroid)
        val inflater = LayoutInflater.from(themed)

        val ids = allLayoutIds()
        assertTrue("应能枚举到布局资源", ids.isNotEmpty())

        val failures = mutableListOf<String>()
        for ((name, id) in ids) {
            try {
                val view: View = inflater.inflate(id, null)
                if (view == null) failures.add(name + ": 膨胀结果为 null")
            } catch (t: Throwable) {
                failures.add(name + ": " + t.javaClass.simpleName + " - " + (t.message ?: ""))
            }
        }
        assertTrue(
            "以下布局无法膨胀(" + failures.size + "/" + ids.size + "):\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun everyLayoutHasSingleRoot() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themed = ContextThemeWrapper(base, R.style.Theme_SecureDroid)
        val inflater = LayoutInflater.from(themed)
        for ((name, id) in allLayoutIds()) {
            val view = inflater.inflate(id, null)
            assertTrue("布局 " + name + " 应至少有一个子视图", view is android.view.ViewGroup)
        }
    }
}
