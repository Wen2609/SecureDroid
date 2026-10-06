package com.armorlab.securedroid.web

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.armorlab.securedroid.MainActivity

/**
 * WebView → 原生 桥接入口(薄壳)。
 *
 * 页面上传稿 HTML(assets/ui/index.html + app.js)的全部交互经唯一入口
 * [post](action + JSON payload) 进入 [BridgeRouter],由职责化 handler 分派:
 * 数据读取(request→reply 信封)、扫描(启动即返回 + 事件推送)、
 * UI 动作(对话框/跳转,主线程)。
 *
 * 线程约定:此方法运行在 WebView 的 JavaBridge 线程,路由只做解析即返回,
 * **永不阻塞** —— 旧实现的 4 个同步 getter(getDashboard 等)会冻结 JS 线程,
 * 已在 v1.9.15 全部改为异步应答。
 */
class NativeBridge(
    activity: MainActivity,
    web: WebView
) {

    private val router = BridgeRouter(activity, WebViewEventSink(web))

    /** 页面唯一调用入口:AndroidBridge.post(action, jsonPayload) */
    @JavascriptInterface
    fun post(action: String, payload: String) {
        router.dispatch(action, payload)
    }
}
