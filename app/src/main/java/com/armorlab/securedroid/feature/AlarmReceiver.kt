package com.armorlab.securedroid.feature

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.armorlab.securedroid.realtime.RealtimeProtectionService

/** 定时查杀触发:启动前台服务执行一轮全盘查杀 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_scan_enabled", false)) return
        val svc = Intent(context, RealtimeProtectionService::class.java)
            .setAction(RealtimeProtectionService.ACTION_SCHEDULED_SCAN)
        ContextCompat.startForegroundService(context, svc)
    }
}
