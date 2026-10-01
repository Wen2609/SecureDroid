package com.armorlab.securedroid.smoke

import android.app.Activity
import com.armorlab.securedroid.lock.LockActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 全部 Activity 拉起测试。
 *
 * 从 AndroidManifest 自动枚举 activity 并逐个走完 create → start → resume。
 * 任何 onCreate 中的空指针、绑定失败、主题属性缺失、权限调用错误都会在此暴露 ——
 * 这正是用户点开某个页面就闪退的那类问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityLaunchTest {

    private fun manifestText(): String {
        val candidates = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
        candidates.firstOrNull { it.isFile }?.let { return it.readText() }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/AndroidManifest.xml")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 AndroidManifest.xml")
    }

    @Suppress("UNCHECKED_CAST")
    private fun manifestActivities(): List<Class<out Activity>> {
        val pkg = "com.armorlab.securedroid"
        return Regex("<activity\\b[^>]*?android:name=\"([^\"]+)\"")
            .findAll(manifestText())
            .map { it.groupValues[1] }
            .filter { it.startsWith(".") }
            .map { Class.forName(pkg + it).asSubclass(Activity::class.java) }
            .toList()
    }

    @Test
    fun everyActivityLaunches() {
        val activities = manifestActivities()
        assertTrue("应从清单枚举到 Activity", activities.isNotEmpty())

        val failures = mutableListOf<String>()
        for (cls in activities) {
            // LockActivity 在未设置 PIN 时会主动 finish(),单独用例覆盖
            if (cls == LockActivity::class.java) continue
            try {
                Robolectric.buildActivity(cls).setup().get()
            } catch (t: Throwable) {
                failures.add(cls.simpleName + ": " + t.javaClass.simpleName + " - " + (t.message ?: ""))
            }
        }
        assertTrue(
            "以下页面无法拉起(" + failures.size + "):\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun lockActivityFinishesImmediatelyWithoutPin() {
        val controller = Robolectric.buildActivity(LockActivity::class.java).setup()
        assertTrue("未设置 PIN 时锁屏页必须自动结束,避免把用户锁死", controller.get().isFinishing)
    }
}
