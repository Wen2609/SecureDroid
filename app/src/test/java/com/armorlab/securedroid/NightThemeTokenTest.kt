package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 深色主题令牌不变量测试。
 *
 * 背景:本项目真实踩过一次 —— 深色令牌被写进了 res/values/night/colors.xml。
 * AAPT **不会报错**(该目录被静默忽略),构建、Lint、全部单元测试都是绿的,
 * 但深色模式下界面会悄悄退回浅色取值。这类"资源目录放错位置"的缺陷,
 * 只能靠对文件结构本身的断言拦住,因此单独成测试。
 */
class NightThemeTokenTest {

    private val resDir: File by lazy { locateResDir() }

    private fun locateResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        candidates.firstOrNull { it.isDirectory }?.let { return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 res 目录")
    }

    private fun colors(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val re = Regex("<color\\s+name=\"([^\"]+)\"\\s*>([^<]+)</color>")
        return re.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    /** values/ 下只允许放 XML 文件:任何子目录(如误建的 values/night/)都会被 AAPT 静默忽略 */
    @Test
    fun valuesDirectoryHasNoSubDirectories() {
        val stray = File(resDir, "values").listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()
        assertEquals(
            "res/values 下不应出现子目录(限定符目录必须与 values 平级,如 res/values-night);发现:$stray",
            emptyList<String>(),
            stray
        )
    }

    /** 深色令牌必须覆盖全部关键色,否则深色模式下会退回浅色值 */
    @Test
    fun nightColorsOverrideCoreTokens() {
        val night = colors(File(resDir, "values-night/colors.xml"))
        assertTrue("应存在 res/values-night/colors.xml", night.isNotEmpty())
        // 语义令牌集:旧别名已在"清理未使用资源"时删除,这里校验当前核心集
        val required = listOf(
            "c_background", "c_foreground", "c_card", "c_muted", "c_muted_foreground",
            "c_border", "c_primary", "c_on_primary", "c_accent", "c_on_accent",
            "c_destructive", "c_success", "c_gold"
        )
        val missing = required.filter { it !in night }
        assertEquals("深色令牌缺少:$missing", emptyList<String>(), missing)
    }

    /** 防止把浅色值复制到深色文件里("改了但没生效"的另一种形态) */
    @Test
    fun nightPaletteActuallyDiffersFromLight() {
        val light = colors(File(resDir, "values/colors.xml"))
        val night = colors(File(resDir, "values-night/colors.xml"))
        val common = light.keys.intersect(night.keys).filter { !night.getValue(it).startsWith("@color/") }
        val differing = common.filter { light.getValue(it).lowercase() != night.getValue(it).lowercase() }
        assertTrue(
            "深色令牌与浅色几乎完全相同(仅 " + differing.size + "/" + common.size + " 项不同),深色模式很可能没生效",
            differing.size >= 8
        )
    }

    /** 深色模式必须把状态栏图标切回浅色 */
    @Test
    fun nightThemeDisablesLightStatusBar() {
        val theme = File(resDir, "values-night/themes.xml")
        assertTrue("应存在 res/values-night/themes.xml", theme.isFile)
        val text = theme.readText()
        assertTrue(
            "深色主题必须显式声明 android:windowLightStatusBar=false,否则状态栏图标在深色底上不可见",
            Regex("<item name=\"android:windowLightStatusBar\">\\s*false\\s*</item>").containsMatchIn(text)
        )
        assertTrue(
            "深色主题必须设置 windowBackground,否则深色下会露出系统白底",
            text.contains("android:windowBackground")
        )
    }
}
