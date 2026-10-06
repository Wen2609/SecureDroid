package com.armorlab.securedroid.web.handlers

import android.content.Context
import android.os.SystemClock
import com.armorlab.securedroid.R
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.data.ScanRecordEntity
import com.armorlab.securedroid.root.LockerDetector
import com.armorlab.securedroid.root.ModuleScanner
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.trojan.RootkitDetector
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.vscan.ParallelScanner
import com.armorlab.securedroid.vscan.ScanControl
import com.armorlab.securedroid.web.ScanSessions
import com.armorlab.securedroid.web.WebEventSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 扫描会话:病毒/木马/专项检测的启动、进度推送与取消。
 *
 * 会话状态在进程级 [ScanSessions],本 handler 随 Activity 重建而重建,
 * 但"哪些扫描在跑"只有一份真相;新页面经 getScanState 重放进度。
 * 全部函数挂起执行于应用级协程,不再持有裸 Thread(旧实现随 Activity 死亡丢单)。
 */
class ScanHandler(private val app: Context, private val sink: WebEventSink) {

    private fun progressEvent(kind: String, done: Int, total: Int): String =
        JSONObject().put("done", done).put("total", total).toString()

    private fun emitProgress(kind: String, event: String, done: Int, total: Int) {
        ScanSessions.updateProgress(kind, done, total)
        sink.sendEvent(event, progressEvent(kind, done, total))
    }

    /* ---------------- 全盘病毒扫描(并行多引擎) ---------------- */

    suspend fun startVirusScan() {
        if (!ScanSessions.start("virus")) return
        try {
            withContext(Dispatchers.Default) {
                ClamAvSignatures.ensureLoaded(app)
                val outcome = ParallelScanner.scanReports(
                    app, 4,
                    onProgress = { done, total ->
                        emitProgress("virus", "virusProgress", done, total)
                    }
                )
                val results = outcome.reports.sortedWith(
                    compareByDescending<TrojanScanner.Report> { it.isInfected }
                        .thenByDescending { it.worstLevel?.ordinal ?: -1 }
                )
                val dao = AppDatabase.get(app).scanRecordDao()
                val now = System.currentTimeMillis()
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
                    (if (infected > 0) "，发现 $infected 个威胁" else "，未发现威胁") +
                    (if (outcome.cancelled) "(已取消，仅含已扫描部分)" else "")
                sink.sendEvent("virusDone", JSONObject().put("results", arr)
                    .put("total", results.size).put("infected", infected).put("summary", summary).toString())
            }
        } catch (t: Throwable) {
            sink.sendEvent("virusDone", JSONObject().put("results", JSONArray())
                .put("total", 0).put("infected", 0)
                .put("summary", "扫描失败：" + (t.message ?: "未知错误")).toString())
        } finally {
            ScanSessions.finish("virus")
        }
    }

    /* ---------------- 木马查杀(逐包多引擎,支持取消) ---------------- */

    suspend fun startTrojanScan() {
        if (!ScanSessions.start("trojan")) return
        ScanControl.reset()
        try {
            withContext(Dispatchers.Default) {
                ClamAvSignatures.ensureLoaded(app)
                val pkgs = PackageSnapshot.installedPackages(app, 0)
                val items = JSONArray()
                var infected = 0
                var scanned = 0
                // 进度事件节流:逐包推送会让 UI 线程执行上百次 evaluateJavascript,
                // 进度条平滑度只取决于 CSS transition,300ms 一帧绰绰有余
                var lastPost = 0L
                for (info in pkgs) {
                    if (ScanControl.cancelled) break
                    val report = TrojanScanner.scanPackage(app, info.packageName)
                    scanned++
                    if (report.isInfected) {
                        infected++
                        items.put(reportToJson(report))
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPost >= PROGRESS_INTERVAL_MS) {
                        lastPost = now
                        emitProgress("trojan", "trojanProgress", scanned, pkgs.size)
                    }
                }
                val cancelled = ScanControl.cancelled && scanned < pkgs.size
                val summary = when {
                    cancelled -> app.getString(R.string.trojan_done, scanned, infected) +
                        "(已取消，仅含已扫描部分)"
                    else -> app.getString(R.string.trojan_done, pkgs.size, infected)
                }
                emitProgress("trojan", "trojanProgress", scanned, pkgs.size)
                sink.sendEvent("trojanDone", JSONObject().put("items", items)
                    .put("total", pkgs.size).put("summary", summary).toString())
            }
        } catch (t: Throwable) {
            sink.sendEvent("trojanDone", JSONObject().put("items", JSONArray())
                .put("total", 0).put("summary", "查杀失败：" + (t.message ?: "未知错误")).toString())
        } finally {
            ScanSessions.finish("trojan")
        }
    }

