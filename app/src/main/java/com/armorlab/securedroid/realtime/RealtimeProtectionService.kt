package com.armorlab.securedroid.realtime

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.armorlab.securedroid.R
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.trojan.TrojanScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台常驻服务:监控应用安装/更新,自动对新应用执行静态扫描,
 * 命中特征库时发送高优先级告警通知。
 */
class RealtimeProtectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        registerPackageMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        receiver = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, SecureGuardAppRefs.CHANNEL_REALTIME)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.realtime_title))
            .setContentText(getString(R.string.realtime_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun registerPackageMonitor() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                scope.launch {
                    val result = ScannerEngine.scanPackage(applicationContext, pkg)
                    AppDatabase.get(applicationContext).scanRecordDao().insert(
                        ScanRecordEntity(
                            packageName = result.packageName,
                            appName = result.appName,
                            sha256 = result.sha256,
                            threatName = result.threat?.name,
                            riskScore = result.permissionRiskScore,
                            scannedAt = System.currentTimeMillis()
                        )
                    )
                    if (result.isMalicious) notifyThreat(result)
                    val trojan = TrojanScanner.scanPackage(applicationContext, pkg)
                    if (trojan.isInfected) notifyTrojan(trojan)
                }
            }
        }
        ContextCompat.registerReceiver(this, receiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun notifyThreat(result: ScannerEngine.ScanResult) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val threat = result.threat ?: return
        nm.notify(
            result.packageName.hashCode(),
            NotificationCompat.Builder(this, SecureGuardAppRefs.CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(getString(R.string.threat_found_title))
                .setContentText(result.appName + " — " + threat.name)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(result.appName + " — " + threat.name + "\n" + threat.description)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notifyTrojan(report: TrojanScanner.Report) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val names = report.detections.joinToString("; ") { it.name }
        nm.notify(
            ("trojan" + report.packageName).hashCode(),
            NotificationCompat.Builder(this, SecureGuardAppRefs.CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(getString(R.string.threat_found_title))
                .setContentText(report.appName + " — " + names)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RealtimeProtectionService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RealtimeProtectionService::class.java))
        }
    }
}

/** 通知渠道 ID 集中管理 */
object SecureGuardAppRefs {
    const val CHANNEL_REALTIME = "realtime"
    const val CHANNEL_ALERT = "threat_alert"
}
