package com.armorlab.securedroid.feature

import android.content.Intent
import android.service.quicksettings.TileService
import com.armorlab.securedroid.MainActivity

/** 快捷设置磁贴:下拉通知栏一键进入病毒扫描 */
class ScanTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("goto", "scan")
        startActivityAndCollapse(intent)
    }
}
