package com.armorlab.securedroid.root

import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import java.io.File

/**
 * 恶意模块与 su 脚本扫描器,覆盖当前主流 Root 方案:
 * - Magisk / KernelSU / SukiSU-Ultra: /data/adb/modules、/data/adb/ksu/modules
 * - APatch: /data/adb/ap/modules(部分版本 /data/adb/apatch/modules)
 * - 通用开机脚本目录: /data/adb/service.d、/data/adb/post-fs-data.d
 *
 * 检测逻辑:读取各模块 module.prop 与启动脚本(service.sh / post-fs-data.sh /
 * action.sh / customize.sh / uninstall.sh / boot-completed.sh),经 ScriptAnalyzer
 * 加权评分;命中即生成"禁用模块"(touch disable,Magisk/KSU/APatch/SukiSU 通用的
 * 模块禁用机制)或"删除脚本"处置命令。
 */
object ModuleScanner {

    data class Finding(
        val title: String,
        val sub: String,
        val detail: String,
        val level: ThreatLevel,
        val suggestion: String,
        val fixCommand: String?,
        val fixLabel: String?
    )

    data class ScanResult(val findings: List<Finding>, val scannedRoots: Int)

    /** Root 方案 → 模块根目录(全部尝试,目录不存在自动跳过) */
    private val moduleRoots: List<Triple<String, String, String>> = listOf(
        Triple("/data/adb/modules", "Magisk / KernelSU / SukiSU-Ultra", "disable"),
        Triple("/data/adb/ksu/modules", "KernelSU / SukiSU-Ultra", "disable"),
        Triple("/data/adb/ap/modules", "APatch", "disable"),
        Triple("/data/adb/apatch/modules", "APatch", "disable")
    )

    /** 通用 su 开机脚本目录 */
    private val scriptDirs = listOf(
        "/data/adb/service.d",
        "/data/adb/post-fs-data.d"
    )

    /** 模块内会被自动执行的脚本 */
    private val moduleScripts = listOf(
        "service.sh", "post-fs-data.sh", "action.sh", "customize.sh",
        "uninstall.sh", "boot-completed.sh"
    )

    /**
     * root 批量采集:单次 su 调用完成全部模块 / su 脚本读取,
     * 将 N 次 su 往返压缩为 1 次,守护循环延迟下降一个数量级。
     */
    fun scanBatched(context: Context): ScanResult {
        val findings = mutableListOf<Finding>()
        val cmd = "for d in /data/adb/modules/*/ /data/adb/ksu/modules/*/ " +
            "/data/adb/ap/modules/*/ /data/adb/apatch/modules/*/; do " +
            "[ -d \"\$d\" ] || continue; [ -f \"\$d\"disable ] && continue; " +
            "echo ===MODULE \"\$d\"; cat \"\$d\"module.prop 2>/dev/null; " +
            "for s in service.sh post-fs-data.sh action.sh customize.sh uninstall.sh boot-completed.sh; do " +
            "if [ -f \"\$d\$s\" ]; then echo ===SCRIPT \"\$s\"; cat \"\$d\$s\"; fi; done; done; " +
            "echo ===SUDIRS; for f in /data/adb/service.d/*.sh /data/adb/post-fs-data.d/*.sh; do " +
            "[ -f \"\$f\" ] || continue; echo ===SUFILE \"\$f\"; cat \"\$f\"; done"
        val out = ShellBridge.runSu(cmd, 60_000L) ?: return ScanResult(findings, 0)

        // 第一遍:切分为段
        data class Seg(val kind: String, val path: String, val content: String)
        val segs = mutableListOf<Seg>()
        var kind = ""
        var path = ""
        val buf = StringBuilder()
        fun flushSeg() {
            if (kind.isNotEmpty()) segs.add(Seg(kind, path, buf.toString()))
            buf.setLength(0)
        }
        for (raw in out.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("===MODULE ") -> { flushSeg(); kind = "MODULE"; path = line.removePrefix("===MODULE ").trim().trimEnd('/') }
                line.startsWith("===SCRIPT ") -> { flushSeg(); kind = "SCRIPT"; path = line.removePrefix("===SCRIPT ").trim() }
                line.startsWith("===SUFILE ") -> { flushSeg(); kind = "SUFILE"; path = line.removePrefix("===SUFILE ").trim() }
                line.startsWith("===SUDIRS") -> { flushSeg(); kind = "" }
                else -> if (kind.isNotEmpty()) buf.append(line).append('\n')
            }
        }
        flushSeg()

        // 第二遍:按模块聚合评分
        val propByDir = LinkedHashMap<String, String>()
        val scriptsByDir = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        val suFiles = mutableListOf<Pair<String, String>>()
        for (seg in segs) {
            when (seg.kind) {
                "MODULE" -> { propByDir[seg.path] = seg.content }
                "SCRIPT" -> {
                    val dir = seg.path.substringBeforeLast('/')
                    scriptsByDir.getOrPut(dir) { mutableListOf() }
                        .add(seg.path.substringAfterLast('/') to seg.content)
                }
                "SUFILE" -> suFiles.add(seg.path to seg.content)
            }
        }

