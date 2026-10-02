package com.armorlab.securedroid.trojan

import com.armorlab.securedroid.root.LockerDetector
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.scan.TokenMatch
import com.armorlab.securedroid.vscan.ParallelScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 误报回归测试(v1.7.3)。
 *
 * 全部用例来自实测:用真实第三方 APK 语料(一加官方"备份与恢复"、OTA 包、Dute 等)
 * 复现出 7-10 条 MEDIUM+ 误报,以及本应用扫描自己时 14 条规则全中。
 * 这里把那些真实字符串画像固化成断言,防止"修好的误报"再回来。
 *
 * 四个方向:
 * 1. 真实应用画像不得判为感染(词边界 + 强特征门槛 + 分级下调);
 * 2. 子串命中不算命中(exec → execute/execSQL);
 * 3. 合成恶意样本仍必须命中(不能把检测能力一起修没了);
 * 4. 自排除 / 通用魔数 / 缓存版本等结构性防线已接线。
 */
class FalsePositiveTest {

    private fun det(name: String, level: ThreatLevel) =
        TrojanScanner.Detection("行为规则", name, level, "")

    private fun infections(hits: List<TrojanScanner.Detection>) =
        hits.filter { it.level.ordinal >= ThreatLevel.MEDIUM.ordinal }

    // ---------------- 1. 真实应用画像 ----------------

    @Test
    fun realWorldAppStringProfilesAreNotInfections() {
        val profiles = mapOf(
            "Dute-Android-v1.1.0" to listOf(
                "Ljava/net/Socket;", "exec", "Ljavax/crypto/Cipher;", "BTC",
                "MediaRecorder", "Landroid/hardware/Camera;", "xposed"
            ),
            "dute294" to listOf(
                "Ljava/net/Socket;", "exec", "Ljavax/crypto/Cipher;", "BTC",
                "addJavascriptInterface", "setJavaScriptEnabled", "xposed"
            ),
            "OnePlus-BackupRestore" to listOf(
                "sendTextMessage", "Landroid/telephony/SmsManager;", "divideMessage",
                "/system/bin/su", "/system/xbin/su", "Ljava/net/Socket;", "exec",
                "Ljavax/crypto/Cipher;", "BTC", "Ljava/net/HttpURLConnection;",
                "takePicture", "Landroid/hardware/Camera;", "setComponentEnabledSetting",
                "android.intent.category.HOME", "addJavascriptInterface",
                "setJavaScriptEnabled", "Landroid/os/SystemProperties;",
                "Lsun/misc/Unsafe;", "xposed", "substrate"
            ),
            "OnePlus-OTA-113" to listOf(
                "Ljava/net/Socket;", "exec", "Ljavax/crypto/Cipher;",
                "Ljava/net/HttpURLConnection;", "xposed"
            )
        )
        for ((app, strings) in profiles) {
            val hits = BehaviorRules.match(strings.toSet())
            val bad = infections(hits)
            assertTrue(
                "正常应用 " + app + " 不应有 MEDIUM+ 命中,实际: " + bad.map { it.name },
                bad.isEmpty()
            )
        }
    }

    @Test
    fun apiCombinationsWithoutDiscriminatingEvidenceStayHints() {
        // Socket + exec 但没有真实 shell 路径;Cipher + BTC 但没有 .locked;
        // lockNow + DevicePolicyManager 但没有 resetPassword —— 全部只能算提示。
        val hits = BehaviorRules.match(
            listOf(
                "Ljava/net/Socket;", "exec", "Ljavax/crypto/Cipher;", "BTC",
                "lockNow", "Landroid/app/admin/DevicePolicyManager;",
                "sendTextMessage", "Landroid/telephony/SmsManager;"
            ).toSet()
        )
        assertTrue("无强特征的 API 组合不得判为感染: " + infections(hits).map { it.name }, infections(hits).isEmpty())
    }

    // ---------------- 2. 词边界 ----------------

