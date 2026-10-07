package com.armorlab.securedroid.feature

import android.content.Context

/** DNS 防护开关偏好(设置层 / 授权回调 / 开机恢复共用一份真值) */
object DnsGuardState {

    const val KEY = "dns_guard_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, on).apply()
    }
}
