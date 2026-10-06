package com.armorlab.securedroid.root

import android.content.Context
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.AutoActionEntity

/**
 * Root 模式控制器 + 自动杀毒执行器。
 *
 * 开关全部存于 settings(明文偏好,非敏感);probeRoot 触发 su 授权
 * (首次由管理器弹出授权框),成功后进入 Root 模式。
 *
 * 自动杀毒(自动处置)仅在对应开关开启时执行:
 * - 自动禁用恶意模块(touch disable,各框架通用机制)
 * - 自动删除恶意 su 脚本(rm -f)
 * - 自动卸载恶意应用(pm uninstall --user 0)
 * 每次处置都写入 auto_actions 审计表并发通知。
 */
object RootGuard {

    private const val KEY_ROOT_MODE = "root_mode"
    private const val KEY_AUTO_DISINFECT = "auto_disinfect"
    private const val KEY_AUTO_UNINSTALL = "auto_uninstall"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isRootMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ROOT_MODE, false)

    fun setRootMode(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROOT_MODE, value).apply()
    }

    fun isAutoDisinfect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DISINFECT, false)

    fun setAutoDisinfect(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DISINFECT, value).apply()
    }

    fun isAutoUninstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UNINSTALL, false)

    fun setAutoUninstall(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UNINSTALL, value).apply()
    }

    /**
     * 探测最高权限:统一走 PrivilegeManager(缓存层级 + 能力矩阵),
     * 经 su 执行 id 确认 uid=0(首次会触发管理器授权框)。
     */
    fun probeRoot(context: Context): Boolean =
        PrivilegeManager.probe(context) == PrivLevel.ROOT

    /** 删除恶意 su 脚本 */
    fun removeScript(path: String): Boolean =
        ShellBridge.runSuChecked("rm -f " + ShellBridge.quote(path))

    /** root 卸载应用(当前用户) */
    fun uninstallApp(pkg: String): Boolean {
        val result = ShellBridge.runSuResult("pm uninstall --user 0 " + ShellBridge.quote(pkg)) ?: return false
        return result.exitCode == 0 && result.output.lowercase().contains("success")
    }

    /** 处置审计落库 */
    suspend fun record(
        context: Context,
        actionType: String,
        target: String,
        reason: String,
        success: Boolean
    ) {
        AppDatabase.get(context).autoActionDao().insert(
            AutoActionEntity(
                actionType = actionType,
                target = target,
                reason = reason,
                success = success,
                actedAt = System.currentTimeMillis()
            )
        )
    }
}