    @Test
    fun substringOccurrencesAreNotMatches() {
        assertFalse("exec 不应命中 execute", TokenMatch.occurs("Lcom/x/execute;", "exec"))
        assertFalse("exec 不应命中 execSQL", TokenMatch.occurs("execSQL(db)", "exec"))
        assertFalse("exec 不应命中 ExecutorService", TokenMatch.occurs("ExecutorService", "exec"))
        assertFalse("xposed 不应命中 xposedcheck", TokenMatch.occurs("xposedcheck", "xposed"))
        assertTrue("独立的 exec 调用必须命中", TokenMatch.occurs("Runtime.getRuntime().exec(cmd)", "exec"))
        assertTrue("整串等于模式必须命中", TokenMatch.occurs("exec", "exec"))
        // 非标识符开头/结尾的模式只检查标识符侧
        assertTrue(TokenMatch.occurs("photo.jpg.locked", ".locked"))
        assertTrue(TokenMatch.occurs("/system/bin/sh", "/system/bin/sh"))
        assertFalse("/system/bin/sh 不应命中 /system/bin/shx", TokenMatch.occurs("/system/bin/shx", "/system/bin/sh"))
        assertTrue(
            "descriptor 以 ; 结尾,右侧不检查",
            TokenMatch.occurs("Landroid/os/SystemProperties;", "Landroid/os/SystemProperties;")
        )
    }

    @Test
    fun plainSubstringCorpusProducesNoInfections() {
        val strings = setOf(
            "execute", "execSQL", "executor", "subversion", "xposedcheck",
            "getExternalFilesDir", "SocketFactory", "CipherOutputStream"
        )
        assertTrue(
            "纯子串语料不得产生任何 MEDIUM+ 判定",
            infections(BehaviorRules.match(strings)).isEmpty()
        )
    }

    // ---------------- 3. 恶意样本仍要命中 ----------------

    @Test
    fun maliciousProfilesAreStillDetected() {
        val reverseShell = listOf("Ljava/net/Socket;", "/system/bin/sh", "exec", "sh -i")
        val rs = BehaviorRules.match(reverseShell.toSet()).find { it.name == "Backdoor.ReverseShell" }
        assertTrue("反向 Shell 样本必须命中", rs != null)
        assertEquals(ThreatLevel.CRITICAL, rs!!.level)

        val ransomware = listOf("Ljavax/crypto/Cipher;", "photo.jpg.locked", "readme.txt")
        val rx = BehaviorRules.match(ransomware.toSet()).find { it.name == "Trojan.Ransom.Crypto" }
        assertTrue("勒索样本必须命中", rx != null)
        assertTrue("勒索判定必须是 HIGH 及以上", rx!!.level.ordinal >= ThreatLevel.HIGH.ordinal)

        val lockRansom = listOf("lockNow", "resetPassword", "Landroid/app/admin/DevicePolicyManager;")
        val lk = BehaviorRules.match(lockRansom.toSet()).find { it.name == "Trojan.Lock.Ransom" }
        assertTrue("锁机勒索样本必须命中", lk != null)
        assertEquals(ThreatLevel.CRITICAL, lk!!.level)
    }

    // ---------------- 4. 感染判定与结构性防线 ----------------

    @Test
    fun onlyMediumAndAboveCountAsInfection() {
        assertFalse(
            "LOW 提示不得计为感染",
            TrojanScanner.Report("p", "n", listOf(det("Virus.HookFramework", ThreatLevel.LOW))).isInfected
        )
        assertTrue(
            "MEDIUM 及以上必须计为感染",
            TrojanScanner.Report("p", "n", listOf(det("Trojan.Ransom.Crypto", ThreatLevel.MEDIUM))).isInfected
        )
        assertFalse(TrojanScanner.Report("p", "n", emptyList()).isInfected)

        // 提示级信息不丢:仍然留在 detections 里(UI 可展示),只是不再算感染
        val hints = BehaviorRules.match(setOf("xposed", "substrate"))
        assertTrue("提示级命中应保留在结果里", hints.isNotEmpty())
        assertFalse("提示级不应计为感染", TrojanScanner.Report("p", "n", hints).isInfected)
    }

