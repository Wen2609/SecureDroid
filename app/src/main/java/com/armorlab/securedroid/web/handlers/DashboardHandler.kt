package com.armorlab.securedroid.web.handlers

import android.content.Context
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.lock.AppLockStore
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.trojan.ClamAvSignatures
import org.json.JSONObject

/** 首页概览数据源。全部查询挂起执行,不再阻塞 JS 线程(旧 getDashboard 为同步桥调用)。 */
class DashboardHandler(private val app: Context) {

    suspend fun getDashboard(): String = try {
        val dao = AppDatabase.get(app).scanRecordDao()
        // 三个标量一次取回:countAll/lastScannedAt 是聚合查询,不整表拉 2000 行
        val scanned = dao.countAll()
        val threats = dao.threatCount()
        val lastScan = dao.lastScannedAt() ?: 0L
        val prefs = BridgeKeys.settings(app)
        ClamAvSignatures.ensureLoaded(app)
        var protected = 0
        if (prefs.getBoolean(BridgeKeys.REALTIME, false)) protected++
        if (prefs.getBoolean(BridgeKeys.BOOT, true)) protected++
        if (prefs.getBoolean("daily_scan_enabled", false)) protected++
        if (prefs.getBoolean("sim_guard_enabled", false)) protected++
        if (RootGuard.isRootMode(app)) protected++
        JSONObject()
            .put("libOk", ClamAvSignatures.hashCount() > 0)
            .put("scanned", scanned)
            .put("threats", threats)
            .put("protected", protected)
            .put("locked", AppLockStore.lockedApps(app).size)
            .put("risky", PermissionAuditor.riskyAppCount(app))
            .put("lastScanAt", lastScan)
            .toString()
    } catch (t: Throwable) {
        JSONObject()
            .put("libOk", false).put("scanned", 0).put("threats", 0).put("protected", 0)
            .put("locked", 0).put("risky", 0).put("lastScanAt", 0L)
            .toString()
    }

    suspend fun getAudit(): String = try {
        val items = PermissionAuditor.audit(app)
        val risky = items.count { it.score >= 40 && it.packageName != app.packageName }
        val arr = org.json.JSONArray()
        items.forEach {
            val perms = org.json.JSONArray()
            it.risky.forEach { p -> perms.put(p) }
            arr.put(JSONObject().put("name", it.appName).put("pkg", it.packageName)
                .put("score", it.score).put("level", it.level.name).put("perms", perms))
        }
        JSONObject()
            .put("count", risky)
            .put("summary", if (risky > 0)
                "发现 $risky 个应用存在高风险权限组合，建议逐项审查。"
            else
                "未发现高风险权限应用。")
            .put("items", arr)
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("count", 0).put("summary", "审计失败：" + (t.message ?: ""))
            .put("items", org.json.JSONArray()).toString()
    }
}

/** 桥层共享偏好键 */
internal object BridgeKeys {
    const val REALTIME = "realtime_enabled"
    const val BOOT = "boot_enabled"
    const val THEME = "theme"

    fun settings(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
