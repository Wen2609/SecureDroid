package com.armorlab.securedroid.web

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.databinding.DialogSetPinBinding
import com.armorlab.securedroid.lock.AppLockStore
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.realtime.BootReceiver
import com.armorlab.securedroid.realtime.RealtimeProtectionService
import com.armorlab.securedroid.root.LockerDetector
import com.armorlab.securedroid.root.ModuleScanner
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.scan.SignatureDatabase
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.trojan.RootkitDetector
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.ui.CleanerActivity
import com.armorlab.securedroid.ui.DeepScanActivity
import com.armorlab.securedroid.ui.FullAuditActivity
import com.armorlab.securedroid.ui.NetworkAuditActivity
import com.armorlab.securedroid.ui.PrivacyActivity
import com.armorlab.securedroid.ui.VirusCenterActivity
import com.armorlab.securedroid.ui.VulnerabilityActivity
import com.armorlab.securedroid.vscan.FeatureUpdater
import com.armorlab.securedroid.vscan.ParallelScanner
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebView → 原生 桥接层。
 *
 * 上传稿 HTML(assets/ui/index.html)里的全部交互经 [AndroidBridge] 对象调用到这里:
 * 同步读取(getDashboard / getLockState / getAudit / getToggles)直接返回 JSON;
 * 耗时扫描 / Root 探测改为后台线程 + [postEvent] 把进度与结果推回 JS;
 * 弹窗(检查更新 / 关于 / PIN)与深层原生页面(病毒中心 / 深度查杀 / 网络审计 / 隐私检测 / 漏洞扫描)
 * 在 UI 线程上拉起,复用原有真实实现,不造空壳。
 */
