package com.armorlab.securedroid.web.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.core.TimeFmt
import com.armorlab.securedroid.deep.DeepScanEngine
import com.armorlab.securedroid.feature.DnsGuard
import com.armorlab.securedroid.feature.HostsGuard
import com.armorlab.securedroid.feature.NetAudit
import com.armorlab.securedroid.feature.SystemBaseline
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.root.Capability
import com.armorlab.securedroid.root.PrivLevel
import com.armorlab.securedroid.root.PrivilegeManager
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.root.SystemIntegrity
import com.armorlab.securedroid.scan.SignatureDatabase
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.vscan.ActionPolicy
import com.armorlab.securedroid.vscan.AttackSurface
import com.armorlab.securedroid.vscan.BlocklistEngine
import com.armorlab.securedroid.vscan.CertStore
import com.armorlab.securedroid.vscan.CombinedScore
import com.armorlab.securedroid.vscan.FamilyClassifier
import com.armorlab.securedroid.vscan.FeatureUpdater
import com.armorlab.securedroid.vscan.HashCache
import com.armorlab.securedroid.vscan.NetKill
import com.armorlab.securedroid.vscan.ParallelScanner
import com.armorlab.securedroid.vscan.PhishingDetector
import com.armorlab.securedroid.vscan.ProcessBaseline
import com.armorlab.securedroid.vscan.Quarantine
import com.armorlab.securedroid.vscan.ResidueScanner
import com.armorlab.securedroid.vscan.ResultDiff
import com.armorlab.securedroid.vscan.ScanControl
import com.armorlab.securedroid.vscan.SignatureStats
import com.armorlab.securedroid.vscan.ThreatReport
import com.armorlab.securedroid.vscan.Timeline
import com.armorlab.securedroid.vscan.TrustStore
import com.armorlab.securedroid.vscan.VerdictArbiter
import com.armorlab.securedroid.web.AppMode
import com.armorlab.securedroid.web.AppModeStore
import com.armorlab.securedroid.web.BridgeScope
import com.armorlab.securedroid.web.WebEventSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 深层工具桥接处理器:网络审计 / 隐私检测 / 漏洞扫描 / 深度查杀 / 病毒中心。
 * 所有列表项使用统一 UiItem JSON 模型,与 HTML 列表渲染器对接。
 */
