package com.armorlab.securedroid.web.handlers

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import com.armorlab.securedroid.R
import com.armorlab.securedroid.root.PrivilegeManager
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.web.AppMode
import com.armorlab.securedroid.web.AppModeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 初始化模式选择:页面启动时先 [getMode] 判断是否已强制选择;
 * [setMode] 持久化所选模式,并在超级用户模式下联动探测 su 与 Root 模式开关。
 *
 * 需求:v1.9.17 起,选择模式后必须检查该模式所需权限是否充足,
 * 权限不充足则无法工作(前端引导授予,全部满足后才进入主界面)。
 */
class ModeHandler(private val app: Context) {

    fun getMode(): String {
        val mode = AppModeStore.current(app)
        return JSONObject()
            .put("selected", AppModeStore.isSelected(app))
            .put("mode", mode.id)
            .put("rootMode", RootGuard.isRootMode(app))
            .put("permissions", permissionsArray(mode))
            .toString()
    }

    suspend fun setMode(json: JSONObject): String = withContext(Dispatchers.Default) {
        val mode = AppMode.fromId(json.optString("mode"))
            ?: return@withContext JSONObject().put("ok", false).toString()
        AppModeStore.save(app, mode)
        when (mode) {
            AppMode.SUPERUSER -> {
                if (RootGuard.probeRoot(app)) RootGuard.setRootMode(app, true)
            }
            else -> RootGuard.setRootMode(app, false)
        }
        JSONObject()
            .put("ok", true)
            .put("mode", mode.id)
            .put("permissions", permissionsArray(mode))
            .toString()
    }

    /** 当前模式所需权限清单:每项 {key,label,desc,granted} */
    private fun permissionsArray(mode: AppMode): JSONArray {
        val arr = JSONArray()
        fun add(key: String, label: String, desc: String, granted: Boolean) {
            arr.put(JSONObject()
                .put("key", key)
                .put("label", label)
                .put("desc", desc)
                .put("granted", granted))
        }
        // 所有模式都必须有通知权限(威胁告警 / 实时防护提示)
        add(
            "notification",
            app.getString(R.string.perm_notification_label),
            app.getString(R.string.perm_notification_desc),
            isNotificationGranted()
        )
        when (mode) {
            AppMode.WIRELESS_DEBUG -> add(
                "usage_stats",
                app.getString(R.string.perm_usage_stats_label),
                app.getString(R.string.perm_usage_stats_desc),
                isUsageStatsGranted()
            )
            AppMode.SUPERUSER -> add(
                "root",
                app.getString(R.string.perm_root_label),
                app.getString(R.string.perm_root_desc),
                PrivilegeManager.isRoot(app)
            )
            AppMode.STANDARD -> {}
        }
        return arr
    }

    private fun isNotificationGranted(): Boolean = try {
        NotificationManagerCompat.from(app).areNotificationsEnabled()
    } catch (_: Exception) {
        true // 低于 API 33 未引入运行时通知权限,视为已充足
    }

    private fun isUsageStatsGranted(): Boolean = try {
        val appOps = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}