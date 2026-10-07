package com.armorlab.securedroid.feature

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 特征库定时自动更新的后台任务(每日一次,需网络)。
 *
 * 只在用户已配置更新源(update_url + update_sha)且开关打开时执行;
 * 更新失败不无限重试 —— 下个周期会再来,失败信息可在「特征库回滚」工具的
 * 历史中观察(成功才记历史)。
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_update_enabled", false)) {
            return Result.success()
        }
        val url = prefs.getString("update_url", null)
        val sha = prefs.getString("update_sha", null)
        if (url.isNullOrBlank() || sha.isNullOrBlank()) {
            // 未配置更新源:无事可做,直接成功(保持周期任务,配置后即可生效)
            return Result.success()
        }
        return try {
            val result = com.armorlab.securedroid.vscan.FeatureUpdater.update(applicationContext, url, sha)
            if (result.ok) Result.success()
            else if (runAttemptCount < 2) Result.retry() else Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "securedroid_auto_update"
    }
}
