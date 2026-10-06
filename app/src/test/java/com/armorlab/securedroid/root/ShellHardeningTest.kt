package com.armorlab.securedroid.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * root 侧命令执行的加固回归:
 *  - 退出码/拒绝标记才是成功判据,输出非 null 不是;
 *  - 一切拼进 shell 的参数都必须经过 [ShellBridge.quote],否则文件名里的单引号会越出引号;
 *  - [PrivilegedPolicy] 必须放行应用真实使用的处置命令,同时拦住灾难性命令。
 *
 * 前三组是行为断言,后三组是源码守卫(与本项目 PerfGuardTest 同风格)。
 */
class ShellHardeningTest {

    // ---------- 1. 引号转义 ----------

    @Test
    fun quoteWrapsAndEscapesSingleQuotes() {
        assertEquals("'/data/local/tmp/x.apk'", ShellBridge.quote("/data/local/tmp/x.apk"))
        assertEquals("''", ShellBridge.quote(""))
        assertEquals("'a'\\''b'", ShellBridge.quote("a'b"))
        assertEquals("'a'\\''b'\\''c'", ShellBridge.quote("a'b'c"))
    }

    @Test
    fun unquoteIsInverseOfQuoteForHostileNames() {
        val names = listOf(
            "normal.apk",
            "with space.apk",
            "quote'inside",
            "two'quotes'here",
            "\$(reboot)",
            "semi;colon|pipe",
            "换行\n注入",
            "中文'文件名'.bin",
            "star*glob?.apk",
            ""
        )
        for (n in names) {
            assertEquals(n, ShellBridge.unquote(ShellBridge.quote(n)))
        }
    }

    @Test
    fun injectedPayloadStaysOneSingleArgument() {
        val evil = "x'; rm -rf /data; echo '"
        val quoted = ShellBridge.quote(evil)
        val cmd = "rm -f " + quoted
        assertTrue(cmd.startsWith("rm -f '"))
        assertEquals(1, Regex("rm -f ").findAll(cmd).count())
        // 逆运算必须还原出原始文件名:说明整段 payload 仍是“一个参数”,没有被拆成命令
        assertEquals(evil, ShellBridge.unquote(cmd.removePrefix("rm -f ")))
    }

    // ---------- 2. 策略放行 / 拦截 ----------

    @Test
    fun policyAllowsEveryCommandTheAppActuallyRuns() {
        val real = listOf(
            "rm -rf /data/data/*/cache/* 2>/dev/null",
            "rm -f '/data/local/tmp/evil.apk'",
            "mv '/data/local/tmp/x' '/data/data/com.armorlab.securedroid/files/quarantine/x.qtn' && chmod 600 '/data/data/com.armorlab.securedroid/files/quarantine/x.qtn'",
            "iptables -I OUTPUT 1 -m owner --uid-owner 10001 -j DROP",
            "iptables -D OUTPUT -m owner --uid-owner 10001 -j DROP",
            "touch '/data/adb/modules/foo/disable'",
            "pm uninstall --user 0 'com.evil.app'",
            "kill -9 1234",
            "am force-stop 'com.evil.app' ; pm clear 'com.evil.app'",
            "dpm remove-active-admin 'com.evil/.Admin' ; pm uninstall --user 0 'com.evil'",
            "chmod 644 '/system/etc/hosts'",
            "base64 '/data/system/users/0/foo' 2>/dev/null | head -c 4104",
            "dd if='/dev/block/sda' bs=4096 count=16",
            "find '/data/local/tmp' -maxdepth 1 -type f -perm -0002 2>/dev/null | head -50",
            "rm -rf '/data/data/com.evil/cache'"
        )
        val denied = real.filter { PrivilegedPolicy.check(it) is PolicyVerdict.Deny }
        assertEquals("以下真实命令被策略误杀: " + denied, emptyList<String>(), denied)
    }

    @Test
    fun policyDeniesCatastrophicCommands() {
        val bad = listOf(
            "rm -rf /",
            "rm -rf /system",
            "mkfs.ext4 /dev/block/sda",
            "dd if=/dev/zero of=/dev/block/sda",
            "fastboot flash boot boot.img",
            "wipe data",
            "master clear",
            "rm -rf /data/adb/modules",
            "pm uninstall --user all com.x",
            "reboot",
            "setprop ro.debuggable 1",
            "chmod -R 777 /",
            "echo x > /dev/block/sda",
            ""
        )
        for (cmd in bad) {
            assertTrue("策略未拦截: " + cmd, PrivilegedPolicy.check(cmd) is PolicyVerdict.Deny)
        }
    }

