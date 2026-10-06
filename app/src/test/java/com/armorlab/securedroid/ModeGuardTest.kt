package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 初始化模式选择守卫(源码级)。
 *
 * 需求:软件初始化必须强制选择运行模式(标准模式 / 无线调试模式 / 超级用户模式),
 * 各模式只可用有权限功能。该测试钉住三件事:模式枚举存在、桥接注册、HTML 强制选择层存在。
 */
class ModeGuardTest {

    private fun moduleDir(): File {
        val candidates = listOf(File(""), File(".."))
        candidates.firstOrNull { File(it, "src/main/assets/ui").isDirectory }?.let { return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/assets/ui")
            if (f.isDirectory) return File(dir, "app")
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 app 模块目录")
    }

    private fun asset(rel: String): String {
        val f = File(moduleDir(), "src/main/assets/$rel")
        assertTrue("缺少资产文件: " + rel, f.isFile)
        return f.readText()
    }

    private fun code(rel: String): String {
        val f = File(moduleDir(), "src/main/java/com/armorlab/securedroid/$rel")
        assertTrue("缺少源文件: " + rel, f.isFile)
        return f.readText()
    }

    /** 模式枚举必须存在,且产品定义的三档名称不可修改 */
    @Test
    fun modeEnumDefinesTheThreeProductModes() {
        val mode = code("web/AppMode.kt")
        for (name in listOf("标准模式", "无线调试模式", "超级用户模式")) {
            assertTrue("AppMode 必须包含模式名「$name」", mode.contains(name))
        }
        for (id in listOf("standard", "wireless", "superuser")) {
            assertTrue("AppMode 必须包含模式 id「$id」", mode.contains("\"$id\""))
        }
    }

    /** 桥接层必须注册 getMode / setMode,供 HTML 初始化时强制选择 */
    @Test
    fun bridgeRegistersModeActions() {
        val router = code("web/BridgeRouter.kt")
        assertTrue("BridgeRouter 必须注册 getMode", router.contains("\"getMode\""))
        assertTrue("BridgeRouter 必须注册 setMode", router.contains("\"setMode\""))
        val handler = code("web/handlers/ModeHandler.kt")
        assertTrue("缺少 ModeHandler.kt(模式选择后端)", handler.isNotBlank())
    }

    /** index.html 必须提供强制选择覆盖层与三个模式卡片 */
    @Test
    fun htmlProvidesForcedModeSelection() {
        val html = asset("ui/index.html")
        assertTrue("index.html 必须提供 modeOverlay 覆盖层", html.contains("id=\"modeOverlay\""))
        assertTrue("modeOverlay 默认必须隐藏(hidden 属性)", html.contains("id=\"modeOverlay\" hidden"))
        assertTrue("必须提供确认按钮 btnModeConfirm", html.contains("id=\"btnModeConfirm\""))
        for (mode in listOf("standard", "wireless", "superuser")) {
            assertTrue("modeOverlay 必须包含 data-mode=\"$mode\" 卡片", html.contains("data-mode=\"$mode\""))
        }
    }

    /** app.js 必须实现初始化拉取模式、未选择则强制显示、选择后应用门控 */
    @Test
    fun jsWiresModeSelectionFlow() {
        val js = asset("ui/app.js")
        assertTrue("app.js 必须请求 getMode", js.contains("api.request('getMode'"))
        assertTrue("app.js 必须发送 setMode", js.contains("setMode'"))
        assertTrue("app.js 必须实现 applyMode 门控", js.contains("applyMode"))
        assertTrue("app.js 必须绑定模式卡片选择", js.contains("mode-card"))
    }
}