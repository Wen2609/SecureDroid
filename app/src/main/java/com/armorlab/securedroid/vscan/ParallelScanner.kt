package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.ui.TrojanAdapter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 并行全盘查杀:共享线程池并发跑多引擎扫描,支持取消与进度回报 */
object ParallelScanner {

    /**
     * 共享扫描线程池(跨扫描复用,避免每次扫描新建/销毁线程池的开销)。
     * 核心线程 0、峰值 6:空闲自动回收,扫描时按需扩张。
     */
    private val pool: ThreadPoolExecutor = ThreadPoolExecutor(
        0, 6, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        { r -> Thread(r, "sd-scan").apply { isDaemon = true } }
    )

    /** 自适应并发度:按 CPU 核数取 2-6,小设备省电、大设备提速 */
    fun defaultWorkers(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    data class Outcome(
        val reports: List<TrojanScanner.Report>,
        val cancelled: Boolean,
        val total: Int
    )

    /** 核心扫描:返回原始报告(供 ResultDiff / FamilyClassifier 等数据层复用) */
    fun scanReports(
        context: Context,
        workers: Int = defaultWorkers(),
        onProgress: (done: Int, total: Int) -> Unit,
        onEachResult: ((TrojanScanner.Report) -> Unit)? = null
    ): Outcome {
        ScanControl.reset()
        val trusted = TrustStore.trusted(context).toHashSet()
        val pkgs = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it !in trusted }
        val total = pkgs.size
        if (total == 0) return Outcome(emptyList(), false, 0)

        // 并发上限随本次请求收敛到共享池(取较小值,避免过度并发抢占 CPU)
        pool.maximumPoolSize = workers.coerceIn(2, 6)

        val results = ConcurrentLinkedQueue<TrojanScanner.Report>()
        val done = AtomicInteger(0)
        val latch = CountDownLatch(total)
        var cancelled = false

        for (pkg in pkgs) {
            pool.execute {
                // 协作式取消:已取消时不再启动新扫描,直接计入完成
                if (ScanControl.cancelled) {
                    done.incrementAndGet()
                    latch.countDown()
                    return@execute
                }
                try {
                    val r = TrojanScanner.scanPackage(context, pkg)
                    results.add(r)
                    onEachResult?.invoke(r)
                } catch (_: Exception) {
                } finally {
                    done.incrementAndGet()
                    latch.countDown()
                }
            }
        }

        while (!latch.await(400, TimeUnit.MILLISECONDS)) {
            if (ScanControl.cancelled) {
                cancelled = true
                break
            }
            onProgress(done.get(), total)
        }
        onProgress(done.get(), total)

        // 本轮命中的哈希指纹批量落盘(单次 apply,替代逐包写盘)
        try {
            HashCache.flush(context)
        } catch (_: Exception) {
        }

        return Outcome(results.toList(), cancelled, total)
    }

    /** 报告 → UI 列表(取消提示 + 感染项 + 汇总) */
    fun toUiItems(outcome: Outcome): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()
        if (outcome.cancelled) {
            items.add(
                TrojanAdapter.UiItem(
                    "Scan.Cancelled", "查杀已被用户取消(完成 " + outcome.reports.size + "/" + outcome.total + ")",
                    "", ThreatLevel.MEDIUM, null, null
                )
            )
        }
        for (r in outcome.reports) {
            if (!r.isInfected) continue
            items.add(
                TrojanAdapter.UiItem(
                    "Parallel.Infected · " + r.appName, r.packageName,
                    r.detections.joinToString("; ") { it.engine + ":" + it.name },
                    r.worstLevel ?: ThreatLevel.HIGH,
                    "并行查杀命中,建议处理", null,
                    "am force-stop '" + r.packageName + "' ; pm clear '" + r.packageName + "'",
                    "强停清数据"
                )
            )
        }
        items.add(
            TrojanAdapter.UiItem(
                "Parallel.Summary", "并行扫描 " + outcome.reports.size + "/" + outcome.total + " 个应用,感染 " +
                    outcome.reports.count { it.isInfected } + " 个",
                "", if (outcome.reports.any { it.isInfected }) ThreatLevel.HIGH else ThreatLevel.LOW, null, null
            )
        )
        return items
    }

    /** 兼容旧调用:直接返回 UI 列表 */
    fun scanAll(
        context: Context,
        workers: Int = defaultWorkers(),
        onProgress: (done: Int, total: Int) -> Unit,
        onEachResult: ((TrojanScanner.Report) -> Unit)? = null
    ): List<TrojanAdapter.UiItem> =
        toUiItems(scanReports(context, workers, onProgress, onEachResult))
}