    // ---------- 3. 源码守卫 ----------

    @Test
    fun noMutationTreatsSuOutputAsSuccess() {
        val pattern = Regex("runSu\\([^;]*\\)\\s*!=\\s*null")
        val offenders = mainKotlin().filter { pattern.containsMatchIn(codeOnly(it)) }.map { it.name }
        assertEquals("以下文件仍把“输出非 null”当成成功: " + offenders, emptyList<String>(), offenders)
    }

    @Test
    fun everyShelledPathIsQuoted() {
        val needles = listOf(
            "rm -f '\"", "touch '\"", "chmod 644 '\"", "dd if='\"",
            "[ -e '\"", "base64 '\"", "cat '\"", "ls -1 '\"", "joinToString(\" \") { \"'\" + it + \"'\" }"
        )
        val offenders = mainKotlin().filter { f ->
            val code = codeOnly(f)
            needles.any { code.contains(it) }
        }.map { it.name }
        assertEquals("以下文件仍在引号内直接拼接参数: " + offenders, emptyList<String>(), offenders)
    }

    @Test
    fun mutationCallSitesGoThroughCheckedRunner() {
        val root = mainSourceRoot()
        val files = listOf(
            "com/armorlab/securedroid/vscan/Quarantine.kt",
            "com/armorlab/securedroid/vscan/NetKill.kt",
            "com/armorlab/securedroid/root/RootGuard.kt",
            "com/armorlab/securedroid/realtime/RealtimeProtectionService.kt",
            "com/armorlab/securedroid/web/handlers/ToolsHandler.kt",
            "com/armorlab/securedroid/root/PrivilegeManager.kt"
        )
        for (rel in files) {
            val f = File(root, rel)
            assertTrue("缺少源文件: " + rel, f.isFile)
            val code = codeOnly(f)
            assertTrue(rel + " 未使用加固后的执行入口", code.contains("runSuChecked(") || code.contains("runSuResult("))
        }
    }

    @Test
    fun shellBridgeExposesHardenedApi() {
        val f = File(mainSourceRoot(), "com/armorlab/securedroid/root/ShellBridge.kt")
        assertTrue(f.isFile)
        val code = codeOnly(f)
        for (member in listOf("fun quote(", "fun unquote(", "fun runSuChecked(", "fun runSuResult(", "DENY_HINTS")) {
            assertTrue("ShellBridge 缺少 " + member, code.contains(member))
        }
        assertFalse("ShellBridge 不应再把输出非 null 当作成功判据", code.contains("out != null"))
    }

    // ---------- helpers ----------

    private fun mainSourceRoot(): File {
        for (c in listOf(File("src/main/java"), File("app/src/main/java"))) {
            if (c.isDirectory) return c
        }
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        throw IllegalStateException("找不到 app/src/main/java")
    }

    private fun mainKotlin(): List<File> =
        mainSourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * 去注释后返回源码,避免注释里的示例代码触发守卫。
     * 必须区分"字符串里的斜杠加星号"与真注释:shell 通配符会在字符串内部制造假的注释配对,
     * 朴素的 replace(Regex) 会把大段真实代码一起吞掉。
     */
    private fun codeOnly(f: File): String {
        val src = f.readText()
        val sb = StringBuilder(src.length)
        var i = 0
        var inLine = false
        var inBlock = false
        var inStr = false
        var inChar = false
        while (i < src.length) {
            val c = src[i]
            val n = if (i + 1 < src.length) src[i + 1] else ' '
            when {
                inLine -> { if (c == '\n') { inLine = false; sb.append(c) }; i++ }
                inBlock -> { if (c == '*' && n == '/') { inBlock = false; i += 2 } else i++ }
                inStr -> {
                    sb.append(c)
                    if (c == '\\' && i + 1 < src.length) { sb.append(n); i += 2 } else { if (c == '"') inStr = false; i++ }
                }
                inChar -> {
                    sb.append(c)
                    if (c == '\\' && i + 1 < src.length) { sb.append(n); i += 2 } else { if (c == '\'') inChar = false; i++ }
                }
                c == '/' && n == '/' -> { inLine = true; i += 2 }
                c == '/' && n == '*' -> { inBlock = true; i += 2 }
                c == '"' -> { inStr = true; sb.append(c); i++ }
                c == '\'' -> { inChar = true; sb.append(c); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}
