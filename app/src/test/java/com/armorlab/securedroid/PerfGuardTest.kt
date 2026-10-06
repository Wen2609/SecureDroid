package com.armorlab.securedroid

import com.armorlab.securedroid.vscan.ParallelScanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 性能不变量守卫(源码级)。
 *
 * 性能优化最容易被后续改动悄悄回退:循环里又 new 一个 SimpleDateFormat、
 * 逐包又塞回 loadLabel、并行池又改回 corePoolSize = 0……
 * 这些回退不会让任何功能测试失败,所以在这里用零成本的静态断言钉住。
 */
class PerfGuardTest {

    private fun mainSources(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        candidates.firstOrNull { it.isDirectory }?.let { return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 main 源码目录")
    }

    private fun ktFiles(): List<File> =
        mainSources().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun text(rel: String): String {
        val f = File(mainSources(), rel)
        assertTrue("缺少源文件: " + rel, f.isFile)
        return f.readText()
    }

    /** 去掉注释,避免注释里的示例代码触发误报 */
    private fun codeOnly(rel: String): String =
        text(rel).replace(Regex("/\\*[\\s\\S]*?\\*/"), "").lines()
            .filterNot { it.trim().startsWith("//") }
            .joinToString("\n")

    private fun simpleName(rel: String): String = rel.substringAfterLast('/')

    @Test
    fun scanPoolReallyUsesMultipleCoreThreads() {
        ParallelScanner.configurePoolForTest(4)
        assertEquals("并行度必须作用在 corePoolSize 上(无界队列下 maximumPoolSize 形同虚设)", 4, ParallelScanner.poolCoreSizeForTest())
        ParallelScanner.configurePoolForTest(1)
        assertEquals("并发度下限为 2", 2, ParallelScanner.poolCoreSizeForTest())
        val src = text("com/armorlab/securedroid/vscan/ParallelScanner.kt")
        assertTrue("核心线程必须允许超时回收", src.contains("allowCoreThreadTimeOut(true)"))
    }

    @Test
    fun parallelScanStopsSubmittingAfterCancelAndFlushesCaches() {
        val src = codeOnly("com/armorlab/securedroid/vscan/ParallelScanner.kt")
        assertTrue("取消后必须停止投递剩余任务", src.contains("ScanControl.cancelled"))
        assertTrue("扫描结束必须批量落盘 DEX 判定缓存", src.contains("DexVerdictCache.flush"))
        assertTrue("不得再把 TrustStore 结果整集复制一份", !src.contains(".toHashSet()"))
    }

    @Test
    fun noSimpleDateFormatOutsideTimeFmt() {
        val offenders = ktFiles()
            .filter { it.readText().contains("SimpleDateFormat(") }
            .filterNot { it.path.replace('\\', '/').endsWith("core/TimeFmt.kt") }
            .map { it.name }
        assertTrue("SimpleDateFormat 必须集中在 core/TimeFmt.kt(线程安全 + 记忆化): " + offenders, offenders.isEmpty())
    }

    @Test
    fun hotPathsDoNotRecompileRegex() {
        val files = listOf(
            "com/armorlab/securedroid/feature/NetAudit.kt",
            "com/armorlab/securedroid/feature/CleanerTool.kt",
            "com/armorlab/securedroid/deep/ProcessScanner.kt",
            "com/armorlab/securedroid/deep/PartitionScanner.kt",
            "com/armorlab/securedroid/vscan/ProcessBaseline.kt",
            "com/armorlab/securedroid/vscan/Quarantine.kt",
            "com/armorlab/securedroid/vscan/NetKill.kt",
            "com/armorlab/securedroid/feature/DnsGuard.kt",
            "com/armorlab/securedroid/trojan/RootkitDetector.kt"
        )
        val bad = files.filter { codeOnly(it).contains("Regex(") }.map { simpleName(it) }
        assertTrue("以下热路径仍在循环内编译正则,应改用 core/Re.kt 的共享实例: " + bad, bad.isEmpty())
    }

    @Test
    fun packageManagerQueriesGoThroughSnapshot() {
        val allowed = "core/PackageSnapshot.kt"
        val needles = listOf("getInstalledPackages(", "getInstalledApplications(", "loadLabel(", "getPackagesForUid(")
        val root = mainSources()
        val offenders = ktFiles().filter { f ->
            val rel = f.toRelativeString(root).replace('\\', '/')
            if (rel.endsWith(allowed)) false else needles.any { codeOnly(rel).contains(it) }
        }.map { it.name }
        assertTrue("PackageManager 查询必须走 core/PackageSnapshot.kt 快照层: " + offenders, offenders.isEmpty())
    }

    @Test
    fun scannerEnginesDoNotRequeryEachPackage() {
        for (rel in listOf(
            "com/armorlab/securedroid/scan/ScannerEngine.kt",
            "com/armorlab/securedroid/trojan/TrojanScanner.kt"
        )) {
            assertTrue(
                simpleName(rel) + " 不应再逐包 getPackageInfo(应使用快照层列表里的 PackageInfo)",
                !codeOnly(rel).contains("getPackageInfo(")
            )
        }
    }

    @Test
    fun behaviorMatchingIsSinglePass() {
        assertTrue(
            "行为规则必须用 MultiPatternMatcher 单遍匹配",
            codeOnly("com/armorlab/securedroid/trojan/BehaviorRules.kt").contains("matcherFor")
        )
        assertTrue(
            "锁机检测也必须走单遍匹配",
            codeOnly("com/armorlab/securedroid/root/LockerDetector.kt").contains("lockMatcher")
        )
    }

    @Test
    fun allListAdaptersDeclareFixedSize() {
        // v1.9.9 删除旧 Fragment 死代码后,真实列表界面剩 4 个 RecyclerView:
        // DeepScanActivity / BaseListToolActivity / VirusCenterActivity(rvActions+rvList)
        val n = ktFiles().sumOf { Regex("setHasFixedSize\\(true\\)").findAll(it.readText()).count() }
        assertTrue("4 个 RecyclerView 列表都应声明 setHasFixedSize(true),当前 " + n, n >= 4)
    }

    @Test
    fun appLockEncryptedPrefsMustBeSingletonCached() {
        val src = codeOnly("com/armorlab/securedroid/lock/AppLockStore.kt")
        val creates = Regex("EncryptedSharedPreferences\\.create").findAll(src).count()
        assertEquals(
            "EncryptedSharedPreferences 只能创建一次(单例缓存):每次 create 都要做 " +
                "KeyStore 密钥派生,逐事件/逐行重建会让无障碍服务与应用锁列表付出成百上千次派生",
            1, creates
        )
        assertTrue("必须用双检锁缓存加密存储实例", src.contains("synchronized(createLock)"))
        assertTrue("创建失败必须有冷却期,避免故障环境下高频重试", src.contains("FAILED_RETRY_MS"))
    }

    @Test
    fun dashboardMustUseAggregateQueries() {
        val dao = codeOnly("com/armorlab/securedroid/data/AppDatabase.kt")
        assertTrue("ScanRecordDao 必须提供 countAll 聚合查询", dao.contains("countAll"))
        assertTrue("ScanRecordDao 必须提供 lastScannedAt 聚合查询", dao.contains("lastScannedAt"))
        val bridge = codeOnly("com/armorlab/securedroid/web/NativeBridge.kt")
        assertTrue(
            "getDashboard 不得整表拉取扫描记录(getAll 最多 2000 行实体,只为取 size 与最大时间戳)",
            !bridge.contains("dao.getAll()")
        )
    }

    @Test
    fun lockStateMustReadLockSetOnce() {
        val bridge = codeOnly("com/armorlab/securedroid/web/NativeBridge.kt")
        assertTrue(
            "getLockState 必须一次读出锁定集合并内存判锁,不得逐应用调 AppLockStore.isLocked" +
                "(每次都是一次加密存储读取)",
            !bridge.contains("AppLockStore.isLocked(")
        )
    }

    @Test
    fun trojanProgressEventsMustBeThrottled() {
        val bridge = codeOnly("com/armorlab/securedroid/web/NativeBridge.kt")
        assertTrue(
            "木马查杀进度必须按 PROGRESS_INTERVAL_MS 节流:逐包 evaluateJavascript " +
                "会让 UI 线程执行上百次 JS 注入",
            bridge.contains("PROGRESS_INTERVAL_MS") && bridge.contains("SystemClock.elapsedRealtime")
        )
    }
}
