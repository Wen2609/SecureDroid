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
        if (enabled) schedule(context) else cancel(context) // 返回布尔仅为可观测性,失败不抛出
    }

    /**
     * 入队周期任务。
     * 任何调度异常都不得向上传播:本方法会在 Application.onCreate 中被调用,
     * 调度失败只应导致"定时查杀不生效",绝不能让应用启动崩溃。
     * @return true 表示入队成功
     */
    fun schedule(context: Context): Boolean = try {
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
        true
    } catch (t: Throwable) {
        android.util.Log.w("ScanScheduler", "定时查杀调度失败,已忽略以免影响启动", t)
        false
    }

    fun cancel(context: Context): Boolean = try {
        WorkManager.getInstance(context).cancelUniqueWork(DailyScanWorker.UNIQUE_NAME)
        true
    } catch (t: Throwable) {
        android.util.Log.w("ScanScheduler", "取消定时查杀失败,已忽略", t)
        false
    }
}