    private fun reportToJson(r: TrojanScanner.Report): JSONObject {
        val detail = r.detections.joinToString("；") { "[" + it.engine + "] " + it.name }
        return JSONObject().put("title", r.appName).put("sub", r.packageName)
            .put("detail", detail)
            .put("level", r.worstLevel?.name)
    }

    /* ---------------- 专项检测:Rootkit / 恶意模块 / 锁机 ---------------- */

    suspend fun startRootkitScan() {
        if (!ScanSessions.start("rootkit")) return
        try {
            withContext(Dispatchers.Default) {
                val findings = RootkitDetector.detect(app)
                val items = JSONArray()
                findings.forEach {
                    items.put(JSONObject().put("title", it.name).put("sub", it.detail)
                        .put("detail", "").put("level", it.level.name))
                }
                val summary = if (findings.isEmpty()) app.getString(R.string.rootkit_clean)
                else app.getString(R.string.rootkit_done, findings.size)
                sink.sendEvent("rootkitDone", JSONObject().put("items", items).put("summary", summary).toString())
            }
        } catch (t: Throwable) {
            sink.sendEvent("rootkitDone", JSONObject().put("items", JSONArray())
                .put("summary", "检测失败：" + (t.message ?: "未知错误")).toString())
        } finally {
            ScanSessions.finish("rootkit")
        }
    }

    suspend fun startModuleScan() {
        if (!ScanSessions.start("modules")) return
        try {
            withContext(Dispatchers.Default) {
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
                sink.sendEvent("modulesDone", JSONObject().put("items", items).put("summary", summary).toString())
            }
        } catch (t: Throwable) {
            sink.sendEvent("modulesDone", JSONObject().put("items", JSONArray())
                .put("summary", "检测失败：" + (t.message ?: "未知错误")).toString())
        } finally {
            ScanSessions.finish("modules")
        }
    }

    suspend fun startLockerScan() {
        if (!ScanSessions.start("locker")) return
        try {
            withContext(Dispatchers.Default) {
                val findings = LockerDetector.scan(app)
                val items = JSONArray()
                findings.forEach {
                    items.put(JSONObject().put("title", it.title).put("sub", it.sub)
                        .put("detail", it.detail).put("level", it.level.name))
                }
                val summary = if (findings.isEmpty()) app.getString(R.string.locker_clean)
                else app.getString(R.string.locker_done, findings.size)
                sink.sendEvent("lockerDone", JSONObject().put("items", items).put("summary", summary).toString())
            }
        } catch (t: Throwable) {
            sink.sendEvent("lockerDone", JSONObject().put("items", JSONArray())
                .put("summary", "检测失败：" + (t.message ?: "未知错误")).toString())
        } finally {
            ScanSessions.finish("locker")
        }
    }

    /* ---------------- 取消与状态重放 ---------------- */

    fun cancelScan() {
        ScanControl.requestCancel()
    }

    /** 新页面上线时重放进行中的扫描(virus/trojan 有进度 UI) */
    suspend fun scanState(): String {
        val arr = JSONArray()
        ScanSessions.snapshot().forEach { (kind, done, total) ->
            arr.put(JSONObject().put("action", kind).put("done", done).put("total", total))
        }
        return JSONObject().put("active", arr).toString()
    }

    companion object {
        /** 木马查杀进度事件最小间隔(毫秒) */
        const val PROGRESS_INTERVAL_MS = 300L
    }
}
