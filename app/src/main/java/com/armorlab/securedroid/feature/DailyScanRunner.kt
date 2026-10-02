package com.armorlab.securedroid.feature

import android.content.Context
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.realtime.SecurityNotifier
import com.armorlab.securedroid.vscan.BatteryAware
import com.armorlab.securedroid.vscan.ParallelScanner
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 每日定时查杀执行体。
 *
 * 从 RealtimeProtectionService 中抽出,使前台服务(手动触发)与
 * WorkManager(系统调度)共用同一实现,行为一致、便于测试。
 */
object DailyScanRunner {

    data class Summary(val scanned: Int, val infected: Int, val skipped: Boolean)

    /**
     * 执行一轮全盘多引擎查杀并落库、汇总通知。
     * 低电量省电模式下自动跳过(返回 skipped = true)。
     */
    suspend fun run(context: Context): Summary {
        if (BatteryAware.eco(context)) {
            SecurityNotifier.alert(context, "电量低,本次定时查杀已跳过(省电模式)")
            return Summary(0, 0, true)
        }
        val dao = AppDatabase.get(context).scanRecordDao()
        val now = System.currentTimeMillis()
        val entities = ConcurrentLinkedQueue<ScanRecordEntity>()
        ParallelScanner.scanReports(context, ParallelScanner.defaultWorkers(), { _, _ -> }, { r ->
            entities.add(
                ScanRecordEntity(
                    packageName = r.packageName,
                    appName = r.appName,
                    sha256 = r.sha256,
                    threatName = r.detections.maxByOrNull { it.level.ordinal }?.name,
                    riskScore = r.riskScore,
                    scannedAt = now
                )
            )
        })
        val list = entities.toList()
        val infected = list.count { it.threatName != null }
        try {
            dao.insertAll(list)
            dao.trim()
        } catch (_: Exception) {
        }
        SecurityNotifier.alert(
            context,
            "每日查杀完成: 扫描 " + list.size + " 个应用, 发现 " + infected + " 个感染项"
        )
        return Summary(list.size, infected, false)
    }
}
