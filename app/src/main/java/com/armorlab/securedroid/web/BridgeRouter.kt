package com.armorlab.securedroid.web

import android.os.Handler
import android.os.Looper
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.web.handlers.DashboardHandler
import com.armorlab.securedroid.web.handlers.LockHandler
import com.armorlab.securedroid.web.handlers.ModeHandler
import com.armorlab.securedroid.web.handlers.ScanHandler
import com.armorlab.securedroid.web.handlers.SettingsHandler
import com.armorlab.securedroid.web.handlers.ToolsHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 桥层路由:页面唯一的动作入口(action + JSON payload)分派到职责化 handler,
 * 并按 id 回传统一信封(见 [WebEventSink])。
 *
 * 线程约定:调用线程(AndroidBridge 线程)只做解析,**永不阻塞**;
 * 数据/扫描动作挂应用级 [BridgeScope.default],UI 动作(对话框/跳转)挂主线程。
 * 新增能力 = 在 [actions] 注册一项,不再往上帝对象里堆 @JavascriptInterface。
 */
class BridgeRouter(
    activity: MainActivity,
    private val sink: WebEventSink
) {

    private val main = Handler(Looper.getMainLooper())

    private class Action(val onMain: Boolean, val run: suspend (JSONObject) -> String?)

    private val actions: Map<String, Action> = buildMap {
        val dashboard = DashboardHandler(activity.applicationContext)
        val scan = ScanHandler(activity.applicationContext, sink)
        val lock = LockHandler(activity.applicationContext, activity, sink)
        val settings = SettingsHandler(activity.applicationContext, activity, sink)
        val tools = ToolsHandler(activity.applicationContext, activity, sink)
        val mode = ModeHandler(activity.applicationContext)

        // 初始化模式选择(强制)
        put("getMode", Action(false) { mode.getMode() })
        put("setMode", Action(false) { json -> mode.setMode(json) })
        put("openPermissionSettings", Action(true) { json ->
            settings.openPermissionSettings(json.optString("key")); null
        })

        // 数据读取(request→reply)
        put("getDashboard", Action(false) { dashboard.getDashboard() })
        put("getAudit", Action(false) { dashboard.getAudit() })
        put("getLockState", Action(false) { lock.getLockState() })
        put("getToggles", Action(false) { settings.getToggles() })
        put("getTheme", Action(false) { settings.getTheme() })
        put("setTheme", Action(false) { json ->
            settings.setTheme(json.optString("theme")); null
        })
        put("getScanState", Action(false) { scan.scanState() })

        // 扫描(启动即返回,进度/结果走事件)
        put("startVirusScan", Action(false) { scan.startVirusScan(); null })
        put("startTrojanScan", Action(false) { scan.startTrojanScan(); null })
        put("startRootkit", Action(false) { scan.startRootkitScan(); null })
        put("startModuleScan", Action(false) { scan.startModuleScan(); null })
        put("startLockerScan", Action(false) { scan.startLockerScan(); null })
        put("cancelScan", Action(false) { scan.cancelScan(); null })

        // 应用锁
        put("setLocked", Action(false) { json ->
            lock.setLocked(json.optString("pkg"), json.optBoolean("locked")); null
        })
        put("toggleDecoy", Action(false) { json ->
            lock.toggleDecoy(json.optBoolean("on")); null
        })
        put("toggleBiometric", Action(false) { json ->
            lock.toggleBiometric(json.optBoolean("on")); null
        })
        put("savePin", Action(false) { json ->
            val pin = json.optString("pin")
            val ok = pin.length == 4 && com.armorlab.securedroid.lock.AppLockStore.setPin(activity, pin)
            if (ok) sink.sendEvent("lockStateChanged", "{}")
            JSONObject().put("ok", ok).toString()
        })

        // 防护开关
        put("toggleRealtime", Action(false) { json ->
            settings.toggleRealtime(json.optBoolean("on")); null
        })
        put("toggleBoot", Action(false) { json ->
            settings.toggleBoot(json.optBoolean("on")); null
        })
        put("toggleAutoUpdate", Action(false) { json ->
            settings.toggleAutoUpdate(json.optBoolean("on")); null
        })
        put("toggleRoot", Action(false) { json ->
            settings.toggleRoot(json.optBoolean("on")); null
        })

        // --- 深层工具(HTML化) ---
        put("getNetAudit", Action(false) { tools.getNetAudit() })
        put("getPrivacyAudit", Action(false) { tools.getPrivacyAudit() })
        put("getVulnScan", Action(false) { tools.getVulnScan() })

        put("startDeepScan", Action(false) { tools.startDeepScan(); null })

        put("getVirusCenterMenu", Action(false) { tools.getVirusCenterMenu() })
        put("runVirusTool", Action(false) { json ->
            tools.runVirusTool(json.optString("action")); null
        })
        put("cancelVirusTool", Action(false) { tools.cancelVirusTool(); null })

        put("getTrustList", Action(false) { tools.getTrustList() })
        put("removeTrust", Action(false) { json ->
            tools.removeTrust(json.optString("pkg")); null
        })
        put("getBlocklist", Action(false) { tools.getBlocklist() })
        put("addBlocklist", Action(false) { json ->
            JSONObject().put("ok", tools.addBlocklist(json.optString("pattern"))).toString()
        })
        put("getPolicy", Action(false) { tools.getPolicy() })
        put("setPolicyLevel", Action(false) { json ->
            tools.setPolicyLevel(json.optString("level")); null
        })
        put("setAutoQuarantine", Action(false) { json ->
            tools.setAutoQuarantine(json.optBoolean("on")); null
        })
        put("getCertMarkApps", Action(false) { tools.certMarkApps() })
        put("markCertGood", Action(false) { json ->
            JSONObject().put("ok", tools.markCertGood(json.optString("pkg"))).toString()
        })
        put("getUpdateInfo", Action(false) { tools.getUpdateInfo() })
        put("checkUrl", Action(false) { json ->
            tools.checkUrl(json.optString("url"))
        })
        put("runUpdate", Action(false) { json ->
            tools.runUpdate(json.optString("url"), json.optString("sha").ifBlank { null })
        })
        put("getAbout", Action(false) { tools.getAbout() })
        put("exportReport", Action(false) { tools.exportReport(); null })
        put("runFixCommand", Action(false) { json ->
            JSONObject().put("ok", tools.runFixCommand(json.optString("cmd"))).toString()
        })

        // UI 动作(对话框 / 跳转 / 触感)挂主线程
        put("showPinDialog", Action(true) { lock.showPinDialog(); null })
        put("openAccessibility", Action(true) { lock.openAccessibility(); null })
        put("checkUpdate", Action(true) { settings.checkUpdate(); null })
        put("showAbout", Action(true) { settings.showAbout(); null })
        put("openDeepScan", Action(true) { settings.openDeepScan(); null })
        put("openVirusCenter", Action(true) { settings.openVirusCenter(); null })
        put("openNetworkAudit", Action(true) { settings.openNetworkAudit(); null })
        put("openPrivacy", Action(true) { settings.openPrivacy(); null })
        put("openVulnerability", Action(true) { settings.openVulnerability(); null })
        put("openAppSettings", Action(true) { json ->
            settings.openAppSettings(json.optString("pkg")); null
        })
        put("haptic", Action(false) { settings.haptic(); null })
    }

    fun dispatch(action: String?, payload: String?) {
        if (action.isNullOrBlank()) return
        val json = try {
            JSONObject(payload?.takeIf { it.isNotBlank() } ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        val id = json.optInt("id", 0)
        val a = actions[action]
        if (a == null) {
            if (id != 0) sink.sendReply(id, ok = false, dataJson = null, error = "未知操作: $action")
            return
        }
        BridgeScope.default.launch(if (a.onMain) Dispatchers.Main.immediate else Dispatchers.Default) {
            try {
                val data = a.run(json)
                if (id != 0) sink.sendReply(id, ok = true, dataJson = data, error = null)
            } catch (t: Throwable) {
                if (id != 0) sink.sendReply(id, ok = false, dataJson = null, error = t.message ?: "执行失败")
            }
        }
    }
}