class NativeBridge(
    private val activity: MainActivity,
    private val web: WebView
) {

    private val app = activity.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val running = ConcurrentHashMap.newKeySet<String>()

    private fun settings() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 把事件推回页面:window.__sdEvent(type, <json 字面量>) */
    private fun postEvent(type: String, json: JSONObject) {
        main.post {
            try {
                web.evaluateJavascript("window.__sdEvent('$type', ${json.toString()});", null)
            } catch (_: Exception) {
            }
        }
    }

    private fun toast(res: Int) {
        main.post { Toast.makeText(activity, res, Toast.LENGTH_SHORT).show() }
    }

    private fun startActivity(cls: Class<*>) {
        main.post {
            try {
                activity.startActivity(Intent(activity, cls))
            } catch (_: Exception) {
            }
        }
    }

    /* ================================================================
       首页概览
       ================================================================ */

    @JavascriptInterface
    fun getDashboard(): String = try {
        val dao = AppDatabase.get(app).scanRecordDao()
        // 三个标量一次协程取回:countAll/lastScannedAt 是聚合查询,
        // 不再把最多 2000 行记录整表读进内存只为取 size 与最大时间戳
        val (scanned, threats, lastScan) = runBlocking {
            Triple(dao.countAll(), dao.threatCount(), dao.lastScannedAt() ?: 0L)
        }
        val prefs = settings()
        ClamAvSignatures.ensureLoaded(app)
        var protected = 0
        if (prefs.getBoolean(KEY_REALTIME, false)) protected++
        if (prefs.getBoolean(KEY_BOOT, true)) protected++
        if (prefs.getBoolean("daily_scan_enabled", false)) protected++
        if (prefs.getBoolean("sim_guard_enabled", false)) protected++
        if (RootGuard.isRootMode(app)) protected++
        JSONObject()
            .put("libOk", ClamAvSignatures.hashCount() > 0)
            .put("scanned", scanned)
            .put("threats", threats)
            .put("protected", protected)
            .put("locked", AppLockStore.lockedApps(app).size)
            .put("risky", PermissionAuditor.riskyAppCount(app))
            .put("lastScanAt", lastScan)
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("libOk", false).put("scanned", 0).put("threats", 0).put("protected", 0)
            .put("locked", 0).put("risky", 0).put("lastScanAt", 0L).toString()
    }

    /* ================================================================
       病毒扫描(全盘,并行多引擎)
       ================================================================ */

    @JavascriptInterface
    fun startVirusScan() {
        if (!running.add(SCAN_VIRUS)) return
        Thread {
            try {
                ClamAvSignatures.ensureLoaded(app)
                val outcome = ParallelScanner.scanReports(
                    app, 4,
                    onProgress = { done, total ->
                        postEvent("virusProgress", JSONObject().put("done", done).put("total", total))
                    }
                )
                val results = outcome.reports.sortedWith(
                    compareByDescending<TrojanScanner.Report> { it.isInfected }
                        .thenByDescending { it.worstLevel?.ordinal ?: -1 }
                )
                val dao = AppDatabase.get(app).scanRecordDao()
                val now = System.currentTimeMillis()
                runBlocking {
                    dao.insertAll(results.map { r ->
                        ScanRecordEntity(
                            packageName = r.packageName,
                            appName = r.appName,
                            sha256 = r.sha256,
                            threatName = r.detections.maxByOrNull { it.level.ordinal }?.name,
                            riskScore = r.riskScore,
                            scannedAt = now
                        )
                    })
                    dao.trim()
                }
                val arr = JSONArray()
                results.forEach { r ->
                    val det = JSONArray()
                    r.detections.forEach { d ->
                        det.put(JSONObject().put("engine", d.engine).put("name", d.name)
                            .put("level", d.level.name).put("detail", d.detail))
                    }
                    arr.put(JSONObject().put("name", r.appName).put("pkg", r.packageName)
                        .put("infected", r.isInfected).put("risk", r.riskScore)
                        .put("worst", r.worstLevel?.name).put("detections", det))
                }
                val infected = results.count { it.isInfected }
                val summary = "扫描完成，共 ${results.size} 项" +
                    (if (infected > 0) "，发现 $infected 个威胁" else "，未发现威胁")
                postEvent("virusDone", JSONObject().put("results", arr)
                    .put("total", results.size).put("infected", infected).put("summary", summary))
            } catch (t: Throwable) {
                postEvent("virusDone", JSONObject().put("results", JSONArray())
                    .put("total", 0).put("infected", 0)
                    .put("summary", "扫描失败：" + (t.message ?: "未知错误")))
            } finally {
                running.remove(SCAN_VIRUS)
            }
        }.start()
    }

    /* ================================================================
       木马查杀(逐包多引擎)
       ================================================================ */

    @JavascriptInterface
    fun startTrojanScan() {
        if (!running.add(SCAN_TROJAN)) return
        Thread {
            try {
                ClamAvSignatures.ensureLoaded(app)
                val pkgs = PackageSnapshot.installedPackages(app, 0)
                val items = JSONArray()
                var infected = 0
                // 进度事件节流:逐包推送会让 UI 线程执行上百次 evaluateJavascript,
                // 进度条平滑度只取决于 CSS transition,300ms 一帧绰绰有余;末包必推
                var lastPost = 0L
                pkgs.forEachIndexed { index, info ->
                    val report = TrojanScanner.scanPackage(app, info.packageName)
                    if (report.isInfected) {
                        infected++
                        items.put(reportToJson(report))
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPost >= PROGRESS_INTERVAL_MS || index == pkgs.size - 1) {
                        lastPost = now
                        postEvent("trojanProgress",
                            JSONObject().put("done", index + 1).put("total", pkgs.size))
                    }
                }
                val summary = app.getString(R.string.trojan_done, pkgs.size, infected)
                postEvent("trojanDone", JSONObject().put("items", items)
                    .put("total", pkgs.size).put("summary", summary))
            } catch (t: Throwable) {
                postEvent("trojanDone", JSONObject().put("items", JSONArray())
                    .put("total", 0).put("summary", "查杀失败：" + (t.message ?: "未知错误")))
            } finally {
                running.remove(SCAN_TROJAN)
            }
        }.start()
    }

    private fun reportToJson(r: TrojanScanner.Report): JSONObject {
        val detail = r.detections.joinToString("；") { "[" + it.engine + "] " + it.name }
        return JSONObject().put("title", r.appName).put("sub", r.packageName)
            .put("detail", detail)
            .put("level", r.worstLevel?.name)
    }

    /* ================================================================
       专项检测:Rootkit / 恶意模块 / 锁机
       ================================================================ */

    @JavascriptInterface
    fun startRootkit() {
        if (!running.add(SCAN_ROOTKIT)) return
        Thread {
            try {
                val findings = RootkitDetector.detect(app)
                val items = JSONArray()
                findings.forEach {
                    items.put(JSONObject().put("title", it.name).put("sub", it.detail)
                        .put("detail", "").put("level", it.level.name))
                }
                val summary = if (findings.isEmpty()) app.getString(R.string.rootkit_clean)
                else app.getString(R.string.rootkit_done, findings.size)
                postEvent("rootkitDone", JSONObject().put("items", items).put("summary", summary))
            } catch (t: Throwable) {
                postEvent("rootkitDone", JSONObject().put("items", JSONArray())
                    .put("summary", "检测失败：" + (t.message ?: "未知错误")))
            } finally {
                running.remove(SCAN_ROOTKIT)
            }
        }.start()
    }

    @JavascriptInterface
    fun startModuleScan() {
        if (!running.add(SCAN_MODULES)) return
        Thread {
            try {
                val result = ModuleScanner.scan(app)
                val items = JSONArray()
                result.findings.forEach {
                    items.put(JSONObject().put("title", it.title).put("sub", it.sub)
                        .put("detail", it.detail).put("level", it.level.name))
                }
                val summary = when {
                    items.length() > 0 -> app.getString(R.string.modules_done, items.length())
                    result.scannedRoots == 0 -> app.getString(R.string.modules_no_access)
                    else -> app.getString(R.string.modules_clean)
                }
                postEvent("modulesDone", JSONObject().put("items", items).put("summary", summary))
            } catch (t: Throwable) {
                postEvent("modulesDone", JSONObject().put("items", JSONArray())
                    .put("summary", "检测失败：" + (t.message ?: "未知错误")))
            } finally {
                running.remove(SCAN_MODULES)
            }
        }.start()
    }

    @JavascriptInterface
    fun startLockerScan() {
        if (!running.add(SCAN_LOCKER)) return
        Thread {
            try {
                val findings = LockerDetector.scan(app)
                val items = JSONArray()
                findings.forEach {
                    items.put(JSONObject().put("title", it.title).put("sub", it.sub)
                        .put("detail", it.detail).put("level", it.level.name))
                }
                val summary = if (findings.isEmpty()) app.getString(R.string.locker_clean)
                else app.getString(R.string.locker_done, findings.size)
                postEvent("lockerDone", JSONObject().put("items", items).put("summary", summary))
            } catch (t: Throwable) {
                postEvent("lockerDone", JSONObject().put("items", JSONArray())
                    .put("summary", "检测失败：" + (t.message ?: "未知错误")))
            } finally {
                running.remove(SCAN_LOCKER)
            }
        }.start()
    }

    /* ================================================================
       应用锁
       ================================================================ */

    @JavascriptInterface
    fun getLockState(): String = try {
        // 锁定集合只读一次,逐行内存判锁;排序在纯数据上做,
        // 不再对每个应用各读一次加密存储、再逐个反查 JSONObject 字符串排序
        val locked = AppLockStore.lockedApps(app)
        val entries = PackageSnapshot.installedApplications(app, 0)
            .asSequence()
            .filter { it.packageName != app.packageName }
            .map { Triple(PackageSnapshot.label(app, it), it.packageName, it.packageName in locked) }
            .sortedWith(compareBy({ it.first.lowercase() }, { it.second }))
            .toList()
        val apps = JSONArray()
        entries.forEach { (name, pkg, isLocked) ->
            apps.put(JSONObject().put("name", name).put("pkg", pkg).put("locked", isLocked))
        }
        JSONObject()
            .put("hasPin", AppLockStore.hasPin(app))
            .put("decoy", AppLockStore.isDecoyEnabled(app))
            .put("accessibility", isAccessibilityEnabled())
            .put("apps", apps)
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("hasPin", false).put("decoy", false)
            .put("accessibility", false).put("apps", JSONArray()).toString()
    }

    @JavascriptInterface
    fun setLocked(pkg: String, locked: Boolean) {
        try {
            AppLockStore.setLocked(app, pkg, locked)
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun toggleDecoy(on: Boolean) {
        try {
            AppLockStore.setDecoyEnabled(app, on)
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun showPinDialog() {
        main.post {
            try {
                val dialogBinding = DialogSetPinBinding.inflate(activity.layoutInflater)
                val dialog = AlertDialog.Builder(activity, R.style.Theme_SecureDroid_Dialog_Alert)
                    .setView(dialogBinding.root)
                    .create()
                dialogBinding.btnPinCancel.setOnClickListener { dialog.dismiss() }
                dialogBinding.btnPinSave.setOnClickListener {
                    val pin = dialogBinding.etPin.text?.toString() ?: ""
                    val confirm = dialogBinding.etPinConfirm.text?.toString() ?: ""
                    if (pin.length == 4 && pin == confirm) {
                        val saved = AppLockStore.setPin(activity, pin)
                        toast(if (saved) R.string.lock_pin_saved else R.string.lock_pin_store_failed)
                        postEvent("lockStateChanged", JSONObject())
                        dialog.dismiss()
                    } else {
                        toast(R.string.lock_pin_mismatch)
                    }
                }
                dialog.show()
            } catch (_: Exception) {
            }
        }
    }

    @JavascriptInterface
    fun openAccessibility() {
        main.post {
            try {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val services = Settings.Secure.getString(
            activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.contains(activity.packageName)
    }

    /* ================================================================
       权限审计
       ================================================================ */

    @JavascriptInterface
    fun getAudit(): String = try {
        val items = PermissionAuditor.audit(app)
        val risky = items.count { it.score >= 40 && it.packageName != app.packageName }
        val arr = JSONArray()
        items.forEach {
            val perms = JSONArray()
            it.risky.forEach { p -> perms.put(p) }
            arr.put(JSONObject().put("name", it.appName).put("pkg", it.packageName)
                .put("score", it.score).put("level", it.level.name).put("perms", perms))
        }
        JSONObject()
            .put("count", risky)
            .put("summary", if (risky > 0)
                "发现 $risky 个应用存在高风险权限组合，建议逐项审查。"
            else
                "未发现高风险权限应用。")
            .put("items", arr)
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("count", 0).put("summary", "审计失败：" + (t.message ?: "")).put("items", JSONArray()).toString()
    }

    /* ================================================================
       工具箱:防护开关
       ================================================================ */

    @JavascriptInterface
    fun getToggles(): String = try {
        val prefs = settings()
        val rootMode = RootGuard.isRootMode(app)
        JSONObject()
            .put("realtime", prefs.getBoolean(KEY_REALTIME, false))
            .put("boot", prefs.getBoolean(KEY_BOOT, true))
            .put("rootMode", rootMode)
            .put("rootSub", if (rootMode) app.getString(R.string.root_mode_on)
                else app.getString(R.string.root_mode_off))
            .toString()
    } catch (t: Throwable) {
        JSONObject().put("realtime", false).put("boot", true)
            .put("rootMode", false).put("rootSub", "").toString()
    }

    @JavascriptInterface
    fun toggleRealtime(on: Boolean) {
        try {
            if (on) RealtimeProtectionService.start(app) else RealtimeProtectionService.stop(app)
            settings().edit().putBoolean(KEY_REALTIME, on).apply()
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun toggleBoot(on: Boolean) {
        try {
            settings().edit().putBoolean(KEY_BOOT, on).apply()
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            app.packageManager.setComponentEnabledSetting(
                ComponentName(app, BootReceiver::class.java), state, PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
        }
    }

    /** Root 模式:开启前先探测 su,失败把开关状态回推给页面 */
    @JavascriptInterface
    fun toggleRoot(on: Boolean) {
        Thread {
            try {
                if (on) {
                    val ok = RootGuard.probeRoot(app)
                    if (ok) RootGuard.setRootMode(app, true)
                    toast(if (ok) R.string.toast_root_enabled else R.string.toast_need_root)
                } else {
                    RootGuard.setRootMode(app, false)
                }
                postRootState()
            } catch (t: Throwable) {
                postRootState()
            }
        }.start()
    }

    private fun postRootState() {
        val rootMode = RootGuard.isRootMode(app)
        postEvent("rootState", JSONObject()
            .put("rootMode", rootMode)
            .put("rootSub", if (rootMode) app.getString(R.string.root_mode_on)
                else app.getString(R.string.root_mode_off)))
    }

    /* ================================================================
       安全设置:检查更新 / 关于(复用原生弹窗实现)
       ================================================================ */

    @JavascriptInterface
    fun checkUpdate() {
        // 签名库首次加载要读盘,先在后台线程加载完再上主线程建弹窗,避免主线程做磁盘 IO 引发 ANR
        Thread {
            try {
                ClamAvSignatures.ensureLoaded(app)
            } catch (_: Exception) {
            }
            main.post {
                try {
                    val ctx = activity
                    val prefs = settings()
                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 8, 48, 0)
                    }
                    val status = ctx.getString(
                        R.string.update_status_fmt,
                        SignatureDatabase.size(),
                        ClamAvSignatures.hashCount(),
                        ClamAvSignatures.byteCount()
                    )
                    val tvStatus = TextView(ctx).apply {
                        text = status
                        setTextColor(ContextCompat.getColor(ctx, R.color.c_muted_foreground))
                        setTextAppearance(R.style.TextAppearance_SecureDroid_Caption)
                        setPadding(0, 0, 0, 16)
                    }
                    val etUrl = EditText(ctx).apply { hint = ctx.getString(R.string.vc_update_url) }
                    val etSha = EditText(ctx).apply { hint = ctx.getString(R.string.vc_update_sha) }
                    etUrl.setText(prefs.getString("update_url", ""))
                    etSha.setText(prefs.getString("update_sha", ""))
                    container.addView(tvStatus)
                    container.addView(etUrl)
                    container.addView(etSha)
                    AlertDialog.Builder(ctx, R.style.Theme_SecureDroid_Dialog_Alert)
                        .setTitle(R.string.settings_update)
                        .setView(container)
                        .setPositiveButton(R.string.vc_update_btn) { _, _ ->
                            val url = etUrl.text.toString().trim()
                            val sha = etSha.text.toString().trim()
                            if (url.isEmpty()) {
                                toast(R.string.update_need_url)
                                return@setPositiveButton
                            }
                            prefs.edit()
                                .putString("update_url", url)
                                .putString("update_sha", sha)
                                .apply()
                            toast(R.string.update_checking)
                            Thread {
                                val r = FeatureUpdater.update(ctx, url, sha.ifEmpty { null })
                                main.post {
                                    try {
                                        AlertDialog.Builder(ctx, R.style.Theme_SecureDroid_Dialog_Alert)
                                            .setTitle(R.string.settings_update)
                                            .setMessage(r.message)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                    } catch (_: Exception) {
                                    }
                                }
                            }.start()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    @JavascriptInterface
    fun showAbout() {
        main.post {
            try {
                val ctx = activity
                val pkg = try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                } catch (_: Exception) {
                    null
                }
                val version = pkg?.versionName ?: "?"
                val code = if (pkg != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode
                    else @Suppress("DEPRECATION") pkg.versionCode.toLong()
                } else {
                    0L
                }
                val message = ctx.getString(R.string.about_version_fmt, version, code) + "\n\n" +
                    ctx.getString(R.string.about_desc) + "\n" + ctx.getString(R.string.about_license)
                AlertDialog.Builder(ctx, R.style.Theme_SecureDroid_Dialog_Alert)
                    .setTitle(R.string.about_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (_: Exception) {
            }
        }
    }

    /* ================================================================
       深层工具入口(原生 Activity)
       ================================================================ */

    @JavascriptInterface
    fun openDeepScan() = startActivity(DeepScanActivity::class.java)

    @JavascriptInterface
    fun openVirusCenter() = startActivity(VirusCenterActivity::class.java)

    @JavascriptInterface
    fun openNetworkAudit() = startActivity(NetworkAuditActivity::class.java)

    @JavascriptInterface
    fun openPrivacy() = startActivity(PrivacyActivity::class.java)

    @JavascriptInterface
    fun openVulnerability() = startActivity(VulnerabilityActivity::class.java)

    @JavascriptInterface
    fun openFullAudit() = startActivity(FullAuditActivity::class.java)

    @JavascriptInterface
    fun openCleaner() = startActivity(CleanerActivity::class.java)

    /** 从扫描 / 审计结果行跳到指定应用的应用详情页(可改权限 / 卸载) */
    @JavascriptInterface
    fun openAppSettings(packageName: String) {
        if (packageName.isBlank()) return
        main.post {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    /** 轻触感反馈(开关 / 扫描 / 导航等关键交互),失败静默 */
    @JavascriptInterface
    fun haptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val manager =
                    activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= 29) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18)
            }
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val KEY_REALTIME = "realtime_enabled"
        const val KEY_BOOT = "boot_enabled"
        const val SCAN_VIRUS = "virus"
        const val SCAN_TROJAN = "trojan"
        const val SCAN_ROOTKIT = "rootkit"
        const val SCAN_MODULES = "modules"
        const val SCAN_LOCKER = "locker"

        /** 木马查杀进度事件最小间隔(毫秒) */
        const val PROGRESS_INTERVAL_MS = 300L
    }
}
