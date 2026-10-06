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
import com.armorlab.securedroid.web.WebEventSink
import kotlinx.coroutines.Dispatchers
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
            .put("rootMode", rootMode)
            .put("rootSub", if (rootMode) app.getString(R.string.root_mode_on)
                else app.getString(R.string.root_mode_off))
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("realtime", false).put("boot", true)
            .put("rootMode", false).put("rootSub", "").toString()
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

    /** Root 模式:开启前先探测 su(可能阻塞数十秒等待授权),失败把开关状态回推给页面 */
    suspend fun toggleRoot(on: Boolean) {
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
}
