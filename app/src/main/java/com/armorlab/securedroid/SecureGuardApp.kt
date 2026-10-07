package com.armorlab.securedroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import com.armorlab.securedroid.feature.ScanScheduler
import com.armorlab.securedroid.feature.UpdateScheduler
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
        // 主界面是 WebView,提前初始化 Chromium 内核(Application 阶段完成最重的
        // provider 初始化),显著缩短 MainActivity 首屏白屏时间。
        // 创建即销毁的预热 WebView 不参与任何渲染,失败静默(个别设备 WebView 不可用)。
        try {
            android.webkit.WebView(applicationContext).destroy()
        } catch (_: Exception) {
        }
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
        UpdateScheduler.sync(this)
    }
}