class ToolsHandler(
    private val app: Context,
    private val activity: MainActivity,
    private val sink: WebEventSink
) {

    /* ---------- 网络审计 ---------- */

    suspend fun getNetAudit(): String = withContext(Dispatchers.Default) {
        val items = mutableListOf<JSONObject>()

        for (r in DnsGuard.probe()) {
            val level = when {
                r.hijacked -> "high"
                r.verified -> "low"
                else -> "medium"
            }
            items.add(
                uiItem(
                    title = if (r.hijacked) "DNS.HijackSuspect · " + r.host
                            else if (r.verified) "DNS.Clean · " + r.host
                            else "DNS.Unverified · " + r.host,
                    sub = "系统解析: " + r.systemIps.joinToString(", ").ifEmpty { "解析失败" } +
                        " | DoH: " + r.dohIps.joinToString(", ").ifEmpty { "加密解析失败" },
                    level = level,
                    suggestion = if (r.hijacked) "系统 DNS 与加密 DNS 结果不一致,怀疑被劫持;建议更换可信 DNS(如 223.5.5.5)" else null
                )
            )
        }

        val hosts = HostsGuard.analyze(HostsGuard.readHosts())
        for (h in hosts) {
            items.add(
                uiItem(
                    title = if (h.sensitiveHit) "Hosts.SensitiveRedirect" else "Hosts.CustomEntry",
                    sub = h.line,
                    level = if (h.sensitiveHit) "high" else "medium",
                    suggestion = if (h.sensitiveHit) "hosts 中出现支付/银行相关域名映射,高度可疑" else "非标准 hosts 条目,确认是否自己添加"
                )
            )
        }
        if (hosts.isEmpty()) {
            items.add(uiItem("Hosts.Clean", "/etc/hosts 无自定义条目", level = "low"))
        }

        for (c in NetAudit.established(app)) {
            items.add(
                uiItem(
                    title = "Net.Conn · " + c.appLabel,
                    sub = c.pkg + " → " + c.remote,
                    level = "low"
                )
            )
        }

        resultJson(items)
    }

    /* ---------- 隐私检测 ---------- */

    suspend fun getPrivacyAudit(): String = withContext(Dispatchers.Default) {
        val self = app.packageName
        val results = PermissionAuditor.audit(app).filter { it.packageName != self }
        val items = mutableListOf<JSONObject>()

        if (results.isEmpty()) {
            items.add(uiItem("Privacy.Clean", app.getString(R.string.privacy_clean), level = "low"))
            return@withContext resultJson(items)
        }

        val highest = results.maxByOrNull { it.score }?.level ?: ThreatLevel.LOW
        items.add(
            uiItem(
                title = "Privacy.Summary",
                sub = app.getString(R.string.privacy_summary_fmt, results.size, levelLabel(highest)),
                detail = "敏感权限按权重累加评分,分数越高越危险",
                level = if (highest == ThreatLevel.CRITICAL || highest == ThreatLevel.HIGH) "medium" else "low"
            )
        )
        for (r in results) {
            val perms = r.risky.map { permLabel(it) }.distinct()
            items.add(
                uiItem(
                    title = r.appName,
                    sub = r.packageName,
                    detail = perms.joinToString(" · "),
                    level = threatToStr(r.level),
                    suggestion = if (r.level.ordinal >= ThreatLevel.HIGH.ordinal) "在系统设置中撤销其敏感权限,或卸载" else null,
                    uninstallPkg = r.packageName
                )
            )
        }

        resultJson(items)
    }

    /* ---------- 漏洞扫描 ---------- */

    suspend fun getVulnScan(): String = withContext(Dispatchers.Default) {
        val checks = SystemBaseline.checks(app)
        val okCount = checks.count { it.ok }
        val items = mutableListOf(
            uiItem(
                title = "Vuln.Summary",
                sub = app.getString(R.string.vuln_summary_fmt, checks.size, okCount),
                detail = if (okCount == checks.size) "系统补丁与配置状态良好" else "存在待处理的风险项,按建议逐项处置",
                level = if (okCount == checks.size) "low" else "medium"
            )
        )
        for (c in checks) {
            items.add(
                uiItem(
                    title = (if (c.ok) "Vuln.OK · " else "Vuln.Risk · ") + c.name,
                    sub = if (c.ok) "检查通过" else c.detail,
                    detail = if (c.ok) "保持现状" else c.advice,
                    level = if (c.ok) "low" else threatToStr(c.level),
                    suggestion = if (!c.ok) c.advice else null
                )
            )
        }
        items.add(
            uiItem(
                title = "Vuln.Surface",
                sub = app.getString(R.string.vuln_surface_head),
                detail = "第三方应用 exported 组件与 Provider 暴露面",
                level = "low"
            )
        )
        items.addAll(AttackSurface.items(app).map { item ->
            uiItem(
                title = item.title,
                sub = item.sub,
                detail = item.detail,
                level = threatToStr(item.level),
                suggestion = item.suggestion,
                uninstallPkg = item.uninstallPkg,
                fixCommand = item.fixCommand,
                fixLabel = item.fixLabel
            )
        })

        resultJson(items)
    }

    /* ---------- 深度查杀 ---------- */

    fun startDeepScan() {
        BridgeScope.default.launch {
            ScanControl.reset()
            sink.sendEvent("deepscan.progress",
                JSONObject().put("phase", "深度扫描启动中…").put("running", true).toString())
            val items = try {
                DeepScanEngine.run(app) { phase ->
                    sink.sendEvent("deepscan.progress",
                        JSONObject().put("phase", phase).put("running", true).toString())
                }.map { item ->
                    uiItem(
                        title = item.title,
                        sub = item.sub,
                        detail = item.detail,
                        level = threatToStr(item.level),
                        suggestion = item.suggestion,
                        uninstallPkg = item.uninstallPkg,
                        fixCommand = item.fixCommand,
                        fixLabel = item.fixLabel
                    )
                }
            } catch (e: Exception) {
                listOf(uiItem("Error", e.message ?: "执行异常", level = "medium"))
            }
            val sorted = items.sortedByDescending { levelOrder(it.optString("level")) }
            sink.sendEvent("deepscan.result", JSONObject()
                .put("items", JSONArray(sorted))
                .put("count", sorted.size)
                .put("running", false)
                .toString())
        }
    }

    /* ---------- 病毒中心菜单 ---------- */

    /* ---------- 模式门控(需求:各模式只可用有权限功能) ---------- */

    /** 必须 Root 权限的工具:仅超级用户模式开放 */
    private val rootOnlyTools = setOf(
        "netkill", "priv", "integrity", "lockfiles", "unlockfiles", "quarantine", "residue"
    )

    /** 需要 Shell / 无线调试能力的工具:无线调试与超级用户模式开放 */
    private val shellTools = setOf("newproc", "learn")

    private fun currentMode(): AppMode = AppModeStore.current(app)

    private fun toolAllowed(actionId: String): Boolean {
        val mode = currentMode()
        return when {
            actionId in rootOnlyTools -> mode == AppMode.SUPERUSER
            actionId in shellTools -> mode == AppMode.WIRELESS_DEBUG || mode == AppMode.SUPERUSER
            else -> true
        }
    }

    /* 病毒中心菜单按模式过滤并缓存(模式不变则复用,避免每次构建 30 项 JSON) */
    private var menuCacheMode: AppMode? = null
    private var menuCacheJson: String? = null

    suspend fun getVirusCenterMenu(): String = withContext(Dispatchers.Default) {
        val mode = currentMode()
        val cached = menuCacheJson
        if (menuCacheMode == mode && cached != null) return@withContext cached
        val menu = JSONArray().apply {
            fun add(id: String, title: Int, sub: Int) {
                if (toolAllowed(id)) put(menuItem(id, app.getString(title), app.getString(sub)))
            }
            add("recent", R.string.vc_recent, R.string.vc_recent_sub)
            add("parallel", R.string.va_parallel, R.string.va_parallel_sub)
            add("diff", R.string.vc_diff, R.string.vc_diff_sub)
            add("embedded", R.string.vc_embedded, R.string.vc_embedded_sub)
            add("resultdiff", R.string.va_resultdiff, R.string.va_resultdiff_sub)
            add("family", R.string.va_family, R.string.va_family_sub)
            add("timeline", R.string.va_timeline, R.string.va_timeline_sub)
            add("newproc", R.string.vc_newproc, R.string.vc_newproc_sub)
            add("learn", R.string.vc_learn, R.string.vc_learn_sub)
            add("netblock", R.string.va_netblock, R.string.va_netblock_sub)
            add("netblockmgr", R.string.va_netblockmgr, R.string.va_netblockmgr_sub)
            add("netkill", R.string.va_netkill, R.string.va_netkill_sub)
            add("vpnapps", R.string.va_vpn, R.string.va_vpn_sub)
            add("attack", R.string.va_attack, R.string.va_attack_sub)
            add("origin", R.string.va_origin, R.string.va_origin_sub)
            add("certaudit", R.string.va_cert, R.string.va_cert_sub)
            add("certmark", R.string.va_certmark, R.string.va_certmark_sub)
            add("residue", R.string.vc_residue, R.string.vc_residue_sub)
            add("quarantine", R.string.vc_quarantine, R.string.vc_quarantine_sub)
            add("stats", R.string.vc_stats, R.string.vc_stats_sub)
            add("sig", R.string.vc_sig, R.string.vc_sig_sub)
            add("phishing", R.string.vc_phishing, R.string.vc_phishing_sub)
            add("policy", R.string.va_policy, R.string.va_policy_sub)
            add("autq", R.string.va_autq, R.string.va_autq_sub)
            add("update", R.string.vc_update, R.string.vc_update_sub)
            add("priv", R.string.va_priv, R.string.va_priv_sub)
            add("integrity", R.string.va_integrity, R.string.va_integrity_sub)
            add("lockfiles", R.string.va_lockfiles, R.string.va_lockfiles_sub)
            add("unlockfiles", R.string.va_unlockfiles, R.string.va_unlockfiles_sub)
            add("trust", R.string.vc_trust, R.string.vc_trust_sub)
            add("report", R.string.vc_report, R.string.vc_report_sub)
        }
        val json = JSONObject().put("menu", menu).toString()
        menuCacheMode = mode
        menuCacheJson = json
        json
    }

    /* ---------- 病毒中心:运行指定工具 ---------- */

    private var virusToolRunning = false

    fun isVirusToolRunning(): Boolean = virusToolRunning

    fun runVirusTool(actionId: String) {
        if (virusToolRunning) return
        if (!toolAllowed(actionId)) {
            sink.sendEvent("virustool.result", JSONObject()
                .put("items", JSONArray().put(
                    uiItem("Mode.Required", app.getString(R.string.mode_need_higher), level = "medium")))
                .put("count", 1)
                .put("running", false)
                .put("action", actionId)
                .toString())
            return
        }
        virusToolRunning = true
        BridgeScope.default.launch {
            ScanControl.reset()
            val phase = phaseFor(actionId)
            sink.sendEvent("virustool.progress",
                JSONObject().put("phase", phase).put("running", true).put("action", actionId).toString())

            val resultItems = try {
                runToolAction(actionId)
            } catch (e: Exception) {
                listOf(uiItem("Error", e.message ?: "执行异常", level = "medium"))
            }

            val sorted = resultItems.sortedByDescending { levelOrder(it.optString("level")) }
            sink.sendEvent("virustool.result", JSONObject()
                .put("items", JSONArray(sorted))
                .put("count", sorted.size)
                .put("running", false)
                .put("action", actionId)
                .toString())
            virusToolRunning = false
        }
    }

    fun cancelVirusTool() {
        ScanControl.requestCancel()
    }

    private fun phaseFor(actionId: String): String = when (actionId) {
        "learn" -> app.getString(R.string.vc_phase_learn)
        "stats" -> app.getString(R.string.vc_phase_stats)
        "sig" -> app.getString(R.string.vc_phase_sig)
        "recent" -> app.getString(R.string.vc_phase_recent)
        "parallel" -> app.getString(R.string.va_phase_parallel)
        "diff" -> app.getString(R.string.vc_phase_diff)
        "embedded" -> app.getString(R.string.vc_phase_embedded)
        "resultdiff" -> app.getString(R.string.va_phase_resultdiff)
        "family" -> app.getString(R.string.va_phase_family)
        "timeline" -> app.getString(R.string.va_phase_timeline)
        "netblock" -> app.getString(R.string.va_phase_netblock)
        "vpnapps" -> app.getString(R.string.va_phase_vpn)
        "attack" -> app.getString(R.string.va_phase_attack)
        "origin" -> app.getString(R.string.va_phase_origin)
        "certaudit" -> app.getString(R.string.va_phase_cert)
        "residue" -> app.getString(R.string.vc_phase_residue)
        "newproc" -> app.getString(R.string.vc_phase_newproc)
        "quarantine" -> app.getString(R.string.vc_phase_quarantine)
        "netkill" -> app.getString(R.string.va_phase_netkill)
        "priv" -> app.getString(R.string.va_phase_priv)
        "integrity" -> app.getString(R.string.va_phase_integrity)
        "lockfiles" -> app.getString(R.string.va_phase_lockfiles)
        "unlockfiles" -> app.getString(R.string.va_phase_unlockfiles)
        else -> "扫描中…"
    }

    private suspend fun runToolAction(actionId: String): List<JSONObject> = when (actionId) {
        "recent" -> recentDeepScan()
        "parallel" -> parallelScan()
        "diff" -> diffScan()
        "embedded" -> embeddedScan()
        "resultdiff" -> resultDiff()
        "family" -> familyClassify()
        "timeline" -> timelineItems()
        "netblock" -> blocklistItems()
        "vpnapps" -> vpnAppsItems()
        "attack" -> attackSurfaceItems()
        "origin" -> installerOriginItems()
        "certaudit" -> certAuditItems()
        "residue" -> residueScan()
        "newproc" -> newProcCheck()
        "learn" -> learnBaseline()
        "quarantine" -> quarantineList()
        "netkill" -> netKillItems()
        "priv" -> privilegePanel()
        "integrity" -> integrityScan()
        "lockfiles" -> lockPanel(true)
        "unlockfiles" -> lockPanel(false)
        "stats" -> threatStats()
        "sig" -> signatureItems()
        else -> listOf(uiItem("Unsupported", "未实现的工具: $actionId", level = "medium"))
    }

    private fun recentDeepScan(): List<JSONObject> {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val recent = PackageSnapshot.installedPackages(app, 0).filter { it.firstInstallTime > weekAgo }
        if (recent.isEmpty()) {
            return listOf(uiItem("Recent.None", "最近 7 天没有新安装应用", level = "low"))
        }
        val items = mutableListOf<JSONObject>()
        for (info in recent) {
            if (ScanControl.cancelled) break
            val pkg = info.packageName
            val name = info.applicationInfo?.let { PackageSnapshot.label(app, it) } ?: pkg
            val score = CombinedScore.evaluate(app, pkg)
            val verdict = VerdictArbiter.arbitrate(score.highEngineHits, score.score)
            val finalLevel = VerdictArbiter.levelFor(verdict, score.level)
            val fixable = finalLevel == ThreatLevel.CRITICAL || finalLevel == ThreatLevel.HIGH
            items.add(
                uiItem(
                    title = "Combined." + finalLevel.name + "(" + score.score + ") · " + name,
                    sub = pkg + " · 仲裁: " + verdict.name,
                    detail = score.parts.joinToString("; ").ifEmpty { "无风险证据" },
                    level = threatToStr(finalLevel),
                    suggestion = if (verdict == VerdictArbiter.Verdict.SUSPICIOUS) "仅单一证据源,降级为待观察(多引擎仲裁)" else null,
                    fixCommand = if (fixable) "am force-stop " + ShellBridge.quote(pkg) +
                        " ; pm clear " + ShellBridge.quote(pkg) else null,
                    fixLabel = if (fixable) "强停清数据" else null
                )
            )
            for (f in com.armorlab.securedroid.vscan.ApkInsights.analyze(app, pkg)) {
                if (f.level == ThreatLevel.LOW) continue
                items.add(uiItem(f.name + " · " + name, pkg, f.detail, threatToStr(f.level)))
            }
        }
        return items
    }

    private suspend fun parallelScan(): List<JSONObject> {
        val outcome = ParallelScanner.scanReports(app, workers = 4, onProgress = { done, total ->
            sink.sendEvent("virustool.progress",
                JSONObject().put("phase", "并行查杀中… $done/$total").put("running", true).put("action", "parallel").toString())
        })
        ResultDiff.save(app, outcome.reports)
        return ParallelScanner.toUiItems(outcome).map { toUiItemJson(it) }
    }

    private fun diffScan(): List<JSONObject> {
        val all = PackageSnapshot.installedPackages(app, 0)
        val changed = mutableListOf<android.content.pm.PackageInfo>()
        for (info in all) {
            if (ScanControl.cancelled) break
            val path = info.applicationInfo?.sourceDir ?: continue
            if (HashCache.isChanged(app, path, info.lastUpdateTime, java.io.File(path).length())) {
                changed.add(info)
            }
        }
        val items = mutableListOf(
            uiItem(
                "Diff.Summary", "共 " + all.size + " 个应用,其中 " + changed.size + " 个发生变更",
                detail = "仅对变更应用执行完整检测,其余复用缓存哈希结果", level = "low"
            )
        )
        var infected = 0
        for (info in changed) {
            if (ScanControl.cancelled) break
            val r = TrojanScanner.scanPackage(app, info.packageName)
            if (r.isInfected) {
                infected++
                items.add(
                    uiItem(
                        title = "Diff.Infected · " + r.appName,
                        sub = r.packageName,
                        detail = r.detections.joinToString("; ") { it.engine + ":" + it.name },
                        level = threatToStr(r.worstLevel ?: ThreatLevel.HIGH),
                        suggestion = "差异扫描命中,建议处理",
                        fixCommand = "am force-stop " + ShellBridge.quote(r.packageName) +
                            " ; pm clear " + ShellBridge.quote(r.packageName),
                        fixLabel = "强停清数据"
                    )
                )
            }
        }
        items.add(
            uiItem(
                "Diff.Result",
                "变更应用 " + changed.size + " 个,感染 " + infected + " 个",
                level = if (infected > 0) "high" else "low"
            )
        )
        return items
    }

    private fun embeddedScan(): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        for (info in PackageSnapshot.installedPackages(app, 0)) {
            if (ScanControl.cancelled) break
            val name = info.applicationInfo?.let { PackageSnapshot.label(app, it) } ?: info.packageName
            for (f in com.armorlab.securedroid.vscan.ApkInsights.analyze(app, info.packageName)) {
                if (f.level == ThreatLevel.LOW) continue
                items.add(uiItem(f.name + " · " + name, info.packageName, f.detail, threatToStr(f.level)))
            }
        }
        if (items.isEmpty()) {
            items.add(uiItem("Embedded.Clean", "全部应用未发现嵌入式载荷与结构异常", level = "low"))
        }
        return items
    }

    private suspend fun familyClassify(): List<JSONObject> {
        val outcome = ParallelScanner.scanReports(app, workers = 4, onProgress = { done, total ->
            sink.sendEvent("virustool.progress",
                JSONObject().put("phase", "家族分类扫描中… $done/$total").put("running", true).put("action", "family").toString())
        })
        return FamilyClassifier.items(app, outcome.reports).map { toUiItemJson(it) }
    }

    private suspend fun resultDiff(): List<JSONObject> {
        val outcome = ParallelScanner.scanReports(app, workers = 4, onProgress = { done, total ->
            sink.sendEvent("virustool.progress",
                JSONObject().put("phase", "对比扫描中… $done/$total").put("running", true).put("action", "resultdiff").toString())
        })
        return ResultDiff.compare(app, outcome.reports).map { toUiItemJson(it) }
    }

    private suspend fun timelineItems(): List<JSONObject> =
        Timeline.items(app).map { toUiItemJson(it) }

    private fun blocklistItems(): List<JSONObject> =
        BlocklistEngine.items(app).map { toUiItemJson(it) }

    private fun vpnAppsItems(): List<JSONObject> =
        com.armorlab.securedroid.vscan.VpnAppsScanner.items(app).map { toUiItemJson(it) }

    private fun attackSurfaceItems(): List<JSONObject> =
        AttackSurface.items(app).map { toUiItemJson(it) }

    private fun installerOriginItems(): List<JSONObject> =
        com.armorlab.securedroid.vscan.InstallerOrigin.items(app).map { toUiItemJson(it) }

    private fun certAuditItems(): List<JSONObject> =
        CertStore.items(app).map { toUiItemJson(it) }

    private fun residueScan(): List<JSONObject> =
        ResidueScanner.scan(app).map { toUiItemJson(it) }

    private fun newProcCheck(): List<JSONObject> {
        if (!ProcessBaseline.hasBaseline(app)) {
            return listOf(uiItem(
                "Baseline.None", "尚未学习进程基线",
                detail = "先点击\"学习进程基线\",之后即可检测基线外新增进程", level = "medium"))
        }
        val newOnes = ProcessBaseline.newProcesses(app)
        if (newOnes.isEmpty()) {
            return listOf(uiItem("Baseline.Clean", "未发现基线外新增进程", level = "low"))
        }
        return newOnes.map {
            uiItem("Baseline.NewProcess · " + it, it,
                detail = "进程基线外的新增进程,新型木马常驻的第一信号",
                level = "medium",
                suggestion = "确认来源;结合深度查杀的进程检测进一步判定")
        }
    }

    private fun learnBaseline(): List<JSONObject> {
        val count = ProcessBaseline.learn(app)
        return if (count < 0) {
            listOf(uiItem("Baseline.Fail", "学习失败(需要 root 或 ps 不可用)", level = "medium"))
        } else {
            listOf(uiItem("Baseline.Learned", "已学习 " + count + " 个进程为正常基线", level = "low"))
        }
    }

    private fun quarantineList(): List<JSONObject> {
        val items = Quarantine.items(app)
        if (items.isEmpty()) {
            return listOf(uiItem("Quarantine.Empty", "隔离区为空", level = "low"))
        }
        return items.map {
            uiItem(
                title = "Quarantined · " + it.originalPath.substringAfterLast('/'),
                sub = it.quarantinedPath,
                detail = "原路径: " + it.originalPath + " · 隔离于 " + TimeFmt.dateMinute(it.time),
                level = "medium",
                suggestion = "隔离文件已脱离执行路径;确认无用可销毁",
                fixCommand = "rm -f " + ShellBridge.quote(it.quarantinedPath),
                fixLabel = "销毁"
            )
        }
    }

    private fun netKillItems(): List<JSONObject> =
        NetKill.items(app).map { toUiItemJson(it) }

    private fun privilegePanel(): List<JSONObject> {
        val before = PrivilegeManager.level(app)
        val after = PrivilegeManager.probe(app)
        val caps = PrivilegeManager.capabilities(app)
        val items = mutableListOf(
            uiItem(
                "Priv.Level", "权限层级: " + after.title,
                detail = if (after == PrivLevel.ROOT)
                    "已取得系统最高权限,全部防护能力(实时监控 / 静默处置 / 底层查杀)已解锁"
                else
                    "未取得 Root 授权。请在 Magisk / KernelSU / APatch / SukiSU 管理器中为本应用授予 su 权限后重试",
                level = if (after == PrivLevel.ROOT) "low" else "medium",
                suggestion = if (before != after) "本次层级变化: " + before.title + " → " + after.title else null
            )
        )
        for (cap in Capability.entries) {
            val ok = cap in caps
            items.add(
                uiItem(
                    (if (ok) "Priv.Cap.OK · " else "Priv.Cap.NO · ") + cap.label,
                    if (cap.needRoot) "需要 Root 最高权限" else "基础能力",
                    detail = if (ok) "当前可用" else "当前层级不可用",
                    level = if (ok) "low" else "medium"
                )
            )
        }
        val audit = PrivilegeManager.auditTail(10)
        if (audit.isNotEmpty()) {
            items.add(
                uiItem(
                    "Priv.Audit", "最近 " + audit.size + " 条提权命令审计",
                    detail = audit.joinToString("\n") { (if (it.denied) "[拒绝] " else if (it.ok) "[成功] " else "[失败] ") + it.cmd.take(60) },
                    level = "low"
                )
            )
        }
        return items
    }

    private fun integrityScan(): List<JSONObject> =
        SystemIntegrity.scan(app).map { toUiItemJson(it) }

    private fun lockPanel(lock: Boolean): List<JSONObject> {
        if (!PrivilegeManager.isRoot(app)) {
            return listOf(uiItem(
                "Integrity.Lock.NeedRoot", "需要最高权限才能锁定关键文件",
                detail = "先在『最高权限』动作中完成提权", level = "medium"))
        }
        return if (lock) {
            val mech = SystemIntegrity.lockCriticalFiles(app)
            listOf(uiItem(
                if (mech != null) "Integrity.Lock.OK" else "Integrity.Lock.Fail",
                if (mech != null) "关键文件已锁定: " + mech else "锁定失败(文件不存在或 chattr/chmod 不可用)",
                detail = "锁定后 hosts 与提权组件无法被静默替换,可阻断域名劫持与 root 组件替换类攻击",
                level = if (mech != null) "low" else "high"))
        } else {
            val ok = SystemIntegrity.unlockCriticalFiles(app)
            listOf(uiItem(
                if (ok) "Integrity.Unlock.OK" else "Integrity.Unlock.Fail",
                if (ok) "已解除关键文件锁定" else "解锁失败",
                detail = "解除锁定后系统更新与手动维护不受影响,但篡改防护同时失效",
                level = if (ok) "low" else "medium"))
        }
    }

    private suspend fun threatStats(): List<JSONObject> =
        ThreatReport.stats(app).map { toUiItemJson(it) }

    private fun signatureItems(): List<JSONObject> =
        SignatureStats.items(app).map { toUiItemJson(it) }

    /* ---------- 信任列表 / 黑名单 / 策略 / 更新 ---------- */

    suspend fun getTrustList(): String = withContext(Dispatchers.Default) {
        val trusted = TrustStore.trusted(app)
        val arr = JSONArray()
        for (t in trusted) arr.put(t)
        JSONObject().put("items", arr).toString()
    }

    fun removeTrust(pkg: String) {
        try { TrustStore.setTrusted(app, pkg, false) } catch (_: Exception) {}
    }

    suspend fun getBlocklist(): String = withContext(Dispatchers.Default) {
        val patterns = BlocklistEngine.patterns(app)
        val arr = JSONArray()
        for (p in patterns) arr.put(p)
        JSONObject().put("items", arr).toString()
    }

    fun addBlocklist(pattern: String): Boolean {
        return try { BlocklistEngine.add(app, pattern) } catch (_: Exception) { false }
    }

    suspend fun getPolicy(): String = withContext(Dispatchers.Default) {
        JSONObject()
            .put("level", actionPolicyToStr(ActionPolicy.level(app)))
            .put("autoQuarantine", ActionPolicy.autoQuarantine(app))
            .toString()
    }

    fun setPolicyLevel(levelStr: String) {
        val level = when (levelStr) {
            "critical" -> ThreatLevel.CRITICAL
            "high" -> ThreatLevel.HIGH
            else -> ThreatLevel.MEDIUM
        }
        ActionPolicy.setLevel(app, level)
    }

    fun setAutoQuarantine(on: Boolean) {
        ActionPolicy.setAutoQuarantine(app, on)
    }

    suspend fun certMarkApps(): String = withContext(Dispatchers.Default) {
        val thirdParty = try {
            PackageSnapshot.installedApplications(app, 0)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .take(80)
        } catch (_: Exception) { emptyList() }
        val arr = JSONArray()
        for (info in thirdParty) {
            val name = PackageSnapshot.label(app, info)
            arr.put(JSONObject()
                .put("pkg", info.packageName)
                .put("name", name))
        }
        JSONObject().put("apps", arr).toString()
    }

    fun markCertGood(pkg: String): Boolean {
        return try {
            val hash = CertStore.certHash(app, pkg)
            if (hash != null) {
                CertStore.markGood(app, hash)
                true
            } else false
        } catch (_: Exception) { false }
    }

    suspend fun getUpdateInfo(): String = withContext(Dispatchers.Default) {
        try { ClamAvSignatures.ensureLoaded(app) } catch (_: Exception) {}
        val prefs = BridgeKeys.settings(app)
        JSONObject()
            .put("signatureCount", SignatureDatabase.size())
            .put("hashCount", ClamAvSignatures.hashCount())
            .put("byteCount", ClamAvSignatures.byteCount())
            .put("url", prefs.getString("update_url", "") ?: "")
            .put("sha", prefs.getString("update_sha", "") ?: "")
            .toString()
    }

    /** 恶意链接检测:纯本地启发式,不访问目标地址;纯字符串分析,路由已在 Default 线程 */
    fun checkUrl(url: String): String {
        val v = PhishingDetector.check(app, url)
        val arr = JSONArray()
        v.findings.forEach { arr.put(it) }
        return JSONObject()
            .put("level", v.level)
            .put("score", v.score)
            .put("findings", arr)
            .toString()
    }

    suspend fun runUpdate(url: String, sha: String?): String = withContext(Dispatchers.Default) {
        val prefs = BridgeKeys.settings(app)
        prefs.edit().putString("update_url", url).putString("update_sha", sha ?: "").apply()
        val r = FeatureUpdater.update(app, url, sha?.ifEmpty { null })
        JSONObject().put("ok", r.ok).put("message", r.message).toString()
    }

    fun exportReport() {
        BridgeScope.default.launch {
            val text = ThreatReport.build(app)
            withContext(Dispatchers.Main) {
                try {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, app.getString(R.string.vc_report_title))
                        .putExtra(Intent.EXTRA_TEXT, text)
                    activity.startActivity(Intent.createChooser(send, app.getString(R.string.vc_report_title)))
                } catch (_: Exception) {}
            }
        }
    }

    fun runFixCommand(cmd: String): Boolean {
        return try { ShellBridge.runSuChecked(cmd) } catch (_: Exception) { false }
    }

    fun openAppSettings(pkg: String) {
        if (pkg.isBlank()) return
        try {
            activity.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                )
            )
        } catch (_: Exception) {}
    }

    /* ---------- 关于 ---------- */

    fun getAbout(): String {
        val pkgInfo = try { app.packageManager.getPackageInfo(app.packageName, 0) } catch (_: Exception) { null }
        val version = pkgInfo?.versionName ?: "?"
        val code = if (pkgInfo != null) {
            if (android.os.Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode
            else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        } else 0L
        return JSONObject()
            .put("version", version)
            .put("versionCode", code)
            .put("desc", app.getString(R.string.about_desc))
            .put("license", app.getString(R.string.about_license))
            .toString()
    }

    /* ---------- 工具方法 ---------- */

    private fun uiItem(
        title: String,
        sub: String = "",
        detail: String = "",
        level: String = "low",
        suggestion: String? = null,
        uninstallPkg: String? = null,
        fixCommand: String? = null,
        fixLabel: String? = null
    ): JSONObject {
        val obj = JSONObject()
            .put("title", title)
            .put("sub", sub)
            .put("detail", detail)
            .put("level", level)
        if (suggestion != null) obj.put("suggestion", suggestion)
        if (uninstallPkg != null) obj.put("uninstallPkg", uninstallPkg)
        if (fixCommand != null) obj.put("fixCommand", fixCommand)
        if (fixLabel != null) obj.put("fixLabel", fixLabel)
        return obj
    }

    private fun toUiItemJson(item: com.armorlab.securedroid.ui.TrojanAdapter.UiItem): JSONObject =
        uiItem(
            title = item.title,
            sub = item.sub,
            detail = item.detail,
            level = threatToStr(item.level),
            suggestion = item.suggestion,
            uninstallPkg = item.uninstallPkg,
            fixCommand = item.fixCommand,
            fixLabel = item.fixLabel
        )

    private fun menuItem(id: String, title: String, sub: String): JSONObject =
        JSONObject().put("id", id).put("title", title).put("sub", sub)

    private fun resultJson(items: List<JSONObject>): String =
        JSONObject().put("items", JSONArray(items)).put("count", items.size).toString()

    private fun threatToStr(level: ThreatLevel?): String = when (level) {
        ThreatLevel.CRITICAL -> "critical"
        ThreatLevel.HIGH -> "high"
        ThreatLevel.MEDIUM -> "medium"
        else -> "low"
    }

    private fun levelOrder(level: String): Int = when (level) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        else -> 1
    }

    private fun levelLabel(level: ThreatLevel): String = app.getString(
        when (level) {
            ThreatLevel.CRITICAL -> R.string.threat_critical
            ThreatLevel.HIGH -> R.string.threat_high
            ThreatLevel.MEDIUM -> R.string.threat_medium
            else -> R.string.threat_low
        }
    )

    private fun actionPolicyToStr(level: ThreatLevel): String = when (level) {
        ThreatLevel.CRITICAL -> "critical"
        ThreatLevel.HIGH -> "high"
        else -> "medium"
    }

    private fun permLabel(basename: String): String {
        val res = when (basename) {
            "READ_SMS" -> R.string.perm_read_sms
            "SEND_SMS" -> R.string.perm_send_sms
            "RECEIVE_SMS" -> R.string.perm_receive_sms
            "RECEIVE_MMS" -> R.string.perm_receive_mms
            "READ_CONTACTS" -> R.string.perm_read_contacts
            "WRITE_CONTACTS" -> R.string.perm_write_contacts
            "READ_CALL_LOG" -> R.string.perm_read_call_log
            "WRITE_CALL_LOG" -> R.string.perm_write_call_log
            "CALL_PHONE" -> R.string.perm_call_phone
            "RECORD_AUDIO" -> R.string.perm_record_audio
            "CAMERA" -> R.string.perm_camera
            "ACCESS_FINE_LOCATION" -> R.string.perm_fine_location
            "ACCESS_COARSE_LOCATION" -> R.string.perm_coarse_location
            "ACCESS_BACKGROUND_LOCATION" -> R.string.perm_bg_location
            "READ_EXTERNAL_STORAGE" -> R.string.perm_read_storage
            "WRITE_EXTERNAL_STORAGE" -> R.string.perm_write_storage
            "READ_MEDIA_IMAGES" -> R.string.perm_read_media_images
            "READ_MEDIA_VIDEO" -> R.string.perm_read_media_video
            "READ_PHONE_STATE" -> R.string.perm_read_phone_state
            "READ_PHONE_NUMBERS" -> R.string.perm_read_phone_numbers
            "BODY_SENSORS" -> R.string.perm_body_sensors
            "SYSTEM_ALERT_WINDOW" -> R.string.perm_system_alert
            "GET_ACCOUNTS" -> R.string.perm_get_accounts
            "REQUEST_INSTALL_PACKAGES" -> R.string.perm_request_install
            "QUERY_ALL_PACKAGES" -> R.string.perm_query_all
            "PACKAGE_USAGE_STATS" -> R.string.perm_usage_stats
            else -> return basename
        }
        return app.getString(res)
    }
}
