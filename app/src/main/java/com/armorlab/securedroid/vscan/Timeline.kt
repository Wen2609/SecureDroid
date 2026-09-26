package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 查杀历史时间线:扫描记录按日期分组,回溯每天的扫描量与威胁数 */
object Timeline {

    suspend fun items(context: Context): List<TrojanAdapter.UiItem> {
        val records = AppDatabase.get(context).scanRecordDao().getAll()
        if (records.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("Timeline.Empty", "暂无扫描记录", "", ThreatLevel.LOW, null, null))
        }
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val grouped = records.groupBy { dayFmt.format(Date(it.scannedAt)) }
        return grouped.entries.sortedByDescending { it.key }.take(14).map { (day, list) ->
            val threats = list.count { it.threatName != null }
            TrojanAdapter.UiItem(
                "Timeline · " + day,
                "扫描 " + list.size + " 次 · 威胁 " + threats + " 个",
                list.filter { it.threatName != null }
                    .joinToString(", ") { it.appName + "(" + it.threatName + ")" }
                    .take(300).ifEmpty { "无威胁" },
                if (threats > 0) ThreatLevel.HIGH else ThreatLevel.LOW,
                null, null
            )
        }
    }
}
