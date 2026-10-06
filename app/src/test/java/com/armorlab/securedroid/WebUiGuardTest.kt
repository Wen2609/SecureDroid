package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertEquals
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
        assertTrue(
            "动态空态必须用 emptyHint(图标 + 文案),不得回退纯文本",
            Regex("emptyHint\\(").findAll(js).count() >= 4
        )
    }

    @Test
    fun heroCardMustBeThreatAware() {
        val js = asset("ui/app.js")
        assertTrue(
            "首页主状态卡必须按 threats/risky/libOk 切换 data-state(安全/注意/危险三态)",
            js.contains("heroCard.dataset.state") && js.contains("heroTitle")
        )
        val html = asset("ui/index.html")
        assertTrue(
            "index.html 必须提供 data-state=warn/danger 的三态样式",
            html.contains("[data-state=\"warn\"]") && html.contains("[data-state=\"danger\"]")
        )
    }

    @Test
    fun lockListMustBeSearchable() {
        val js = asset("ui/app.js")
        assertTrue(
            "应用锁列表(可达数百行)必须提供搜索过滤:输入过滤缓存数据,不重新拉桥",
            js.contains("lockQuery") && js.contains("renderLockRows")
        )
        val html = asset("ui/index.html")
        assertTrue(
            "index.html 必须提供应用锁搜索输入框 lockSearch",
            html.contains("id=\"lockSearch\"")
        )
    }

    @Test
    fun statCellsMustBeActionable() {
        val html = asset("ui/index.html")
        assertTrue(
            "统计条三格必须可点直达:病毒库/防护中走 data-goto,已扫描走 statScanned 桥调用",
            html.contains("class=\"stat-cell\" data-goto=") && html.contains("id=\"statScanned\"")
        )
        val js = asset("ui/app.js")
        assertTrue(
            "「已扫描」统计格必须绑定打开病毒查杀中心",
            js.contains("bind('statScanned'")
        )
    }

    @Test
    fun pullRefreshMustBeWired() {
        val module = moduleDir()
        val layout = File(module, "src/main/res/layout/activity_main.xml")
        assertTrue("缺少 activity_main.xml", layout.isFile)
        assertTrue(
            "主布局必须用 SwipeRefreshLayout 包裹 WebView 支持下拉刷新",
            layout.readText().contains("SwipeRefreshLayout")
        )
        val activity = File(module, "src/main/java/com/armorlab/securedroid/MainActivity.kt")
        assertTrue("缺少 MainActivity.kt", activity.isFile)
        assertTrue(
            "MainActivity 必须接 setOnRefreshListener 触发 __sdReady",
            activity.readText().contains("setOnRefreshListener")
        )
    }

    @Test
    fun bridgeMustBeSinglePostRouter() {
        val module = moduleDir()
        val bridge = File(module, "src/main/java/com/armorlab/securedroid/web/NativeBridge.kt")
        assertTrue("缺少 NativeBridge.kt", bridge.isFile)
        val text = bridge.readText()
        val ifaceCount = Regex("@JavascriptInterface").findAll(text).count()
        assertEquals(
            "桥入口必须收敛为唯一 post(action, payload),不得再逐方法加 @JavascriptInterface",
            1, ifaceCount
        )
        assertTrue("入口必须委托 BridgeRouter", text.contains("BridgeRouter"))
        val router = File(module, "src/main/java/com/armorlab/securedroid/web/BridgeRouter.kt")
        assertTrue("缺少 BridgeRouter.kt", router.isFile)
        assertTrue(
            "路由必须带统一应答信封(sendReply ok/error)",
            router.readText().contains("sendReply")
        )
    }

    @Test
    fun jsDataMustFlowThroughApiChannel() {
        val js = asset("ui/app.js")
        assertTrue(
            "JS 必须经 __sdChannel 信封接收消息(reply + event)",
            js.contains("__sdChannel")
        )
        assertTrue(
            "JS 数据读取必须走 api.request(异步应答),不得再同步调用 bridge.getXxx 冻结 JS 线程",
            !Regex("bridge\\.get[A-Z]").containsMatchIn(js)
        )
        assertTrue(
            "扫描启动必须走 api.send(action),不得直接 bridge.startXxx",
            !Regex("bridge\\.start[A-Z]").containsMatchIn(js)
        )
    }

    @Test
    fun scanStateMustBeReplayedToNewPage() {
        val js = asset("ui/app.js")
        assertTrue(
            "页面就绪必须请求 getScanState 重放进行中的扫描(旋转/重建不丢进度)",
            js.contains("getScanState") && js.contains("applyScanState")
        )
        val module = moduleDir()
        val sessions = File(module, "src/main/java/com/armorlab/securedroid/web/ScanSessions.kt")
        assertTrue("缺少 ScanSessions.kt(进程级会话注册表)", sessions.isFile)
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

    @Test
    fun scanMustBeCancellableFromWebUi() {
        val js = asset("ui/app.js")
        assertTrue(
            "病毒/木马扫描必须提供取消按钮与取消流程(btnVirusCancel / btnTrojanCancel / requestCancelScan)",
            js.contains("btnVirusCancel") && js.contains("btnTrojanCancel") && js.contains("requestCancelScan")
        )
    }

    @Test
    fun segTouchTargetMustBeAtLeast48px() {
        val html = asset("ui/index.html")
        assertTrue(
            "分段按钮触摸目标必须 ≥48px(项目硬规则;上传稿的 42px 不达标)",
            Regex("\\.seg\\{[^}]*height:48px", RegexOption.DOT_MATCHES_ALL).containsMatchIn(html)
        )
    }

    @Test
    fun virusResultsMustCollapseCleanApps() {
        val js = asset("ui/app.js")
        assertTrue(
            "病毒扫描结果默认只渲染感染项,干净应用折叠进展开器(more-toggle),不得一次铺几百行",
            js.contains("more-toggle") && js.contains("展开其余")
        )
    }

    @Test
    fun switchRowsMustBeFullyTappable() {
        val js = asset("ui/app.js")
        assertTrue(
            "开关行必须整行可点(开关本体 46×28px 达不到 48px 触摸目标)",
            js.contains("closest('.protection-item')")
        )
    }
}
