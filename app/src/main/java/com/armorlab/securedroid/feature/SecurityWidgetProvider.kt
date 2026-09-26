package com.armorlab.securedroid.feature

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.realtime.RealtimeProtectionService

/** 桌面小部件:一键打开 / 一键启动查杀 */
class SecurityWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val open = PendingIntent.getActivity(
                context, 3001,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val scan = PendingIntent.getService(
                context, 3002,
                Intent(context, RealtimeProtectionService::class.java)
                    .setAction(RealtimeProtectionService.ACTION_SCHEDULED_SCAN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_security)
            views.setOnClickPendingIntent(R.id.widgetOpen, open)
            views.setOnClickPendingIntent(R.id.widgetScan, scan)
            manager.updateAppWidget(id, views)
        }
    }
}
