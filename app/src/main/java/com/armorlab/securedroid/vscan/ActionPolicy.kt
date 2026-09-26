package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel

/** 处置策略:自动处置的最低威胁等级 + 隔离模式(处置改为进隔离区而非删除) */
object ActionPolicy {

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun level(context: Context): ThreatLevel = when (
        prefs(context).getString("auto_act_level", "HIGH")
    ) {
        "CRITICAL" -> ThreatLevel.CRITICAL
        "MEDIUM" -> ThreatLevel.MEDIUM
        else -> ThreatLevel.HIGH
    }

    fun setLevel(context: Context, level: ThreatLevel) {
        prefs(context).edit().putString("auto_act_level", level.name).apply()
    }

    fun autoQuarantine(context: Context): Boolean =
        prefs(context).getBoolean("auto_quarantine", false)

    fun setAutoQuarantine(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_quarantine", value).apply()
    }
}