        for ((dir, prop) in propByDir) {
            val meta = parseProp(prop)
            val id = meta["id"] ?: dir.substringAfterLast('/')
            val name = (meta["name"] ?: id).trim()
            var worst: ScriptAnalyzer.Verdict? = null
            val hits = mutableListOf<String>()
            for ((scriptName, content) in scriptsByDir[dir] ?: emptyList()) {
                val v = ScriptAnalyzer.analyze(content)
                if (v.hasFindings) {
                    hits.add("[" + scriptName + "] " + v.hits.joinToString("; ") { it.label })
                    if (worst == null || v.score > worst.score) worst = v
                }
            }
            val w = worst
            if (w != null) {
                findings.add(
                    Finding(
                        name + " (" + id + "), " + "Root 模块 · " + dir,
                        dir,
                        "脚本风险评分 " + w.score + "/100\n" + hits.joinToString("\n"),
                        w.level,
                        "模块启动脚本包含风险行为;可禁用后人工审查,确认恶意再删除整个模块目录",
                        "touch " + ShellBridge.quote(dir + "/disable"), "禁用模块"
                    )
                )
            }
        }

        for ((path, content) in suFiles) {
            val v = ScriptAnalyzer.analyze(content)
            if (v.hasFindings) {
                findings.add(
                    Finding(
                        "SuScript." + (if (v.isMalicious) "Malicious" else "Suspicious"),
                        path,
                        "风险评分 " + v.score + "/100 — " + v.hits.joinToString("; ") { it.label },
                        v.level,
                        "开机脚本包含风险行为;确认来源后删除(删除前建议先备份脚本内容)",
                        "rm -f " + ShellBridge.quote(path), "删除脚本"
                    )
                )
            }
        }

        return ScanResult(findings, propByDir.size)
    }

    fun scan(context: Context): ScanResult {
        val findings = mutableListOf<Finding>()
        var scannedRoots = 0

        // 1. 模块目录
        for ((root, framework, _) in moduleRoots) {
            val modules = ShellBridge.listDirBestEffort(root) ?: continue
            scannedRoots++
            for (m in modules) {
                val dir = root + "/" + m
                scanModule(dir, framework, findings)
            }
        }

        // 2. 独立 su 开机脚本
        for (d in scriptDirs) {
            val files = ShellBridge.listDirBestEffort(d) ?: continue
            for (f in files) {
                if (!f.endsWith(".sh")) continue
                val path = d + "/" + f
                val content = ShellBridge.readFileBestEffort(path) ?: continue
                val v = ScriptAnalyzer.analyze(content)
                if (v.hasFindings) {
                    findings.add(
                        Finding(
                            title = "SuScript." + (if (v.isMalicious) "Malicious" else "Suspicious"),
                            sub = path,
                            detail = "风险评分 " + v.score + "/100 — " +
                                v.hits.joinToString("; ") { it.label },
                            level = v.level,
                            suggestion = "开机脚本包含风险行为;确认来源后删除(删除前建议先备份脚本内容)",
                            fixCommand = "rm -f " + ShellBridge.quote(path),
                            fixLabel = "删除脚本"
                        )
                    )
                }
            }
        }

        return ScanResult(findings, scannedRoots)
    }

    private fun scanModule(dir: String, framework: String, findings: MutableList<Finding>) {
        // 已被禁用的模块不再报告
        if (ShellBridge.existsBestEffort(dir + "/disable")) return

        val prop = ShellBridge.readFileBestEffort(dir + "/module.prop") ?: return
        val meta = parseProp(prop)
        val id = meta["id"] ?: dir.substringAfterLast('/')
        val name = (meta["name"] ?: id).trim()

        var worst: ScriptAnalyzer.Verdict? = null
        val scriptHits = mutableListOf<String>()
        for (s in moduleScripts) {
            val content = ShellBridge.readFileBestEffort(dir + "/" + s) ?: continue
            val v = ScriptAnalyzer.analyze(content)
            if (v.hasFindings) {
                scriptHits.add("[" + s + "] " + v.hits.joinToString("; ") { it.label })
                if (worst == null || v.score > worst.score) worst = v
            }
        }

        if (worst != null) {
            findings.add(
                Finding(
                    title = name + " (" + id + ")",
                    sub = framework + " 模块 · " + dir,
                    detail = "脚本风险评分 " + worst.score + "/100\n" + scriptHits.joinToString("\n"),
                    level = worst.level,
                    suggestion = "模块启动脚本包含风险行为;可通过 disable 禁用后人工审查,确认恶意再删除整个模块目录",
                    fixCommand = "touch " + ShellBridge.quote(dir + "/disable"),
                    fixLabel = "禁用模块"
                )
            )
            return
        }

        // 无脚本命中时,检查敏感附带文件
        if (ShellBridge.existsBestEffort(dir + "/sepolicy.rule")) {
            findings.add(
                Finding(
                    title = name + " (" + id + ")",
                    sub = framework + " 模块 · " + dir,
                    detail = "模块携带 SELinux 策略补丁(sepolicy.rule),可能放宽系统安全策略",
                    level = ThreatLevel.MEDIUM,
                    suggestion = "审查 sepolicy.rule 内容,确认没有 permissive 全放行或放行可疑域的规则",
                    fixCommand = null,
                    fixLabel = null
                )
            )
        }
    }

    /** 解析 module.prop 的 key=value */
    private fun parseProp(content: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (line in content.lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val idx = t.indexOf('=')
            if (idx > 0) map[t.substring(0, idx).trim()] = t.substring(idx + 1).trim()
        }
        return map
    }
}