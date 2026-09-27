package com.armorlab.securedroid.deep

import android.content.Context
import android.util.Base64
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.root.ScriptAnalyzer
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.vscan.IocStore
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter
import java.io.File

/**
 * 全设备文件系统查杀(Root 极致模式):
 * 1. 高危临时目录载荷清点(守护循环复用 quickTmpProbe);
 * 2. find 全盘可执行脚本 / dex / jar;
 * 3. 脚本内容过 ScriptAnalyzer 加权评分;
 * 4. 系统分区全局可写文件;
 * 5. root 方案残留(daemonsu / Superuser.apk / supersu);
 * 6. 可疑命名文件(miner / backdoor / keylog / spy);
 * 7. ClamAV 字节码特征(root-only 路径经 base64 通道读取)。
 */
object FilesystemScanner {

    private val tmpDirs = listOf("/data/local/tmp", "/data/local", "/data/misc/wifi", "/sdcard")
    private val dangerExts = setOf("sh", "dex", "apk", "jar", "bin", "elf")

    data class TmpFile(val path: String, val reason: String, val level: ThreatLevel)

    fun quickTmpProbe(): List<TmpFile> {
        val out = mutableListOf<TmpFile>()
        // 单次 find 覆盖全部临时目录(减少 su 往返)
        val entries = ShellBridge.runSu(
            "find " + tmpDirs.joinToString(" ") { "'" + it + "'" } +
                " -maxdepth 2 -type f 2>/dev/null | head -300", 20_000L
        ) ?: return out
        run {
            for (line in entries.lines()) {
                val path = line.trim()
                if (path.isEmpty()) continue
                val name = path.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (name.startsWith(".") && name.length > 1) {
                    out.add(TmpFile(path, "临时目录隐藏文件", ThreatLevel.HIGH))
                }
                if (ext in dangerExts) {
                    out.add(TmpFile(path, "临时目录载荷文件(." + ext + ")", ThreatLevel.CRITICAL))
                }
            }
        }
        return out
    }

    fun deepScan(context: Context, onPhase: (String) -> Unit): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()
        val rootMode = RootGuard.isRootMode(context)
        if (!rootMode) {
            items.add(
                TrojanAdapter.UiItem(
                    "FS.NeedRoot", "全盘查杀需要 Root 模式;当前结果可能不完整",
                    "请在首页开启 Root 模式后重新执行", ThreatLevel.MEDIUM, null, null
                )
            )
        }

        onPhase("扫描高危临时目录载荷…")
        for (f in quickTmpProbe()) {
            val critical = f.level == ThreatLevel.CRITICAL
            items.add(
                TrojanAdapter.UiItem(
                    "FS." + (if (critical) "TmpPayload" else "TmpHidden") + " · " + f.path.substringAfterLast('/'),
                    f.path, f.reason, f.level,
                    if (critical) "临时目录中的载荷文件是木马投递的典型形态,确认后删除" else null,
                    null, null,
                    if (critical) "rm -f '" + f.path + "'" else null,
                    if (critical) "删除文件" else null
                )
            )
        }

        onPhase("find 全盘扫描(system/vendor/product/odm/data/cache/persist)…")
        val findCmd = "find /system /vendor /product /odm /data /cache /persist -type f " +
            "\\( -name \"*.sh\" -o -name \"*.dex\" -o -name \"*.jar\" \\) -perm -111 2>/dev/null | head -200"
        val found = if (rootMode) ShellBridge.runSu(findCmd, 120_000L) ?: "" else ""
        val scriptPaths = mutableListOf<String>()
        for (path in found.lines()) {
            val t = path.trim()
            if (t.isEmpty()) continue
            when (t.substringAfterLast('.', "").lowercase()) {
                "sh" -> scriptPaths.add(t)
                "dex", "jar" -> items.add(
                    TrojanAdapter.UiItem(
                        "FS.RawDex", t, "全盘扫描发现可执行 dex/jar 文件",
                        ThreatLevel.CRITICAL,
                        "系统与正常应用目录之外的可执行 dex 是注入/免杀常见形态",
                        null, null, "rm -f '" + t + "'", "删除文件"
                    )
                )
            }
        }

