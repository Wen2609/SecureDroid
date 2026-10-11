package com.armorlab.securedroid.web.handlers

import android.content.Context
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
 * 扫描会话:病毒查杀(多引擎并行,唯一的全盘查杀入口)与专项检测的启动、进度推送与取消。
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

    /* ---------------- 病毒查杀(唯一查杀入口:应用多引擎 + Rootkit/模块/锁机 专项一并执行) ---------------- */

    suspend fun startVirusScan() {
        if (!ScanSessions.start("virus")) return
        try {
            withContext(Dispatchers.Default) {
                ClamAvSignatures.ensureLoaded(app)
                // 阶段一:全盘多引擎应用查杀
                val outcome = ParallelScanner.scanReports(
                    app, "virus", 4,
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

                // 阶段二:专项检测并入同一份结果(用户取消则跳过剩余阶段)
                val special = JSONArray()
                if (!ScanControl.isCancelled("virus")) {
                    RootkitDetector.detect(app).forEach {
                        special.put(JSONObject().put("title", it.name).put("sub", it.detail)
                            .put("level", it.level.name))
                    }
                }
                if (!ScanControl.isCancelled("virus")) {
                    ModuleScanner.scan(app).findings.forEach {
                        special.put(JSONObject().put("title", it.title).put("sub", it.sub)
                            .put("detail", it.detail).put("level", it.level.name))
                    }
                }
                if (!ScanControl.isCancelled("virus")) {
                    LockerDetector.scan(app).forEach {
                        special.put(JSONObject().put("title", it.title).put("sub", it.sub)
                            .put("detail", it.detail).put("level", it.level.name))
                    }
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
                val summary = "查杀完成：应用 " + results.size + " 项，感染 " + infected +
                    (if (special.length() > 0) "；专项发现 " + special.length() + " 项" else "；专项未发现风险") +
                    (if (outcome.cancelled) "(已取消，仅含已扫描部分)" else "")
                sink.sendEvent("virusDone", JSONObject().put("results", arr)
                    .put("special", special)
                    .put("total", results.size).put("infected", infected).put("summary", summary).toString())
            }
        } catch (t: Throwable) {
            sink.sendEvent("virusDone", JSONObject().put("results", JSONArray())
                .put("special", JSONArray())
                .put("total", 0).put("infected", 0)
                .put("summary", "查杀失败：" + (t.message ?: "未知错误")).toString())
        } finally {
            ScanSessions.finish("virus")
        }
    }

    /* ---------------- 取消与状态重放 ---------------- */

    fun cancelScan() {
        ScanControl.requestCancelAll()
    }

    /** 新页面上线时重放进行中的扫描(virus 有进度 UI,专项检测有按钮置灰态) */
    suspend fun scanState(): String {
        val arr = JSONArray()
        ScanSessions.snapshot().forEach { (kind, done, total) ->
            arr.put(JSONObject().put("action", kind).put("done", done).put("total", total))
        }
        return JSONObject().put("active", arr).toString()
    }
}
