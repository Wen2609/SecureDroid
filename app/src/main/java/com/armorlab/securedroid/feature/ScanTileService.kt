package com.armorlab.securedroid.feature

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.armorlab.securedroid.MainActivity

/** 快捷设置磁贴:下拉通知栏一键进入病毒扫描 */
class ScanTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("goto", "scan")

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14 起 startActivityAndCollapse(Intent) 已弃用,改用 PendingIntent 版本
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            collapseLegacy(intent)
        }
    }

    /**
     * Android 14 以下没有 PendingIntent 版本的重载,只能调用已弃用的 Intent 版本。
     * 抑制范围严格限制在本方法内(StartActivityAndCollapseDeprecated 是 lint 的跨 API 检查,
     * 不会被 @Suppress("DEPRECATION") 覆盖)。
     */
    @Suppress("StartActivityAndCollapseDeprecated", "DEPRECATION")
    private fun collapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
