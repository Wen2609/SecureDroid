package com.armorlab.securedroid.vscan

import android.app.admin.DevicePolicyManager
import android.content.Context

/** 设备管理员快照:记录已激活管理员集合,守护循环对比新激活即告警 */
object AdminSnapshot {

    private const val KEY = "admin_snapshot"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun current(context: Context): Set<String> = try {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm?.activeAdmins
            ?.mapNotNull { it?.flattenToShortString() }
            ?.toSet() ?: emptySet()
    } catch (_: Exception) { emptySet() }

    fun hasSnapshot(context: Context): Boolean =
        prefs(context).getStringSet(KEY, null) != null

    /** 对比并更新快照,返回新激活的管理员 */
    fun diffAndStore(context: Context): List<String> {
        val cur = current(context)
        val prev = prefs(context).getStringSet(KEY, null) ?: emptySet()
        prefs(context).edit().putStringSet(KEY, cur).apply()
        return (cur - prev).sorted()
    }
}
