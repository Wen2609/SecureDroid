package com.armorlab.securedroid.feature

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 特征库定时自动更新调度(每日一次)。
 *
 * 与 ScanScheduler 同一套约定:WorkManager 周期任务 + 存量策略 UPDATE 保证唯一;
 * 调度异常绝不向上传播(在 Application.onCreate 中被调用,失败只降级为
 * "自动更新不生效",不能让启动崩溃)。需网络 + 非低电量。
 */
object UpdateScheduler {

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "auto_update_enabled"

    fun sync(context: Context) {
        val enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context): Boolean = try {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UpdateWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        true
    } catch (t: Throwable) {
        android.util.Log.w("UpdateScheduler", "自动更新调度失败,已忽略以免影响启动", t)
        false
    }

    fun cancel(context: Context): Boolean = try {
        WorkManager.getInstance(context).cancelUniqueWork(UpdateWorker.UNIQUE_NAME)
        true
    } catch (t: Throwable) {
        android.util.Log.w("UpdateScheduler", "取消自动更新调度失败,已忽略", t)
        false
    }
}
