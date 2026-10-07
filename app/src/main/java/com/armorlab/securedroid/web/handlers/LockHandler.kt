package com.armorlab.securedroid.web.handlers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.lock.AppLockStore
import com.armorlab.securedroid.web.WebEventSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 应用锁:状态读取(单次读锁集合)、开关写入、PIN 弹窗与无障碍入口。 */
class LockHandler(
    private val app: Context,
    private val activity: MainActivity,
    private val sink: WebEventSink
) {

    suspend fun getLockState(): String = try {
        withContext(Dispatchers.Default) { buildLockState() }
    } catch (t: Throwable) {
        JSONObject().put("hasPin", false).put("decoy", false).put("biometric", false)
            .put("canBiometric", false)
            .put("accessibility", false).put("apps", JSONArray()).toString()
    }

    // 锁定集合只读一次,逐行内存判锁;排序在纯数据上做,
    // 不再对每个应用各读一次加密存储、再逐个反查 JSONObject 字符串排序
    private fun buildLockState(): String {
        val locked = AppLockStore.lockedApps(app)
        val entries = PackageSnapshot.installedApplications(app, 0)
            .asSequence()
            .filter { it.packageName != app.packageName }
            .map { Triple(PackageSnapshot.label(app, it), it.packageName, it.packageName in locked) }
            .sortedWith(compareBy({ it.first.lowercase() }, { it.second }))
            .toList()
        val apps = JSONArray()
        entries.forEach { (name, pkg, isLocked) ->
            apps.put(JSONObject().put("name", name).put("pkg", pkg).put("locked", isLocked))
        }
        return JSONObject()
            .put("hasPin", AppLockStore.hasPin(app))
            .put("decoy", AppLockStore.isDecoyEnabled(app))
            .put("biometric", AppLockStore.isBiometricEnabled(app))
            .put("canBiometric", AppLockStore.canBiometric(app))
            .put("accessibility", isAccessibilityEnabled())
            .put("apps", apps)
            .toString()
    }

    fun setLocked(pkg: String, locked: Boolean) {
        try {
            AppLockStore.setLocked(app, pkg, locked)
        } catch (_: Exception) {
        }
    }

    fun toggleDecoy(on: Boolean) {
        try {
            AppLockStore.setDecoyEnabled(app, on)
        } catch (_: Exception) {
        }
    }

    /**
     * 生物识别开关:开启前提是已设 PIN 且设备具备可用生物识别,
     * 否则拒绝写入并回推真实状态(前端开关自动回弹)。
     */
    fun toggleBiometric(on: Boolean) {
        val allowed = !on || (AppLockStore.hasPin(app) && AppLockStore.canBiometric(app))
        if (allowed) {
            try {
                AppLockStore.setBiometricEnabled(app, on)
            } catch (_: Exception) {
            }
        }
        sink.sendEvent("lockStateChanged", JSONObject().toString())
    }

    fun showPinDialog() {
        sink.sendEvent("showPinDialog", JSONObject().toString())
    }

    fun openAccessibility() {
        try {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val services = Settings.Secure.getString(
            activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.contains(activity.packageName)
    }
}
