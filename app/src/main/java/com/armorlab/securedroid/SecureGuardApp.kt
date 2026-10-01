package com.armorlab.securedroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import com.armorlab.securedroid.feature.ScanScheduler
import com.armorlab.securedroid.realtime.SecureGuardAppRefs

/**
 * Application 入口。
 *
 * 实现 [Configuration.Provider] 以启用 WorkManager **按需初始化**:
 * 默认的 androidx.startup 初始化器已在清单中移除,改由本类提供配置,
 * 首次调用 WorkManager.getInstance() 时同步完成初始化。这样避免了
 * 初始化器缺失/被裁剪时 getInstance() 抛 IllegalStateException 导致**启动即崩溃**。
 */
class SecureGuardApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                SecureGuardAppRefs.CHANNEL_REALTIME,
                getString(R.string.channel_realtime_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                SecureGuardAppRefs.CHANNEL_ALERT,
                getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        ScanScheduler.sync(this)
    }
}
