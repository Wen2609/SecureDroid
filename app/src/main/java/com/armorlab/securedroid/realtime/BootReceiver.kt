package com.armorlab.securedroid.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.armorlab.securedroid.feature.ScanScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        // 上传稿"开机自启防护"开关:关闭后组件本身已被禁用,这里再做一道偏好兜底,
        // 防止应用升级重置组件状态后仍被拉起。
        if (!prefs.getBoolean("boot_enabled", true)) return
        // 每日定时查杀依赖 WorkManager,重启后重排一次,保证周期任务不丢
        ScanScheduler.sync(context)
        if (!prefs.getBoolean("realtime_enabled", false)) return
        // 开机自启受系统前台服务策略约束(Android 15 起部分类型禁止从
        // BOOT_COMPLETED 启动)。启动失败必须吞掉异常,否则开机会直接崩溃。
        try {
            RealtimeProtectionService.start(context)
        } catch (_: Exception) {
        }
    }
}
