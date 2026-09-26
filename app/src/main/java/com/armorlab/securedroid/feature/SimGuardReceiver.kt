package com.armorlab.securedroid.feature

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/** SIM 卡变更防盗告警:对比运营商 MCC/MNC,变更即发高优先级通知(无需敏感权限) */
class SimGuardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.SIM_STATE_CHANGED") return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("sim_guard_enabled", false)) return

        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        val operator = try { tm.simOperator ?: "" } catch (_: Exception) { "" }
        if (operator.isEmpty()) return

        val last = prefs.getString("last_sim_operator", null)
        if (last != null && last != operator) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm?.notify(
                9001,
                androidx.core.app.NotificationCompat.Builder(context, "threat_alert")
                    .setSmallIcon(com.armorlab.securedroid.R.drawable.ic_shield)
                    .setContentTitle("SIM 卡已变更!")
                    .setContentText("运营商 " + last + " → " + operator + "。若非本人操作,设备可能被盗,请尽快挂失与远程处置。")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            )
        }
        prefs.edit().putString("last_sim_operator", operator).apply()
    }
}
