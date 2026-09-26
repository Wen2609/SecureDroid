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
import com.armorlab.securedroid.data.AutoActionEntity
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.root.LockerDetector
import com.armorlab.securedroid.root.ModuleScanner
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        // Root 守护循环:即时检测(开机即扫一轮,之后周期巡检)
        scope.launch { guardLoop() }
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
                    // ROOT 自动杀毒:恶意应用自动卸载(需开启开关,且不针对自身)
                    if (trojan.isInfected && pkg != packageName &&
                        RootGuard.isAutoUninstall(applicationContext)
                    ) {
                        val ok = RootGuard.uninstallApp(pkg)
                        RootGuard.record(
                            applicationContext, "UNINSTALL_APP", pkg,
                            trojan.detections.joinToString("; ") { it.name }, ok
                        )
                        notifyAutoAction(
                            (if (ok) "已自动卸载恶意应用: " else "自动卸载失败: ") + trojan.appName
                        )
                    }
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

    /** Root 守护循环:即时检测 + 周期巡检(5 分钟) */
    private suspend fun guardLoop() {
        while (true) {
            try {
                if (RootGuard.isAutoDisinfect(this)) runRootGuardPass()
            } catch (_: Exception) {
            }
            delay(GUARD_INTERVAL_MS)
        }
    }

    private suspend fun runRootGuardPass() {
        val result = ModuleScanner.scan(this)
        for (f in result.findings) {
            val cmd = f.fixCommand ?: continue
            // 仅自动处置高危 / 严重项,轻微项只报告
            if (f.level != ThreatLevel.HIGH && f.level != ThreatLevel.CRITICAL) continue
            val ok = ShellBridge.runSu(cmd) != null
            RootGuard.record(
                this,
                if (cmd.startsWith("touch")) "DISABLE_MODULE" else "REMOVE_SCRIPT",
                f.sub,
                f.detail.take(200),
                ok
            )
            notifyAutoAction((if (ok) "已自动处置: " else "处置失败: ") + f.title)
        }

        // 锁机软件即时检测:第三方管理员 + 锁屏 API 组合(CRITICAL)自动解除
        for (f in LockerDetector.scan(this)) {
            if (f.level != ThreatLevel.CRITICAL) continue
            val cmd = f.fixCommand ?: continue
            val ok = ShellBridge.runSu(cmd) != null
            RootGuard.record(
                this, "REMOVE_LOCKER", f.sub, f.detail.take(200), ok
            )
            notifyAutoAction((if (ok) "已自动移除锁机软件: " else "移除失败: ") + f.title)
        }
    }

    private suspend fun recordAction(type: String, target: String, reason: String, ok: Boolean) {
        AppDatabase.get(applicationContext).autoActionDao().insert(
            AutoActionEntity(
                actionType = type,
                target = target,
                reason = reason,
                success = ok,
                actedAt = System.currentTimeMillis()
            )
        )
    }

    private fun notifyAutoAction(text: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(
            ("auto" + text).hashCode(),
            NotificationCompat.Builder(this, SecureGuardAppRefs.CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(getString(R.string.auto_disinfect_title))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val NOTIF_ID = 1001
        private const val GUARD_INTERVAL_MS = 5L * 60 * 1000

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