    @Test
    fun lockerVerdictRequiresPasswordReset() {
        assertTrue(LockerDetector.lockerVerdict(listOf("resetPassword")))
        assertTrue(LockerDetector.lockerVerdict(listOf("lockNow", "resetPassword", "wipeData")))
        assertFalse("厂商设备管理组件常见 lockNow + wipeData,不得判为锁机木马", LockerDetector.lockerVerdict(listOf("lockNow", "wipeData")))
        assertFalse(LockerDetector.lockerVerdict(emptyList()))
    }

    @Test
    fun scanTargetsExcludeSelfAndTrusted() {
        val targets = ParallelScanner.scanTargets(
            listOf("com.a", "com.self", "com.trusted", "com.b"),
            setOf("com.trusted"),
            "com.self"
        )
        assertEquals(listOf("com.a", "com.b"), targets)
    }

    @Test
    fun bundledSignatureFilesCarryNoGenericFileMagic() {
        val dir = File(mainRoot(), "assets/signatures")
        assertTrue("特征库目录应存在: " + dir.absolutePath, dir.isDirectory)
        val generic = listOf("6465780A3033", "6465780A3038", "504B0304", "7F454C46")
        var checked = 0
        for (f in dir.listFiles().orEmpty().filter { it.name.endsWith(".ndb") || it.name.endsWith(".ndu") }) {
            val text = f.readText()
            for (magic in generic) {
                assertFalse(
                    "特征库 " + f.name + " 不得包含通用文件魔数 " + magic,
                    text.contains(magic, ignoreCase = true)
                )
            }
            checked++
        }
        assertTrue("应至少检查一个 .ndb/.ndu 特征文件", checked >= 1)
        assertFalse(
            "自检特征不得再使用 dex 头魔数签名名",
            File(dir, "trojan_demo.ndb").readText().contains("Test.Trojan.DexHeader")
        )
    }

    @Test
    fun selfScanGuardsAreWiredInSources() {
        val trojan = src("trojan/TrojanScanner.kt")
        assertTrue(
            "TrojanScanner 必须跳过自己(内置检测用字符串会命中自己)",
            trojan.contains("if (pkg == context.packageName) return Report(pkg, pkg, emptyList())")
        )
        assertTrue(
            "感染判定必须只看 MEDIUM 及以上",
            trojan.contains("it.level.ordinal >= ThreatLevel.MEDIUM.ordinal")
        )
        val parallel = src("vscan/ParallelScanner.kt")
        assertTrue(
            "并行查杀必须用 scanTargets 排除自己",
            parallel.contains("scanTargets(PackageSnapshot.packageNames(context), trusted, context.packageName)")
        )
        val rules = src("trojan/BehaviorRules.kt")
        assertTrue("行为规则必须做词边界复核", rules.contains("TokenMatch.occursIn(strings, p)"))
        assertTrue("行为规则必须实现强特征门槛", rules.contains("rule.strong.none { it in matched }"))
        val locker = src("root/LockerDetector.kt")
        assertTrue("锁机判定必须要求 resetPassword", locker.contains("hits.contains(RESET_PASSWORD)"))
        val auditor = src("permissions/PermissionAuditor.kt")
        assertTrue("风险应用统计必须排除本应用", auditor.contains("it.packageName != app.packageName"))
        val cache = src("vscan/DexVerdictCache.kt")
        assertTrue("判定缓存必须带语义版本号(否则老设备继续沿用旧误报)", cache.contains("KEY_VERSION") && cache.contains("VERSION = 2"))
    }

    // ---------------- 源码定位 ----------------

    private fun src(rel: String): String = File(mainRoot(), "java/com/armorlab/securedroid/" + rel).readText()

    private fun mainRoot(): File {
        for (c in listOf(File("src/main"), File("app/src/main"))) {
            if (File(c, "assets/signatures").isDirectory) return c
        }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val c = File(dir, "app/src/main")
            if (File(c, "assets/signatures").isDirectory) return c
            dir = dir.parentFile
        }
        return File("app/src/main")
    }
}
