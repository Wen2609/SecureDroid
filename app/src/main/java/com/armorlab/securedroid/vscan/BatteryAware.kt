package com.armorlab.securedroid.vscan

import android.content.Context
import android.os.BatteryManager

/** 省电模式:低电量且未充电时跳过重型扫描(哈希/CPU 密集) */
object BatteryAware {

    fun eco(context: Context): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return false
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1..19 && !bm.isCharging
    }
}
