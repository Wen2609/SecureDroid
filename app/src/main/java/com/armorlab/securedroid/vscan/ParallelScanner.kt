package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.ui.TrojanAdapter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 并行全盘查杀:4 线程池并发跑多引擎扫描,支持取消与进度回报 */
object ParallelScanner {

    fun scanAll(
        context: Context,
        workers: Int = 4,
        onProgress: (done: Int, total: Int) -> Unit,
        onEachResult: ((TrojanScanner.ScanResult) -> Unit)? = null
    ): List<TrojanAdapter.UiItem> {
        ScanControl.reset()
        val pkgs = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { !TrustStore.isTrusted(context, it) }
        val total = pkgs.size
        if (total == 0) return emptyList()

        val results = ConcurrentLinkedQueue<TrojanScanner.ScanResult>()
        val done = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(workers)
        val latch = CountDownLatch(total)
        var cancelled = false

        for (pkg in pkgs) {
            pool.execute {
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

        while (!latch.await(500, TimeUnit.MILLISECONDS)) {
            if (ScanControl.cancelled) {
                cancelled = true
                pool.shutdownNow()
                break
            }
            onProgress(done.get(), total)
        }
        if (!cancelled) pool.shutdown()
        onProgress(done.get(), total)

        val items = mutableListOf<TrojanAdapter.UiItem>()
        if (cancelled) {
            items.add(
                TrojanAdapter.UiItem(
                    "Scan.Cancelled", "查杀已被用户取消(完成 " + done.get() + "/" + total + ")",
                    "", ThreatLevel.MEDIUM, null, null
                )
            )
        }
        for (r in results) {
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
                "Parallel.Summary", "并行扫描 " + done.get() + "/" + total + " 个应用,感染 " +
                    results.count { it.isInfected } + " 个",
                "", if (results.any { it.isInfected }) ThreatLevel.HIGH else ThreatLevel.LOW, null, null
            )
        )
        return items
    }
}
