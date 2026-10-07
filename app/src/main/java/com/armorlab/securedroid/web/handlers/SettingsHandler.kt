package com.armorlab.securedroid.web.handlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.widget.Toast
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.realtime.BootReceiver
import com.armorlab.securedroid.realtime.RealtimeProtectionService
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.web.AppMode
import com.armorlab.securedroid.web.AppModeStore
import com.armorlab.securedroid.web.BridgeScope
import com.armorlab.securedroid.web.WebEventSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 工具箱:防护开关、深层工具入口、关于/检查更新、触感。
 * 开关写入挂在 Default 线程,对话框与页面跳转挂主线程(路由按注册分派)。
 */
class SettingsHandler(
    private val app: Context,
    private val activity: MainActivity,
    private val sink: WebEventSink
) {

    suspend fun getToggles(): String = try {
        val prefs = BridgeKeys.settings(app)
        val rootMode = RootGuard.isRootMode(app)
        JSONObject()
            .put("realtime", prefs.getBoolean(BridgeKeys.REALTIME, false))
            .put("boot", prefs.getBoolean(BridgeKeys.BOOT, true))
            .put("autoUpdate", prefs.getBoolean("auto_update_enabled", false))
            .put("rootMode", rootMode)
            .put("mode", AppModeStore.current(app).id)
            .put("rootSub", if (rootMode) app.getString(R.string.root_mode_on)
                else app.getString(R.string.root_mode_off))
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("realtime", false).put("boot", true)
            .put("rootMode", false).put("mode", AppModeStore.current(app).id).put("rootSub", "").toString()
    }

    /** 外观主题:跟随系统 / 浅色 / 深色(默认跟随系统) */
    fun getTheme(): String = try {
        val theme = BridgeKeys.settings(app).getString(BridgeKeys.THEME, "system")
        JSONObject().put("theme", theme).toString()
    } catch (t: Throwable) {
        JSONObject().put("theme", "system").toString()
    }

    fun setTheme(theme: String) {
        if (theme != "system" && theme != "light" && theme != "dark") return
        try {
            BridgeKeys.settings(app).edit().putString(BridgeKeys.THEME, theme).apply()
        } catch (_: Exception) {
        }
    }

    fun toggleRealtime(on: Boolean) {
        try {
            if (on) RealtimeProtectionService.start(app) else RealtimeProtectionService.stop(app)
            BridgeKeys.settings(app).edit().putBoolean(BridgeKeys.REALTIME, on).apply()
        } catch (_: Exception) {
        }
    }

    fun toggleBoot(on: Boolean) {
        try {
            BridgeKeys.settings(app).edit().putBoolean(BridgeKeys.BOOT, on).apply()
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            app.packageManager.setComponentEnabledSetting(
                ComponentName(app, BootReceiver::class.java), state, PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
        }
    }

    /** 病毒库自动更新开关:每日联网检查一次(需已配置更新源 URL + SHA-256) */
    fun toggleAutoUpdate(on: Boolean) {
        try {
            BridgeKeys.settings(app).edit().putBoolean("auto_update_enabled", on).apply()
            com.armorlab.securedroid.feature.UpdateScheduler.sync(app)
        } catch (_: Exception) {
        }
    }

    /** Root 模式:仅超级用户模式可用;开启前先探测 su(可能阻塞等待授权),失败把开关状态回推给页面 */
    suspend fun toggleRoot(on: Boolean) {
        if (AppModeStore.current(app) != AppMode.SUPERUSER) {
            postToast(R.string.toast_mode_superuser_only)
            postRootState()
            return
        }
        try {
            withContext(Dispatchers.Default) {
                if (on) {
                    val ok = RootGuard.probeRoot(app)
                    if (ok) RootGuard.setRootMode(app, true)
                    postRootState()
                    postToast(if (ok) R.string.toast_root_enabled else R.string.toast_need_root)
                } else {
                    RootGuard.setRootMode(app, false)
                    postRootState()
                }
            }
        } catch (t: Throwable) {
            postRootState()
        }
    }

    private fun postRootState() {
        val rootMode = RootGuard.isRootMode(app)
        sink.sendEvent(
            "rootState",
            JSONObject()
                .put("rootMode", rootMode)
                .put("rootSub", if (rootMode) app.getString(R.string.root_mode_on)
                    else app.getString(R.string.root_mode_off))
                .toString()
        )
    }

    private fun postToast(res: Int) {
        Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    }

    /* ---------------- 安全设置:关于 / 检查更新(改为HTML模态框事件) ---------------- */

    fun showAbout() {
        sink.sendEvent("showAbout", JSONObject().toString())
    }

    fun checkUpdate() {
        sink.sendEvent("showUpdateDialog", JSONObject().toString())
    }

    /* ---------------- 深层工具入口(改为HTML子页面路由) ---------------- */

    fun openDeepScan() = openSubPage("deepscan")
    fun openVirusCenter() = openSubPage("viruscenter")
    fun openNetworkAudit() = openSubPage("netaudit")
    fun openPrivacy() = openSubPage("privacy")
    fun openVulnerability() = openSubPage("vuln")

    private fun openSubPage(page: String) {
        sink.sendEvent("openSubPage", JSONObject().put("page", page).toString())
    }

    /** 从扫描 / 审计结果行跳到指定应用的应用详情页(可改权限 / 卸载) */
    fun openAppSettings(packageName: String) {
        if (packageName.isBlank()) return
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
        }
    }

    /** 轻触感反馈(开关 / 扫描 / 导航等关键交互),失败静默 */
    fun haptic() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                val manager =
                    activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18)
            }
        } catch (_: Exception) {
        }
    }

    /* ---------------- 模式权限引导(强制授权,不充足无法工作) ---------------- */

    /** 跳转对应权限设置页;root 键触发 su 授权探测并把结果回推页面 */
    fun openPermissionSettings(key: String) {
        when (key) {
            "notification" -> {
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                    )
                } catch (_: Exception) {
                    try {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${app.packageName}")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            "usage_stats" -> {
                try {
                    activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                }
            }
            "root" -> {
                BridgeScope.default.launch {
                    val ok = RootGuard.probeRoot(app)
                    if (ok) RootGuard.setRootMode(app, true)
                    sink.sendEvent(
                        "modePermissions",
                        JSONObject().put("root", ok).toString()
                    )
                }
            }
        }
    }
}
