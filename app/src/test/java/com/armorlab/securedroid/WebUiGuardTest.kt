package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebUI 资产性能守卫(源码级)。
 *
 * 主界面由 assets/ui/index.html + app.js 承载,它的性能回退(图标不再懒加载、
 * 列表逐行 append、转后台不暂停动画)同样不会被任何功能测试发现,
 * 与 PerfGuardTest 同理用静态断言钉住。
 */
class WebUiGuardTest {

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

    @Test
    fun appIconsMustBeLazyLoaded() {
        val js = asset("ui/app.js")
        assertTrue(
            "应用图标必须走 IntersectionObserver 懒加载:应用锁列表可达数百行," +
                "一次性发起全部图标请求会让首屏触发几百次原生位图解码",
            js.contains("IntersectionObserver")
        )
        assertTrue(
            "图标必须经 data-src 暂存,进入视口才赋值 src",
            js.contains("dataset.src")
        )
    }

    @Test
    fun resultListsMustBatchAppend() {
        val js = asset("ui/app.js")
        val count = Regex("createDocumentFragment").findAll(js).count()
        assertTrue(
            "结果列表(病毒扫描/木马/审计/应用锁)必须用 DocumentFragment 批量插入,当前 " +
                count + " 处(至少 4 处)",
            count >= 4
        )
    }

    @Test
    fun refreshMustBeCoalesced() {
        val js = asset("ui/app.js")
        assertTrue(
            "导航刷新必须走 scheduleRefresh 尾沿节流,不得再直接 setTimeout(refreshVisible)",
            !js.contains("setTimeout(refreshVisible")
        )
        assertTrue(
            "refreshVisible 必须带时间窗去重(挡住启动时 init 与 onPageFinished 的双加载)",
            js.contains("lastRefreshAt")
        )
    }

    @Test
    fun pageHiddenMustPauseDecorativeAnimations() {
        val html = asset("ui/index.html")
        assertTrue(
            "index.html 必须提供 .page-hidden 冻结规则(光晕/扫描动画转后台即暂停)",
            html.contains(".page-hidden .blob") && html.contains("animation-play-state:paused")
        )
        val js = asset("ui/app.js")
        assertTrue(
            "app.js 必须监听 visibilitychange 切换 page-hidden 类",
            js.contains("visibilitychange")
        )
    }
}