        onPhase("分析可疑脚本内容…")
        for (p in scriptPaths.take(60)) {
            val content = ShellBridge.readFileBestEffort(p) ?: continue
            val v = ScriptAnalyzer.analyze(content)
            if (!v.hasFindings) continue
            items.add(
                TrojanAdapter.UiItem(
                    "FS." + (if (v.isMalicious) "MaliciousScript" else "SuspiciousScript"),
                    p,
                    "风险评分 " + v.score + "/100 — " + v.hits.joinToString("; ") { it.label },
                    v.level,
                    if (v.isMalicious) "脚本包含恶意行为组合,确认后删除" else "脚本包含可疑行为,建议人工审查",
                    null, null,
                    if (v.isMalicious) "rm -f '" + p + "'" else null,
                    if (v.isMalicious) "删除文件" else null
                )
            )
        }

        onPhase("检查系统分区权限异常…")
        val ww = if (rootMode)
            ShellBridge.runSu("find /system /vendor /product -type f -perm -0002 2>/dev/null | head -50", 120_000L)
            ?: "" else ""
        for (t in ww.lines()) {
            val path = t.trim()
            if (path.isEmpty()) continue
            items.add(
                TrojanAdapter.UiItem(
                    "FS.WorldWritable", path, "系统分区文件对全局可写,可被任意进程篡改",
                    ThreatLevel.HIGH, "建议修复为 644 并排查篡改来源",
                    null, null, "chmod 644 '" + path + "'", "修复权限"
                )
            )
        }

        val suFiles = if (rootMode)
            ShellBridge.runSu("find / -maxdepth 4 -type f \\( -name \"daemonsu\" -o -name \"Superuser.apk\" -o -name \"supersu\" \\) 2>/dev/null | head -20", 120_000L)
            ?: "" else ""
        for (t in suFiles.lines()) {
            val path = t.trim()
            if (path.isEmpty()) continue
            items.add(
                TrojanAdapter.UiItem(
                    "FS.SuRemnant", path, "发现历史 root 方案残留文件",
                    ThreatLevel.MEDIUM, "废弃 root 方案残留是提权后门常见入口,确认后删除",
                    null, null, "rm -f '" + path + "'", "删除文件"
                )
            )
        }

        val named = if (rootMode)
            ShellBridge.runSu("find /data /cache /persist -type f \\( -iname \"*miner*\" -o -iname \"*backdoor*\" -o -iname \"*keylog*\" -o -iname \"*spy*\" \\) 2>/dev/null | head -30", 120_000L)
            ?: "" else ""
        for (t in named.lines()) {
            val path = t.trim()
            if (path.isEmpty()) continue
            items.add(
                TrojanAdapter.UiItem(
                    "FS.SuspiciousName", path, "文件名命中可疑关键词",
                    ThreatLevel.HIGH, "结合文件内容与来源确认是否恶意",
                    null, null, null, null
                )
            )
        }

        // 自定义 IOC 模式匹配
        val iocPatterns = IocStore.patterns(context)
        if (iocPatterns.isNotEmpty() && rootMode) {
            for (p in found.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
                if (IocStore.matches(context, p.substringAfterLast('/'))) {
                    items.add(
                        TrojanAdapter.UiItem(
                            "FS.IocMatch", p, "文件名命中自定义 IOC 模式",
                            ThreatLevel.HIGH, "命中用户自定义 IOC,请人工确认",
                            null, null, null, null
                        )
                    )
                }
            }
        }

        onPhase("ClamAV 字节码特征扫描…")
        val targets = (quickTmpProbe().filter { it.level == ThreatLevel.CRITICAL }.map { it.path } +
            found.lines().map { it.trim() }.filter { it.isNotEmpty() })
            .distinct().take(30)
        for (p in targets) {
            val bytes = readBytesBest(p, 4L * 1024 * 1024) ?: continue
            for (sig in ClamAvSignatures.scanBytes(bytes)) {
                items.add(
                    TrojanAdapter.UiItem(
                        "FS.ClamAvHit · " + sig, p, "文件命中 ClamAV 字节码特征",
                        ThreatLevel.CRITICAL, "命中病毒特征库,建议立即删除",
                        null, null, "rm -f '" + p + "'", "删除文件"
                    )
                )
            }
        }
        return items
    }

    private fun readBytesBest(path: String, cap: Long): ByteArray? {
        try {
            val f = File(path)
            if (f.isFile && f.canRead() && f.length() <= cap) return f.readBytes()
        } catch (_: Exception) {
        }
        val b64 = ShellBridge.runSu(
            "base64 '" + path + "' 2>/dev/null | head -c " + ((cap / 3) * 4 + 8), 60_000L
        ) ?: return null
        return try { Base64.decode(b64.trim(), Base64.DEFAULT) } catch (_: Exception) { null }
    }
}
