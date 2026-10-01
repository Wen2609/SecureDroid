package com.armorlab.securedroid.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("realtime_enabled", false)) return
        // 开机自启受系统前台服务策略约束(Android 15 起部分类型禁止从
        // BOOT_COMPLETED 启动)。启动失败必须吞掉异常,否则开机会直接崩溃。
        try {
            RealtimeProtectionService.start(context)
        } catch (_: Exception) {
        }
    }
}
