package com.armorlab.securedroid.feature

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 每日定时查杀的后台工作任务。
 *
 * 为什么用 WorkManager 而不是「AlarmManager + 启动前台服务」:
 * Android 12+ 限制后台启动前台服务,闹钟触发的 startForegroundService 可能抛
 * ForegroundServiceStartNotAllowedException 导致崩溃;WorkManager 是可延迟后台
 * 工作的官方机制,不受该限制,且能跨 Doze / 重启 / 省电策略可靠执行。
 */
class DailyScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_scan_enabled", false)) {
            // 开关已关闭:直接成功退出,避免用户关闭后仍被系统拉起执行
            return Result.success()
        }
        return try {
            DailyScanRunner.run(applicationContext)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "securedroid_daily_scan"
    }
}
