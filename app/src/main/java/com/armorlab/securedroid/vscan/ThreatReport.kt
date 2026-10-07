package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.core.TimeFmt
import android.content.Context
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter
import kotlinx.coroutines.flow.first

/** 威胁报告导出 + 查杀统计仪表(数据源:Room 扫描记录 / 自动处置审计) */
object ThreatReport {

    // 不缓存 Locale:用户在系统里切换语言后,旧的 Locale 会让时间格式停留在旧语言
    private fun fmt(ms: Long = System.currentTimeMillis()): String = TimeFmt.dateSecond(ms)

    /** #17 统计仪表 */
    suspend fun stats(context: Context): List<TrojanAdapter.UiItem> {
        val db = AppDatabase.get(context)
        val records = db.scanRecordDao().getAll()
        val actions = db.autoActionDao().observeRecent().first()
        val infectedPkgs = records.filter { it.threatName != null }.map { it.packageName }.toSet()
        return listOf(
            TrojanAdapter.UiItem(
                "Stats.Records", "累计扫描记录 " + records.size + " 条",
                "最近一次: " + (records.firstOrNull()?.let { fmt(it.scannedAt) } ?: "无"),
                ThreatLevel.LOW, null, null
            ),
            TrojanAdapter.UiItem(
                "Stats.InfectedApps", "发现过威胁的应用 " + infectedPkgs.size + " 个",
                if (infectedPkgs.isEmpty()) "" else infectedPkgs.joinToString(", ").take(300),
                if (infectedPkgs.isEmpty()) ThreatLevel.LOW else ThreatLevel.HIGH, null, null
            ),
            TrojanAdapter.UiItem(
                "Stats.AutoActions", "自动处置:成功 " + actions.count { it.success } +
                    " / 失败 " + actions.count { !it.success },
                "", ThreatLevel.LOW, null, null
            ),
            TrojanAdapter.UiItem(
                "Stats.Signatures", "特征库:内置 " + com.armorlab.securedroid.scan.SignatureDatabase.size() +
                    " 条 + ClamAV " + (com.armorlab.securedroid.trojan.ClamAvSignatures.hashCount() +
                    com.armorlab.securedroid.trojan.ClamAvSignatures.md5Count() +
                    com.armorlab.securedroid.trojan.ClamAvSignatures.byteCount()) + " 条",
                "", ThreatLevel.LOW, null, null
            )
        )
    }

    /** #16 全量威胁报告文本 */
    suspend fun build(context: Context): String {
        val db = AppDatabase.get(context)
        val records = db.scanRecordDao().getAll().filter { it.threatName != null }
        val actions = db.autoActionDao().observeRecent().first()
        val sb = StringBuilder()
        sb.append("安卫安全助手 — 威胁情报报告\n")
        sb.append("导出时间: ").append(fmt()).append("\n\n")
        sb.append("== 威胁记录 (").append(records.size).append(") ==\n")
        for (r in records) {
            sb.append(fmt(r.scannedAt))
                .append(" | ").append(r.appName).append("(").append(r.packageName).append(")")
                .append(" | ").append(r.threatName)
                .append(" | 风险分 ").append(r.riskScore).append("\n")
        }
        sb.append("\n== 自动处置审计 (").append(actions.size).append(") ==\n")
        for (a in actions) {
            sb.append(fmt(a.actedAt))
                .append(" | ").append(a.actionType)
                .append(" | ").append(a.target)
                .append(" | ").append(if (a.success) "成功" else "失败")
                .append("\n")
        }
        if (records.isEmpty() && actions.isEmpty()) {
            sb.append("(当前无威胁记录,设备状态良好)\n")
        }
        return sb.toString()
    }
}
