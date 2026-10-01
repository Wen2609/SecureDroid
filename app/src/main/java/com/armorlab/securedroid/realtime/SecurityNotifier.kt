package com.armorlab.securedroid.realtime

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.armorlab.securedroid.R

/**
 * 安全告警通知统一出口。
 *
 * 前台服务、WorkManager 定时查杀等不同触发源共用同一套通知与去重逻辑,
 * 避免多处重复实现导致刷屏或行为不一致。
 */
object SecurityNotifier {

    /** 相同文本在该窗口内只提示一次,防止守护巡检刷屏 */
    private const val DEDUPE_WINDOW_MS = 60_000L

    private var lastText = ""
    private var lastAt = 0L

    /**
     * 发送安全告警通知。
     * @return true 表示实际发送;false 表示被去重或通知服务不可用
     */
    @Synchronized
    fun alert(context: Context, text: String, title: String? = null): Boolean {
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastAt < DEDUPE_WINDOW_MS) return false
        lastText = text
        lastAt = now

        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        val builder = NotificationCompat.Builder(context, SecureGuardAppRefs.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title ?: context.getString(R.string.auto_disinfect_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        return try {
            nm.notify(("auto" + text).hashCode(), builder.build())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 测试与切换场景下重置去重状态 */
    @Synchronized
    fun resetDedupe() {
        lastText = ""
        lastAt = 0L
    }
}
