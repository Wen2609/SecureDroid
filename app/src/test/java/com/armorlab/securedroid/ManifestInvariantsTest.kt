package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清单不变量测试。
 *
 * 这些约束一旦被破坏,问题只在真机 / 特定系统版本上才暴露(甚至直接崩溃),
 * 编译期完全无感。用测试把它们钉死:
 * 1. 前台服务类型必须与已声明的权限一致(不一致 → SecurityException 崩溃);
 * 2. 使用 specialUse 类型必须带 subtype 属性(系统要求);
 * 3. 清单中声明的组件类必须真实存在(防止删除类后清单残留);
 * 4. 通知权限必须声明(否则全部告警静默失效)。
 */
class ManifestInvariantsTest {

    private val manifest: String by lazy { manifestFile().readText() }

    private fun manifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        candidates.firstOrNull { it.isFile }?.let { return it }
        // 从工作目录向上回溯,兼容不同的测试运行目录
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/AndroidManifest.xml")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 AndroidManifest.xml,请检查单测工作目录")
    }

    private fun serviceTag(): String {
        val m = Regex("""<service\b[^>]*android:name="\.realtime\.RealtimeProtectionService"[\s\S]*?</service>""")
            .find(manifest)
        assertTrue("清单中应声明 RealtimeProtectionService", m != null)
        return m!!.value
    }

    @Test
    fun foregroundServiceTypesHaveMatchingPermissions() {
        val service = serviceTag()
        assertTrue(
            "服务应声明 foregroundServiceType",
            service.contains("android:foregroundServiceType=")
        )
        if (service.contains("specialUse")) {
            assertTrue(
                "使用 specialUse 必须声明 FOREGROUND_SERVICE_SPECIAL_USE 权限,否则启动时崩溃",
                manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            )
            assertTrue(
                "specialUse 必须携带 PROPERTY_SPECIAL_USE_FGS_SUBTYPE 属性",
                service.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
            )
        }
        if (service.contains("dataSync")) {
            assertTrue(
                "使用 dataSync 必须声明 FOREGROUND_SERVICE_DATA_SYNC 权限",
                manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
            )
        }
        assertTrue(
            "必须声明 FOREGROUND_SERVICE 基础权限",
            manifest.contains("android.permission.FOREGROUND_SERVICE\"")
        )
    }

    @Test
    fun notificationPermissionIsDeclared() {
        assertTrue(
            "缺少 POST_NOTIFICATIONS 会导致 Android 13+ 上所有安全告警静默失效",
            manifest.contains("android.permission.POST_NOTIFICATIONS")
        )
    }

    @Test
    fun declaredComponentsExistAsSourceFiles() {
        // 用转义字符串避免正则尾部的双引号与 Kotlin 原始字符串定界符冲突
        val componentRe = Regex("<(activity|service|receiver|provider)\\b[^>]*?android:name=\"([^\"]+)\"")

        val missing = mutableListOf<String>()
        for (m in componentRe.findAll(manifest)) {
            val name = m.groupValues[2]
            if (!name.startsWith(".")) continue // 系统组件或全限定名跳过
            val rel = name.removePrefix(".").replace('.', '/') + ".kt"
            val javaFile = File(manifestFile().parentFile, "java/com/armorlab/securedroid/" + rel)
            if (!javaFile.isFile) missing.add(name)
        }
        assertTrue(
            "清单声明了不存在的组件类(清单与代码漂移): " + missing.joinToString(", "),
            missing.isEmpty()
        )
    }

    @Test
    fun obsoleteAlarmReceiverIsNotReferenced() {
        // 定时查杀已迁移到 WorkManager;若清单残留 AlarmReceiver 引用则无法安装/启动
        assertTrue(
            "清单不应再引用已删除的 AlarmReceiver",
            !manifest.contains("AlarmReceiver")
        )
    }
}
