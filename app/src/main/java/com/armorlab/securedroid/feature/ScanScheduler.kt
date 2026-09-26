package com.armorlab.securedroid.feature

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** 每日定时自动查杀调度(AlarmManager,按偏好开关同步) */
object ScanScheduler {

    private const val REQUEST = 2001

    fun sync(context: Context) {
        val enabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("daily_scan_enabled", false)
        if (enabled) schedule(context) else cancel(context)
    }

    private fun pending(context: Context, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST,
            Intent(context, AlarmReceiver::class.java),
            flags
        )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 60_000L,
            AlarmManager.INTERVAL_DAY,
            pending(context, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
}
