package com.armorlab.securedroid.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.databinding.ActivityVirusCenterBinding
import com.armorlab.securedroid.feature.NetAudit
import com.armorlab.securedroid.root.Capability
import com.armorlab.securedroid.root.PrivLevel
import com.armorlab.securedroid.root.PrivilegeManager
import com.armorlab.securedroid.root.SystemIntegrity
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner
import com.armorlab.securedroid.vscan.ActionPolicy
import com.armorlab.securedroid.vscan.AdminSnapshot
import com.armorlab.securedroid.vscan.BatteryAware
import com.armorlab.securedroid.vscan.BlocklistEngine
import com.armorlab.securedroid.vscan.CertStore
import com.armorlab.securedroid.vscan.CombinedScore
import com.armorlab.securedroid.vscan.FeatureUpdater
import com.armorlab.securedroid.vscan.FamilyClassifier
import com.armorlab.securedroid.vscan.HashCache
import com.armorlab.securedroid.vscan.NetKill
import com.armorlab.securedroid.vscan.ParallelScanner
import com.armorlab.securedroid.vscan.ProcessBaseline
import com.armorlab.securedroid.vscan.Quarantine
import com.armorlab.securedroid.vscan.ResidueScanner
import com.armorlab.securedroid.vscan.ResultDiff
import com.armorlab.securedroid.vscan.ScanControl
import com.armorlab.securedroid.vscan.SignatureStats
import com.armorlab.securedroid.vscan.Timeline
import com.armorlab.securedroid.vscan.ThreatReport
import com.armorlab.securedroid.vscan.TrustStore
import com.armorlab.securedroid.vscan.VerdictArbiter
import java.util.Date
import kotlinx.coroutines.runBlocking

class VirusCenterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVirusCenterBinding
    private val actionsAdapter = VirusActionAdapter { action -> onAction(action) }
    private val resultsAdapter = TrojanAdapter()
    private val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVirusCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(R.string.vc_title)
        binding.rvActions.layoutManager = LinearLayoutManager(this)
        binding.rvActions.adapter = actionsAdapter
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = resultsAdapter
        actionsAdapter.submitList(menu())
        binding.btnBack.setOnClickListener {
            if (running) {
                toast(getString(R.string.vc_running_hint))
                return@setOnClickListener
            }
            showMenu()
        }
        binding.btnCancel.setOnClickListener {
            ScanControl.requestCancel()
            toast(getString(R.string.vc_cancel_requested))
        }
    }

    private fun menu(): List<VirusActionAdapter.Action> = listOf(
        VirusActionAdapter.Action("recent", getString(R.string.vc_recent), getString(R.string.vc_recent_sub)),
        VirusActionAdapter.Action("parallel", getString(R.string.va_parallel), getString(R.string.va_parallel_sub)),
        VirusActionAdapter.Action("diff", getString(R.string.vc_diff), getString(R.string.vc_diff_sub)),
        VirusActionAdapter.Action("embedded", getString(R.string.vc_embedded), getString(R.string.vc_embedded_sub)),
        VirusActionAdapter.Action("resultdiff", getString(R.string.va_resultdiff), getString(R.string.va_resultdiff_sub)),
        VirusActionAdapter.Action("family", getString(R.string.va_family), getString(R.string.va_family_sub)),
        VirusActionAdapter.Action("timeline", getString(R.string.va_timeline), getString(R.string.va_timeline_sub)),
        VirusActionAdapter.Action("newproc", getString(R.string.vc_newproc), getString(R.string.vc_newproc_sub)),
        VirusActionAdapter.Action("learn", getString(R.string.vc_learn), getString(R.string.vc_learn_sub)),
        VirusActionAdapter.Action("netblock", getString(R.string.va_netblock), getString(R.string.va_netblock_sub)),
        VirusActionAdapter.Action("netblockmgr", getString(R.string.va_netblockmgr), getString(R.string.va_netblockmgr_sub)),
        VirusActionAdapter.Action("netkill", getString(R.string.va_netkill), getString(R.string.va_netkill_sub)),
        VirusActionAdapter.Action("vpnapps", getString(R.string.va_vpn), getString(R.string.va_vpn_sub)),
        VirusActionAdapter.Action("attack", getString(R.string.va_attack), getString(R.string.va_attack_sub)),
        VirusActionAdapter.Action("origin", getString(R.string.va_origin), getString(R.string.va_origin_sub)),
        VirusActionAdapter.Action("certaudit", getString(R.string.va_cert), getString(R.string.va_cert_sub)),
        VirusActionAdapter.Action("certmark", getString(R.string.va_certmark), getString(R.string.va_certmark_sub)),
        VirusActionAdapter.Action("residue", getString(R.string.vc_residue), getString(R.string.vc_residue_sub)),
        VirusActionAdapter.Action("quarantine", getString(R.string.vc_quarantine), getString(R.string.vc_quarantine_sub)),
        VirusActionAdapter.Action("stats", getString(R.string.vc_stats), getString(R.string.vc_stats_sub)),
        VirusActionAdapter.Action("sig", getString(R.string.vc_sig), getString(R.string.vc_sig_sub)),
        VirusActionAdapter.Action("policy", getString(R.string.va_policy), getString(R.string.va_policy_sub)),
        VirusActionAdapter.Action("autq", getString(R.string.va_autq), getString(R.string.va_autq_sub)),
        VirusActionAdapter.Action("update", getString(R.string.vc_update), getString(R.string.vc_update_sub)),
        VirusActionAdapter.Action("priv", getString(R.string.va_priv), getString(R.string.va_priv_sub)),
        VirusActionAdapter.Action("integrity", getString(R.string.va_integrity), getString(R.string.va_integrity_sub)),
        VirusActionAdapter.Action("lockfiles", getString(R.string.va_lockfiles), getString(R.string.va_lockfiles_sub)),
        VirusActionAdapter.Action("unlockfiles", getString(R.string.va_unlockfiles), getString(R.string.va_unlockfiles_sub)),
        VirusActionAdapter.Action("trust", getString(R.string.vc_trust), getString(R.string.vc_trust_sub)),
        VirusActionAdapter.Action("report", getString(R.string.vc_report), getString(R.string.vc_report_sub))
    )

    private fun onAction(action: VirusActionAdapter.Action) {
        when (action.id) {
            "update" -> showUpdateDialog()
            "trust" -> showTrustDialog()
            "certmark" -> showCertMarkDialog()
            "netblockmgr" -> showBlocklistDialog()
            "autq" -> toggleAutoQuarantine()
            "cancel" -> { ScanControl.requestCancel(); toast(getString(R.string.vc_cancel_requested)) }
            "learn" -> runTask(getString(R.string.vc_phase_learn)) { learnBaseline() }
            "stats" -> runTask(getString(R.string.vc_phase_stats)) { runBlocking { ThreatReport.stats(this@VirusCenterActivity) } }
            "sig" -> runTask(getString(R.string.vc_phase_sig)) { SignatureStats.items(this) }
            "report" -> exportReport()
            "recent" -> runTask(getString(R.string.vc_phase_recent)) { recentDeepScan() }
            "parallel" -> runTask(getString(R.string.va_phase_parallel)) { parallelScan() }
            "diff" -> runTask(getString(R.string.vc_phase_diff)) { diffScan() }
            "embedded" -> runTask(getString(R.string.vc_phase_embedded)) { embeddedScan() }
            "resultdiff" -> runTask(getString(R.string.va_phase_resultdiff)) { resultDiff() }
            "family" -> runTask(getString(R.string.va_phase_family)) { familyClassify() }
            "timeline" -> runTask(getString(R.string.va_phase_timeline)) { runBlocking { Timeline.items(this@VirusCenterActivity) } }
            "netblock" -> runTask(getString(R.string.va_phase_netblock)) { BlocklistEngine.items(this) }
            "vpnapps" -> runTask(getString(R.string.va_phase_vpn)) { com.armorlab.securedroid.vscan.VpnAppsScanner.items(this) }
            "attack" -> runTask(getString(R.string.va_phase_attack)) { com.armorlab.securedroid.vscan.AttackSurface.items(this) }
            "origin" -> runTask(getString(R.string.va_phase_origin)) { com.armorlab.securedroid.vscan.InstallerOrigin.items(this) }
            "certaudit" -> runTask(getString(R.string.va_phase_cert)) { CertStore.items(this) }
            "residue" -> runTask(getString(R.string.vc_phase_residue)) { ResidueScanner.scan(this) }
            "newproc" -> runTask(getString(R.string.vc_phase_newproc)) { newProcCheck() }
            "quarantine" -> runTask(getString(R.string.vc_phase_quarantine)) { quarantineList() }
            "netkill" -> runTask(getString(R.string.va_phase_netkill)) { NetKill.items(this) }
            "priv" -> runTask(getString(R.string.va_phase_priv)) { privilegePanel() }
            "integrity" -> runTask(getString(R.string.va_phase_integrity)) { SystemIntegrity.scan(this) }
            "lockfiles" -> runTask(getString(R.string.va_phase_lockfiles)) { lockPanel(true) }
            "unlockfiles" -> runTask(getString(R.string.va_phase_unlockfiles)) { lockPanel(false) }
        }
    }

    // ---------- 功能实现 ----------

    private fun recentDeepScan(): List<TrojanAdapter.UiItem> {
        ScanControl.reset()
        val pm = packageManager
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val recent = pm.getInstalledPackages(0).filter { it.firstInstallTime > weekAgo }
        if (recent.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("Recent.None", "最近 7 天没有新安装应用", "", ThreatLevel.LOW, null, null))
        }
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for (info in recent) {
            if (ScanControl.cancelled) break
            val pkg = info.packageName
            val name = info.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
            val score = CombinedScore.evaluate(this, pkg)
            // #18 多引擎仲裁:单一引擎高危降级为待观察
            val verdict = VerdictArbiter.arbitrate(score.highEngineHits, score.score)
            val finalLevel = VerdictArbiter.levelFor(verdict, score.level)
            val fixable = finalLevel == ThreatLevel.CRITICAL || finalLevel == ThreatLevel.HIGH
            items.add(
                TrojanAdapter.UiItem(
                    title = "Combined." + finalLevel.name + "(" + score.score + ") · " + name,
                    sub = pkg + " · 仲裁: " + verdict.name,
                    detail = score.parts.joinToString("; ").ifEmpty { "无风险证据" },
                    level = finalLevel,
                    suggestion = if (verdict == VerdictArbiter.Verdict.SUSPICIOUS) "仅单一证据源,降级为待观察(多引擎仲裁)" else null,
                    uninstallPkg = null,
                    fixCommand = if (fixable) "am force-stop '" + pkg + "' ; pm clear '" + pkg + "'" else null,
                    fixLabel = if (fixable) "强停清数据" else null
                )
            )
            for (f in com.armorlab.securedroid.vscan.ApkInsights.analyze(this, pkg)) {
                if (f.level == ThreatLevel.LOW) continue
                items.add(TrojanAdapter.UiItem(f.name + " · " + name, pkg, f.detail, f.level, null, null))
            }
        }
        return items
    }

    private fun parallelScan(): List<TrojanAdapter.UiItem> {
        val outcome = ParallelScanner.scanReports(this, 4, { done, total ->
            runOnUiThread { binding.tvPhase.text = "并行查杀中… " + done + "/" + total }
        })
        ResultDiff.save(this, outcome.reports)
        return ParallelScanner.toUiItems(outcome)
    }

    private fun diffScan(): List<TrojanAdapter.UiItem> {
        ScanControl.reset()
        val pm = packageManager
        val all = pm.getInstalledPackages(0)
        val changed = mutableListOf<android.content.pm.PackageInfo>()
        for (info in all) {
            if (ScanControl.cancelled) break
            val path = info.applicationInfo?.sourceDir ?: continue
            if (HashCache.isChanged(this, path, info.lastUpdateTime, java.io.File(path).length())) {
                changed.add(info)
            }
        }
        val items = mutableListOf(
            TrojanAdapter.UiItem(
                "Diff.Summary", "共 " + all.size + " 个应用,其中 " + changed.size + " 个发生变更",
                "仅对变更应用执行完整检测,其余复用缓存哈希结果", ThreatLevel.LOW, null, null
            )
        )
        var infected = 0
        for (info in changed) {
            if (ScanControl.cancelled) break
            val r = TrojanScanner.scanPackage(this, info.packageName)
            if (r.isInfected) {
                infected++
                items.add(
                    TrojanAdapter.UiItem(
                        "Diff.Infected · " + r.appName, r.packageName,
                        r.detections.joinToString("; ") { it.engine + ":" + it.name },
                        r.worstLevel ?: ThreatLevel.HIGH,
                        "差异扫描命中,建议处理", null,
                        "am force-stop '" + r.packageName + "' ; pm clear '" + r.packageName + "'",
                        "强停清数据"
                    )
                )
            }
        }
        items.add(
            TrojanAdapter.UiItem(
                "Diff.Result", "变更应用 " + changed.size + " 个,感染 " + infected + " 个",
                "", if (infected > 0) ThreatLevel.HIGH else ThreatLevel.LOW, null, null
            )
        )
        return items
    }

    private fun embeddedScan(): List<TrojanAdapter.UiItem> {
        val pm = packageManager
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for (info in pm.getInstalledPackages(0)) {
            if (ScanControl.cancelled) break
            val name = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
            for (f in com.armorlab.securedroid.vscan.ApkInsights.analyze(this, info.packageName)) {
                if (f.level == ThreatLevel.LOW) continue
                items.add(TrojanAdapter.UiItem(f.name + " · " + name, info.packageName, f.detail, f.level, null, null))
            }
        }
        if (items.isEmpty()) {
            items.add(TrojanAdapter.UiItem("Embedded.Clean", "全部应用未发现嵌入式载荷与结构异常", "", ThreatLevel.LOW, null, null))
        }
        return items
    }

    private fun familyClassify(): List<TrojanAdapter.UiItem> {
        val outcome = ParallelScanner.scanReports(this, 4, { done, total ->
            runOnUiThread { binding.tvPhase.text = "家族分类扫描中… " + done + "/" + total }
        })
        return FamilyClassifier.items(this, outcome.reports)
    }

    private fun resultDiff(): List<TrojanAdapter.UiItem> {
        val outcome = ParallelScanner.scanReports(this, 4, { done, total ->
            runOnUiThread { binding.tvPhase.text = "对比扫描中… " + done + "/" + total }
        })
        return ResultDiff.compare(this, outcome.reports)
    }

    /** 最高权限面板:主动探测提权,展示层级与能力矩阵 */
    private fun privilegePanel(): List<TrojanAdapter.UiItem> {
        val before = PrivilegeManager.level(this)
        val after = PrivilegeManager.probe(this)
        val caps = PrivilegeManager.capabilities(this)
        val items = mutableListOf<TrojanAdapter.UiItem>()
        items.add(
            TrojanAdapter.UiItem(
                "Priv.Level", "权限层级: " + after.title,
                if (after == PrivLevel.ROOT)
                    "已取得系统最高权限,全部防护能力(实时监控 / 静默处置 / 底层查杀)已解锁"
                else
                    "未取得 Root 授权。请在 Magisk / KernelSU / APatch / SukiSU 管理器中为本应用授予 su 权限后重试",
                if (after == PrivLevel.ROOT) ThreatLevel.LOW else ThreatLevel.MEDIUM,
                if (before != after) "本次层级变化: " + before.title + " → " + after.title else null,
                null
            )
        )
        for (cap in Capability.entries) {
            val ok = cap in caps
            items.add(
                TrojanAdapter.UiItem(
                    (if (ok) "Priv.Cap.OK · " else "Priv.Cap.NO · ") + cap.label,
                    if (cap.needRoot) "需要 Root 最高权限" else "基础能力",
                    if (ok) "当前可用" else "当前层级不可用",
                    if (ok) ThreatLevel.LOW else ThreatLevel.MEDIUM,
                    null, null
                )
            )
        }
        val audit = PrivilegeManager.auditTail(10)
        if (audit.isNotEmpty()) {
            items.add(
                TrojanAdapter.UiItem(
                    "Priv.Audit", "最近 " + audit.size + " 条提权命令审计",
                    audit.joinToString("\n") { (if (it.denied) "[拒绝] " else if (it.ok) "[成功] " else "[失败] ") + it.cmd.take(60) },
                    ThreatLevel.LOW, null, null
                )
            )
        }
        return items
    }

    /** 关键文件锁定 / 解锁 */
    private fun lockPanel(lock: Boolean): List<TrojanAdapter.UiItem> {
        if (!PrivilegeManager.isRoot(this)) {
            return listOf(
                TrojanAdapter.UiItem(
                    "Integrity.Lock.NeedRoot", "需要最高权限才能锁定关键文件",
                    "先在『最高权限』动作中完成提权", ThreatLevel.MEDIUM, null, null
                )
            )
        }
        return if (lock) {
            val mech = SystemIntegrity.lockCriticalFiles(this)
            listOf(
                TrojanAdapter.UiItem(
                    if (mech != null) "Integrity.Lock.OK" else "Integrity.Lock.Fail",
                    if (mech != null) "关键文件已锁定: " + mech else "锁定失败(文件不存在或 chattr/chmod 不可用)",
                    "锁定后 hosts 与提权组件无法被静默替换,可阻断域名劫持与 root 组件替换类攻击",
                    if (mech != null) ThreatLevel.LOW else ThreatLevel.HIGH, null, null
                )
            )
        } else {
            val ok = SystemIntegrity.unlockCriticalFiles(this)
            listOf(
                TrojanAdapter.UiItem(
                    if (ok) "Integrity.Unlock.OK" else "Integrity.Unlock.Fail",
                    if (ok) "已解除关键文件锁定" else "解锁失败",
                    "解除锁定后系统更新与手动维护不受影响,但篡改防护同时失效",
                    if (ok) ThreatLevel.LOW else ThreatLevel.MEDIUM, null, null
                )
            )
        }
    }

    private fun newProcCheck(): List<TrojanAdapter.UiItem> {
        if (!ProcessBaseline.hasBaseline(this)) {
            return listOf(
                TrojanAdapter.UiItem(
                    "Baseline.None", "尚未学习进程基线",
                    "先点击\"学习进程基线\",之后即可检测基线外新增进程", ThreatLevel.MEDIUM, null, null
                )
            )
        }
        val newOnes = ProcessBaseline.newProcesses(this)
        if (newOnes.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("Baseline.Clean", "未发现基线外新增进程", "", ThreatLevel.LOW, null, null))
        }
        return newOnes.map {
            TrojanAdapter.UiItem("Baseline.NewProcess · " + it, it,
                "进程基线外的新增进程,新型木马常驻的第一信号",
                ThreatLevel.MEDIUM, "确认来源;结合深度查杀的进程检测进一步判定", null, null)
        }
    }

    private fun learnBaseline(): List<TrojanAdapter.UiItem> {
        val count = ProcessBaseline.learn(this)
        return if (count < 0) {
            listOf(TrojanAdapter.UiItem("Baseline.Fail", "学习失败(需要 root 或 ps 不可用)", "", ThreatLevel.MEDIUM, null, null))
        } else {
            listOf(TrojanAdapter.UiItem("Baseline.Learned", "已学习 " + count + " 个进程为正常基线", "", ThreatLevel.LOW, null, null))
        }
    }

    private fun quarantineList(): List<TrojanAdapter.UiItem> {
        val items = Quarantine.items(this)
        if (items.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("Quarantine.Empty", "隔离区为空", "", ThreatLevel.LOW, null, null))
        }
        return items.map {
            TrojanAdapter.UiItem(
                "Quarantined · " + it.originalPath.substringAfterLast('/'),
                it.quarantinedPath,
                "原路径: " + it.originalPath + " · 隔离于 " + fmt.format(Date(it.time)),
                ThreatLevel.MEDIUM,
                "隔离文件已脱离执行路径;确认无用可销毁",
                null, null,
                "rm -f '" + it.quarantinedPath + "'",
                "销毁"
            )
        }
    }

    // ---------- 对话框 ----------

    private fun showUpdateDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etUrl = EditText(this).apply { hint = getString(R.string.vc_update_url) }
        val etSha = EditText(this).apply { hint = getString(R.string.vc_update_sha) }
        container.addView(etUrl)
        container.addView(etSha)
        AlertDialog.Builder(this)
            .setTitle(R.string.vc_update)
            .setView(container)
            .setPositiveButton(R.string.vc_update_btn) { _, _ ->
                val url = etUrl.text.toString().trim()
                val sha = etSha.text.toString().trim()
                if (url.isEmpty()) {
                    toast(getString(R.string.vc_update_need_url))
                    return@setPositiveButton
                }
                runTask(getString(R.string.vc_phase_update)) {
                    val r = FeatureUpdater.update(this, url, sha.ifEmpty { null })
                    listOf(
                        TrojanAdapter.UiItem(
                            if (r.ok) "Update.OK" else "Update.Fail", r.message, "",
                            if (r.ok) ThreatLevel.LOW else ThreatLevel.HIGH, null, null
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTrustDialog() {
        val trusted = TrustStore.trusted(this)
        if (trusted.isEmpty()) {
            toast(getString(R.string.vc_trust_none))
            return
        }
        val arr = trusted.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.vc_trust_title)
            .setItems(arr) { _, which ->
                TrustStore.setTrusted(this, arr[which], false)
                toast(getString(R.string.vc_trust_removed) + " " + arr[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCertMarkDialog() {
        val pm = packageManager
        val thirdParty = pm.getInstalledApplications(0)
            .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .take(80)
        if (thirdParty.isEmpty()) {
            toast(getString(R.string.vc_trust_none))
            return
        }
        val labels = thirdParty.map { it.loadLabel(pm).toString() + " (" + it.packageName + ")" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.va_certmark)
            .setItems(labels) { _, which ->
                val pkg = thirdParty[which].packageName
                val hash = CertStore.certHash(this, pkg)
                if (hash == null) {
                    toast(getString(R.string.vc_cert_fail))
                } else {
                    CertStore.markGood(this, hash)
                    toast(getString(R.string.vc_cert_marked) + " " + hash.take(16) + "…")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBlocklistDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val et = EditText(this).apply { hint = getString(R.string.va_blocklist_hint) }
        container.addView(et)
        val current = BlocklistEngine.patterns(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.va_netblockmgr)
            .setMessage(getString(R.string.va_blocklist_current) + " " +
                (current.joinToString(", ").ifEmpty { "(仅预置模式)" }))
            .setView(container)
            .setPositiveButton(R.string.vc_update_btn) { _, _ ->
                if (BlocklistEngine.add(this, et.text.toString())) {
                    toast(getString(R.string.vc_blocklist_added))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleAutoQuarantine() {
        val newValue = !ActionPolicy.autoQuarantine(this)
        ActionPolicy.setAutoQuarantine(this, newValue)
        toast(getString(R.string.va_autq_toggled) + (if (newValue) "开" else "关"))
    }

    private fun showPolicyDialog() {
        val options = arrayOf(
            getString(R.string.va_policy_critical),
            getString(R.string.va_policy_high),
            getString(R.string.va_policy_medium)
        )
        val levels = listOf(ThreatLevel.CRITICAL, ThreatLevel.HIGH, ThreatLevel.MEDIUM)
        val current = ActionPolicy.level(this)
        val checked = levels.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.va_policy)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                ActionPolicy.setLevel(this, levels[which])
                dialog.dismiss()
                toast(getString(R.string.va_policy_set) + options[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportReport() {
        Thread {
            val text = runBlocking { ThreatReport.build(this@VirusCenterActivity) }
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.vc_report_title))
                    .putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, getString(R.string.vc_report_title)))
            }
        }.start()
    }

    // ---------- 通用执行 ----------

    private fun runTask(phase: String, block: () -> List<TrojanAdapter.UiItem>) {
        if (running) return
        running = true
        ScanControl.reset()
        binding.menuState.visibility = View.GONE
        binding.runState.visibility = View.VISIBLE
        binding.progress.isIndeterminate = true
        binding.tvPhase.text = phase
        Thread {
            val items = try {
                block()
            } catch (e: Exception) {
                listOf(TrojanAdapter.UiItem("Error", e.message ?: "执行异常", "", ThreatLevel.MEDIUM, null, null))
            }
            runOnUiThread {
                resultsAdapter.submitList(items.sortedByDescending { it.level?.ordinal ?: -1 })
                binding.tvPhase.text = getString(R.string.vc_done_fmt, items.size)
                binding.progress.isIndeterminate = false
                running = false
            }
        }.start()
    }

    private fun showMenu() {
        binding.runState.visibility = View.GONE
        binding.menuState.visibility = View.VISIBLE
    }

    private fun toast(msg: CharSequence) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
