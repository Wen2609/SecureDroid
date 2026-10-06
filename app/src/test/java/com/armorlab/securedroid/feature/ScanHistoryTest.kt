package com.armorlab.securedroid.feature

import com.armorlab.securedroid.trojan.TrojanScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 查杀历史落库回归测试(v1.7.2)。
 *
 * 背景:定时查杀(DailyScanRunner)与手动查杀(NativeBridge)曾把 sha256 写死为空串、
 * riskScore 写死为 0,于是历史列表 / 威胁报告导出里指纹与风险分全为空 —— 数据看着有,
 * 实际没有内容。本测试锁定两件事:报告对象必须携带真实值,两个写入方不得再落空值。
 */
class ScanHistoryTest {

    @Test
    fun reportCarriesFingerprintAndRiskScore() {
        val bare = TrojanScanner.Report("pkg", "app", emptyList())
        assertEquals("", bare.sha256)
        assertEquals(0, bare.riskScore)

        val filled = TrojanScanner.Report("pkg", "app", emptyList(), "a".repeat(64), 55)
        assertEquals("a".repeat(64), filled.sha256)
        assertEquals(55, filled.riskScore)
        assertFalse(filled.isInfected)
    }

    @Test
    fun scanHistoryWritersPersistRealValues() {
        for (rel in listOf(
            "com/armorlab/securedroid/feature/DailyScanRunner.kt",
            "com/armorlab/securedroid/web/NativeBridge.kt"
        )) {
            val text = src(rel)
            assertTrue("$rel 未写入真实指纹", text.contains("sha256 = r.sha256"))
            assertTrue("$rel 未写入真实风险分", text.contains("riskScore = r.riskScore"))
            assertFalse("$rel 仍在写空指纹", text.contains("sha256 = \"\""))
            assertFalse("$rel 仍在写 0 风险分", text.contains("riskScore = 0,"))
        }
    }

    @Test
    fun deadBatchRunnerIsGone() {
        val text = src("com/armorlab/securedroid/root/PrivilegeManager.kt")
        assertFalse(
            "PrivilegeManager 仍保留无调用方的 execBatch(且用已废弃的 runSu != null 判据)",
            text.contains("execBatch")
        )
        assertTrue("PrivilegeManager 应统一走 runSuResult", text.contains("ShellBridge.runSuResult("))
    }

    @Test
    fun packageNamesInFixCommandsAreQuoted() {
        for (rel in listOf(
            "com/armorlab/securedroid/vscan/ParallelScanner.kt",
            "com/armorlab/securedroid/ui/VirusCenterActivity.kt"
        )) {
            val raw = src(rel)
            assertFalse(
                "$rel 仍用裸单引号拼接包名(应为 ShellBridge.quote)",
                raw.contains("am force-stop '")
            )
            if (raw.contains("am force-stop ")) {
                assertTrue("$rel 的强停命令未走 ShellBridge.quote", raw.contains("ShellBridge.quote("))
            }
        }
    }

    private fun src(rel: String): String = File(mainRoot(), rel).readText()

    /** AGP 单测工作目录是 app/,这里兼容仓库根与 app/ 两种起点 */
    private fun mainRoot(): File {
        val direct = File("src/main/java")
        if (direct.isDirectory) return direct
        val app = File("app/src/main/java")
        if (app.isDirectory) return app
        var cur: File? = File("").absoluteFile
        while (cur != null) {
            val c = File(cur, "app/src/main/java")
            if (c.isDirectory) return c
            cur = cur.parentFile
        }
        throw IllegalStateException("找不到 main 源目录")
    }
}
