package com.armorlab.securedroid.feature

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 每日定时自动查杀调度。
 *
 * 使用 WorkManager 周期任务(24 小时):系统级可靠调度,支持电量约束,
 * 且不触碰 Android 12+ 的前台服务后台启动限制。
 * 通过存量策略 UPDATE 保证同一任务唯一,避免重复排队。
 */
object ScanScheduler {

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "daily_scan_enabled"

    fun sync(context: Context) {
        val enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyScanWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    // 低电量时不唤醒,避免查杀本身加速耗电
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyScanWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DailyScanWorker.UNIQUE_NAME)
    }
}
