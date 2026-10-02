package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.root.ShellBridge
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

    private const val MAX_WORKERS = 6

    /**
     * 共享扫描线程池(跨扫描复用,避免每次扫描新建/销毁线程池的开销)。
     *
     * 关键修复:原实现 corePoolSize = 0 + 无界 LinkedBlockingQueue。
     * ThreadPoolExecutor.execute 只在 workerCount < corePoolSize 时新建线程,
     * 队列满才扩容到 maximumPoolSize —— 无界队列永远不会满,于是永远只有 1 个 worker,
     * "并行查杀"实际是串行。现在 core == 本次并发度,配合 allowCoreThreadTimeOut(true)
     * 实现"空闲自动回收、扫描时真并行"。
     */
    private val pool: ThreadPoolExecutor = ThreadPoolExecutor(
        MAX_WORKERS, MAX_WORKERS, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        { r -> Thread(r, "sd-scan").apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }

    private val poolLock = Any()

    /** 自适应并发度:按 CPU 核数取 2-6,小设备省电、大设备提速 */
    fun defaultWorkers(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, MAX_WORKERS)

    /**
     * 收敛本次扫描的并发度。
     * 注意:setMaximumPoolSize 不允许小于 corePoolSize,因此按方向分两步调整;
     * 无界队列下真正的并发上限就是 corePoolSize。
     */
    private fun configurePool(workers: Int) {
        val n = workers.coerceIn(2, MAX_WORKERS)
        synchronized(poolLock) {
            if (pool.maximumPoolSize == n && pool.corePoolSize == n) return
            if (n < pool.corePoolSize) {
                pool.corePoolSize = n
                pool.maximumPoolSize = n
            } else {
                pool.maximumPoolSize = n
                pool.corePoolSize = n
            }
        }
    }

    /** 供单元测试断言"并行池真的有多个核心线程" */
    internal fun poolCoreSizeForTest(): Int = pool.corePoolSize

    internal fun configurePoolForTest(workers: Int) = configurePool(workers)

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
        val trusted = TrustStore.trusted(context)
        val pkgs = PackageSnapshot.packageNames(context).filter { it !in trusted }
        val total = pkgs.size
        if (total == 0) return Outcome(emptyList(), false, 0)

        configurePool(workers)

        val results = ConcurrentLinkedQueue<TrojanScanner.Report>()
        val done = AtomicInteger(0)
        val latch = CountDownLatch(total)
        var cancelled = false

        var submitted = 0
        for (pkg in pkgs) {
            // 已取消:停止投递剩余任务,不再让线程池排空无意义的扫描
            if (ScanControl.cancelled) {
                cancelled = true
                break
            }
            pool.execute {
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
            submitted++
        }

        // 未投递的任务直接计入完成,保证进度与 latch 不多不少
        if (submitted < total) {
            val skipped = total - submitted
            done.addAndGet(skipped)
            repeat(skipped) { latch.countDown() }
        }

        while (!latch.await(400, TimeUnit.MILLISECONDS)) {
            if (ScanControl.cancelled) {
                cancelled = true
                break
            }
            onProgress(done.get(), total)
        }
        onProgress(done.get(), total)

        // 本轮命中的哈希指纹 / DEX 判定批量落盘(单次 apply,替代逐包写盘)
        try {
            HashCache.flush(context)
        } catch (_: Exception) {
        }
        try {
            DexVerdictCache.flush(context)
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
                    "am force-stop " + ShellBridge.quote(r.packageName) +
                        " ; pm clear " + ShellBridge.quote(r.packageName),
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
