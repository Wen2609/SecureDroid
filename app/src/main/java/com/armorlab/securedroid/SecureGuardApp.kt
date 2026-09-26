package com.armorlab.securedroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.armorlab.securedroid.realtime.SecureGuardAppRefs

class SecureGuardApp : Application() {

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
    }
}
