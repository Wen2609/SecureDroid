package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计规则不变量测试(可自动化的那部分"设计验收")。
 *
 * 环境里没有模拟器/真机,视觉验收做不了 —— 但设计系统的**硬性规则**大多可以静态校验:
 * 触摸目标、令牌化(不许写死颜色/字号)、布局必须由生成器产出。这些规则一旦被破坏,
 * 真机上往往只是"看起来有点怪",很难被发现,所以在这里变成会失败的断言。
 */
class DesignRuleTest {

    private val resDir: File by lazy { locateResDir() }

    private fun locateResDir(): File {
        listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }?.let { return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 res 目录")
    }

    private fun layouts(): List<File> = File(resDir, "layout").listFiles { f -> f.extension == "xml" }?.toList() ?: emptyList()

    private fun dimens(): Map<String, String> {
        val t = File(resDir, "values/dimens.xml").readText()
        return Regex("<dimen\\s+name=\"([^\"]+)\"\\s*>([^<]+)</dimen>")
            .findAll(t).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    private fun resolvedHeight(raw: String): Float? {
        val v = raw.trim()
        if (v.endsWith("dp")) return v.removeSuffix("dp").toFloatOrNull()
        if (v.startsWith("@dimen/")) return resolvedHeight(dimens()[v.removePrefix("@dimen/")] ?: return null)
        return null
    }

    /** 布局必须由 design/generate_layouts.mjs 生成:手改会在下次生成时被覆盖,这里提前拦住 */
    @Test
    fun everyLayoutIsGenerated() {
        val missing = layouts().filter { !it.readText().contains("design/generate_layouts.mjs") }.map { it.name }
        assertEquals("以下布局不是由设计令牌生成器产出的(手写会与设计系统漂移):$missing", emptyList<String>(), missing)
    }

    /** 可点击控件必须有 ≥48dp 的触摸目标(UI/UX Pro Max 规则 2,Android 平台下限) */
    @Test
    fun clickableControlsMeetTouchTarget() {
        val clickable = listOf("com.google.android.material.button.MaterialButton", "Button", "ImageButton",
            "com.google.android.material.materialswitch.MaterialSwitch", "Switch", "androidx.appcompat.widget.SwitchCompat")
        val violations = mutableListOf<String>()
        for (f in layouts()) {
            val blocks = f.readText().split("<")
            for (b in blocks) {
                val tag = b.substringBefore(" ").substringBefore("\n").substringBefore(">").substringBefore("/")
                if (tag !in clickable) continue
                val heightAttr = Regex("android:layout_height=\"([^\"]+)\"").find(b)?.groupValues?.get(1)
                val minAttr = Regex("android:minHeight=\"([^\"]+)\"").find(b)?.groupValues?.get(1)
                val h = heightAttr?.let { resolvedHeight(it) }
                val m = minAttr?.let { resolvedHeight(it) }
                val ok = (h != null && h >= 48f) || (m != null && m >= 48f) || heightAttr == "match_parent" || heightAttr == "wrap_content" && m != null
                if (!ok) violations.add(f.name + ": " + tag + " height=" + heightAttr + " minHeight=" + minAttr)
            }
        }
        assertEquals("以下可点击控件的触摸目标可能小于 48dp:$violations", emptyList<String>(), violations)
    }

    /** 颜色与字号只能来自令牌:布局里不允许出现字面色值或字面字号 */
    @Test
    fun layoutsUseTokensOnly() {
        val violations = mutableListOf<String>()
        for (f in layouts()) {
            // widget_security.xml 走 RemoteViews:它不参与主题解析,只能用字面尺寸
            if (f.name == "widget_security.xml") continue
            val t = f.readText()
            Regex("#[0-9A-Fa-f]{6,8}").findAll(t).forEach { violations.add(f.name + " 字面色值 " + it.value) }
            Regex("android:textSize=\"[0-9]").findAll(t).forEach { violations.add(f.name + " 字面字号 " + it.value) }
        }
        assertEquals("布局中出现了未令牌化的值:$violations", emptyList<String>(), violations)
    }

    /** 令牌本身必须齐全:设计系统的核心角色缺一个,整套界面就会退回默认值 */
    @Test
    fun coreTokensExistInBothThemes() {
        val required = listOf("c_primary", "c_on_primary", "c_accent", "c_on_accent", "c_background",
            "c_foreground", "c_card", "c_muted", "c_muted_foreground", "c_border", "c_destructive",
            "c_success", "c_gold")
        for (file in listOf("values/colors.xml", "values-night/colors.xml")) {
            val t = File(resDir, file).readText()
            val missing = required.filter { !t.contains("name=\"$it\"") }
            assertEquals("$file 缺少核心令牌:$missing", emptyList<String>(), missing)
        }
        val dims = dimens()
        listOf("sd_gutter", "sd_touch_min", "sd_row_height", "sd_radius_card", "sd_radius_inner").forEach {
            assertTrue("values/dimens.xml 缺少 @" + it, dims.containsKey(it))
        }
    }
}
