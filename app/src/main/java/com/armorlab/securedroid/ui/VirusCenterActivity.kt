package com.armorlab.securedroid.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.databinding.ActivityVirusCenterBinding
import com.armorlab.securedroid.vscan.FeatureUpdater
import com.armorlab.securedroid.vscan.ProcessBaseline
import com.armorlab.securedroid.vscan.Quarantine
import com.armorlab.securedroid.vscan.ResidueScanner
import com.armorlab.securedroid.vscan.SignatureStats
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.vscan.ApkInsights
import com.armorlab.securedroid.vscan.CombinedScore
import com.armorlab.securedroid.vscan.HashCache
import com.armorlab.securedroid.vscan.ThreatReport
import com.armorlab.securedroid.vscan.TrustStore
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 病毒查杀中心:20 项病毒查杀能力的操作枢纽,
 * 每个动作在独立线程执行,结果统一进入分级结果列表。
 */
class VirusCenterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVirusCenterBinding
    private val adapter = TrojanAdapter()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVirusCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(R.string.vc_title)
        binding.rvList.layoutManager = LinearLayoutManager(this)

        binding.btnRecent.setOnClickListener {
            runTask(getString(R.string.vc_phase_recent)) { recentDeepScan() }
        }
        binding.btnEmbedded.setOnClickListener {
            runTask(getString(R.string.vc_phase_embedded)) { embeddedScan() }
        }
        binding.btnDiff.setOnClickListener {
            runTask(getString(R.string.vc_phase_diff)) { diffScan() }
        }
        binding.btnResidue.setOnClickListener {
            runTask(getString(R.string.vc_phase_residue)) { ResidueScanner.scan(this) }
        }
        binding.btnNewProc.setOnClickListener {
            runTask(getString(R.string.vc_phase_newproc)) { newProcCheck() }
        }
        binding.btnLearn.setOnClickListener {
            runTask(getString(R.string.vc_phase_learn)) { learnBaseline() }
        }
        binding.btnStats.setOnClickListener {
            runTask(getString(R.string.vc_phase_stats)) { runBlocking { ThreatReport.stats(this@VirusCenterActivity) } }
        }
        binding.btnSig.setOnClickListener {
            runTask(getString(R.string.vc_phase_sig)) { SignatureStats.items(this) }
        }
        binding.btnUpdate.setOnClickListener { showUpdateDialog() }
        binding.btnQuarantine.setOnClickListener {
            runTask(getString(R.string.vc_phase_quarantine)) { quarantineList() }
        }
        binding.btnTrust.setOnClickListener { showTrustDialog() }
        binding.btnReport.setOnClickListener { exportReport() }
    }

    // ---------- 各功能实现 ----------

    /** #2 + #8 + #18:最近 7 天安装应用综合深扫 */
    private fun recentDeepScan(): List<TrojanAdapter.UiItem> {
        val pm = packageManager
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val recent = pm.getInstalledPackages(0).filter { it.firstInstallTime > weekAgo }
        if (recent.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("Recent.None", "最近 7 天没有新安装应用", "", ThreatLevel.LOW, null, null))
        }
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for (info in recent) {
            val pkg = info.packageName
            val name = info.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
            val score = CombinedScore.evaluate(this, pkg)
            val fixable = score.level == ThreatLevel.CRITICAL || score.level == ThreatLevel.HIGH
            items.add(
                TrojanAdapter.UiItem(
                    title = "Combined." + score.level.name + "(" + score.score + ") · " + name,
                    sub = pkg,
                    detail = score.parts.joinToString("; ").ifEmpty { "无风险证据" },
                    level = score.level,
                    suggestion = if (fixable) "综合评分达到处置线:可强制停止并清除数据后再卸载" else null,
                    uninstallPkg = null,
                    fixCommand = if (fixable) "am force-stop '" + pkg + "' ; pm clear '" + pkg + "'" else null,
                    fixLabel = if (fixable) "强停清数据" else null
                )
            )
            for (f in ApkInsights.analyze(this, pkg)) {
                if (f.level == ThreatLevel.LOW) continue
                items.add(
                    TrojanAdapter.UiItem(
                        f.name + " · " + name, pkg, f.detail, f.level, null, null
                    )
                )
            }
        }
        return items
    }

    /** #4/#5/#6/#7:全部应用嵌入式载荷检测 */
    private fun embeddedScan(): List<TrojanAdapter.UiItem> {
        val pm = packageManager
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for (info in pm.getInstalledPackages(0)) {
            val name = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
            for (f in ApkInsights.analyze(this, info.packageName)) {
                if (f.level == ThreatLevel.LOW) continue
                items.add(TrojanAdapter.UiItem(f.name + " · " + name, info.packageName, f.detail, f.level, null, null))
            }
        }
        if (items.isEmpty()) {
            items.add(TrojanAdapter.UiItem("Embedded.Clean", "全部应用未发现嵌入式载荷与结构异常", "", ThreatLevel.LOW, null, null))
        }
        return items
    }

    /** #12:差异快速扫描(仅变更应用) */
    private fun diffScan(): List<TrojanAdapter.UiItem> {
        val pm = packageManager
        val all = pm.getInstalledPackages(0)
        val changed = mutableListOf<android.content.pm.PackageInfo>()
        for (info in all) {
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
            val r = com.armorlab.securedroid.trojan.TrojanScanner.scanPackage(this, info.packageName)
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

    /** #13:新增进程检查 */
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

    /** #10 隔离区 */
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

    // ---------- 对话框动作 ----------

    /** #14 特征库在线更新 */
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

    /** #9 信任列表管理 */
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

    /** #16 威胁报告导出 */
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

    // ---------- 通用执行器 ----------

    private fun runTask(phase: String, block: () -> List<TrojanAdapter.UiItem>) {
        if (running) return
        running = true
        setButtonsEnabled(false)
        binding.progress.isIndeterminate = true
        binding.tvPhase.text = phase
        Thread {
            val items = try {
                block()
            } catch (e: Exception) {
                listOf(
                    TrojanAdapter.UiItem("Error", e.message ?: "执行异常", "",
                        ThreatLevel.MEDIUM, null, null)
                )
            }
            runOnUiThread {
                adapter.submitList(items)
                binding.tvPhase.text = getString(R.string.vc_done_fmt, items.size)
                binding.progress.isIndeterminate = false
                setButtonsEnabled(true)
                running = false
            }
        }.start()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        listOf(
            binding.btnRecent, binding.btnEmbedded, binding.btnDiff, binding.btnResidue,
            binding.btnNewProc, binding.btnLearn, binding.btnStats, binding.btnSig,
            binding.btnUpdate, binding.btnQuarantine, binding.btnTrust, binding.btnReport
        ).forEach { it.isEnabled = enabled }
    }

    private fun toast(msg: CharSequence) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
