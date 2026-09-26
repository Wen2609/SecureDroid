package com.armorlab.securedroid.feature

import android.content.Context
import com.armorlab.securedroid.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 安全日志导出:扫描记录 + 自动处置审计导出为文本(用户主动分享) */
object LogExporter {

    suspend fun build(context: Context): String {
        val db = AppDatabase.get(context)
        val records = db.scanRecordDao().getAll()
        val actions = db.autoActionDao().observeRecent().first()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("安卫安全助手 — 安全日志导出\n")
        sb.append("导出时间: ").append(fmt.format(Date())).append("\n\n")
        sb.append("== 最近扫描记录 (").append(records.size).append(") ==\n")
        for (r in records.take(200)) {
            sb.append(fmt.format(Date(r.scannedAt)))
                .append(" | ").append(r.appName)
                .append(" | ").append(r.threatName ?: "安全")
                .append(" | 风险分 ").append(r.riskScore).append("\n")
        }
        sb.append("\n== 自动处置审计 (").append(actions.size).append(") ==\n")
        for (a in actions) {
            sb.append(fmt.format(Date(a.actedAt)))
                .append(" | ").append(a.actionType)
                .append(" | ").append(a.target)
                .append(" | ").append(if (a.success) "成功" else "失败")
                .append("\n")
        }
        return sb.toString()
    }
}
