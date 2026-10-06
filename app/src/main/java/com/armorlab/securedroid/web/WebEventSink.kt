package com.armorlab.securedroid.web

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * 原生 → 页面 的出站通道。
 *
 * 所有跨桥消息走统一信封,由 [sendEvent]/[sendReply] 构造,杜绝散落的字符串拼接:
 * - 事件:`{kind:"event", type:"virusProgress", data:{...}}`
 * - 应答:`{kind:"reply", id:3, ok:true, data:{...}}` / `{kind:"reply", id:3, ok:false, error:"..."}`
 *
 * 当前实现为 evaluateJavascript 注入 JSON **字面量**(org.json 负责全部转义,
 * 数据永远是数据、不是代码)。将来切 WebMessageCompat 时只需替换实现,页面侧
 * `window.__sdChannel` 接口不变。
 */
interface WebEventSink {

    /** 推送事件(扫描进度/结果、状态变化等) */
    fun sendEvent(type: String, dataJson: String?)

    /** 对 JS 发起的 request 按 id 应答 */
    fun sendReply(id: Int, ok: Boolean, dataJson: String?, error: String?)
}

class WebViewEventSink(private val web: WebView) : WebEventSink {

    private val main = Handler(Looper.getMainLooper())

    override fun sendEvent(type: String, dataJson: String?) {
        val envelope = JSONObject()
            .put("kind", "event")
            .put("type", type)
            .put("data", wrap(dataJson))
        inject(envelope)
    }

    override fun sendReply(id: Int, ok: Boolean, dataJson: String?, error: String?) {
        val envelope = JSONObject()
            .put("kind", "reply")
            .put("id", id)
            .put("ok", ok)
        if (ok) envelope.put("data", wrap(dataJson)) else envelope.put("error", error ?: "执行失败")
        inject(envelope)
    }

    /** dataJson 是 handler 产出的 JSON 对象/数组字面量;解析失败则按普通字符串嵌入 */
    private fun wrap(dataJson: String?): Any = when {
        dataJson == null -> JSONObject()
        dataJson.startsWith("{") -> runCatching { JSONObject(dataJson) }.getOrElse { dataJson }
        dataJson.startsWith("[") -> runCatching { JSONArray(dataJson) }.getOrElse { dataJson }
        else -> dataJson
    }

    private fun inject(envelope: JSONObject) {
        main.post {
            try {
                web.evaluateJavascript("window.__sdChannel&&window.__sdChannel($envelope);", null)
            } catch (_: Exception) {
                // WebView 已销毁:消息丢弃;新页面上线后经 getScanState / 刷新重取
            }
        }
    }
}
