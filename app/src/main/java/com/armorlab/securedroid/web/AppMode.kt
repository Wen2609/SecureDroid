package com.armorlab.securedroid.web

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用运行模式(产品定义,名称不得修改)。
 * 初始化时必须强制选择一次;各模式只开放对应权限级的功能:
 *  - 标准模式:常规安全功能,不请求系统高级权限
 *  - 无线调试模式:额外启用无线调试 / Shell 级检测工具
 *  - 超级用户模式:启用全部 Root 级工具(自动杀毒、关键文件锁定、断网应急等)
 */
enum class AppMode(val id: String, val title: String, val desc: String) {
    STANDARD("standard", "标准模式", "常规安全功能,不请求系统高级权限"),
    WIRELESS_DEBUG("wireless", "无线调试模式", "额外启用无线调试 / Shell 级检测工具"),
    SUPERUSER("superuser", "超级用户模式", "启用全部 Root 级工具");

    companion object {
        fun fromId(id: String?): AppMode? = entries.firstOrNull { it.id == id }
    }
}

/** 模式存储:首次启动强制选择,选择后持久化。 */
object AppModeStore {
    private const val PREFS = "app_mode_prefs"
    private const val KEY_MODE = "app_mode"
    private const val KEY_SELECTED = "mode_selected"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSelected(context: Context): Boolean = prefs(context).getBoolean(KEY_SELECTED, false)

    fun current(context: Context): AppMode =
        AppMode.fromId(prefs(context).getString(KEY_MODE, null)) ?: AppMode.STANDARD

    fun save(context: Context, mode: AppMode) {
        prefs(context).edit().putString(KEY_MODE, mode.id).putBoolean(KEY_SELECTED, true).apply()
    }
}