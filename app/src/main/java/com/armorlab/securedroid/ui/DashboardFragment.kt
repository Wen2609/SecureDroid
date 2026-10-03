package com.armorlab.securedroid.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.core.TimeFmt
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.databinding.FragmentDashboardBinding
import com.armorlab.securedroid.lock.AppLockStore
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.trojan.ClamAvSignatures
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页:问候 + 主状态卡 + 状态概览条 + 功能宫格 + 最近活动。
 *
 * 版面结构按上传稿(deepseek_html_20261003_008012.html)实现:
 * 分数环换成"主状态卡"(图标 + 状态标题 + 上次扫描 + 立即扫描),
 * 状态概览条与"最近活动"里的数字全部来自真实数据 —— 病毒库状态、扫描样本数、
 * 生效中的防护项、待处理威胁数、高风险权限应用数,不用静态文案充数。
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        binding.tvGreeting.text = greeting()

        binding.btnOptimize.setOnClickListener {
            startActivity(Intent(ctx, FullAuditActivity::class.java))
        }
        // 上传稿:四宫格分别直达 检测·病毒扫描 / 检测·木马查杀 / 防护·工具箱 / 防护·应用锁
        binding.tileVirus.setOnClickListener { nav(R.id.nav_detect, 0) }
        binding.tileTrojan.setOnClickListener { nav(R.id.nav_detect, 1) }
        binding.tileNetwork.setOnClickListener { nav(R.id.nav_protect, 2) }
        binding.tileApplock.setOnClickListener { nav(R.id.nav_protect, 0) }

        refresh()
    }

    private fun nav(itemId: Int, segment: Int) {
        (activity as? MainActivity)?.navigateTo(itemId, segment)
    }

    /** 顶部问候:周五 · 10月2日 */
    private fun greeting(): String {
        val now = System.currentTimeMillis()
        return getString(
            R.string.dashboard_greeting_fmt,
            TimeFmt.of("EEE").format(now),
            TimeFmt.of("M月d日").format(now)
        )
    }

    private var lastRefreshAt = 0L
    private var auditCacheAt = 0L
    private var auditCacheRisky = 0

    private fun refresh() {
        val ctx = requireContext()
        // onViewCreated 紧接着 onResume,启动时会把整段 IO(数据库 + 锁列表 + 权限审计)跑两遍
        val nowAt = System.currentTimeMillis()
        if (nowAt - lastRefreshAt < 1_000L) return
        lastRefreshAt = nowAt

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(ctx).scanRecordDao()
            val records = dao.getAll()
            val threats = dao.threatCount()
            val lastScanAt = records.maxOfOrNull { it.scannedAt } ?: 0L
            val locked = try {
                AppLockStore.lockedApps(ctx).size
            } catch (_: Exception) {
                0
            }
            // 权限审计要遍历全部已安装应用,5 分钟内不重复跑
            val risky = if (nowAt - auditCacheAt >= 300_000L) {
                PermissionAuditor.riskyAppCount(ctx, 40).also {
                    auditCacheAt = nowAt
                    auditCacheRisky = it
                }
            } else {
                auditCacheRisky
            }

            withContext(Dispatchers.Main) {
                apply(ctx, records.size, threats, lastScanAt, locked, risky)
            }
        }
    }

    private fun apply(
        ctx: Context,
        scanned: Int,
        threats: Int,
        lastScanAt: Long,
        locked: Int,
        risky: Int
    ) {
        val b = _binding ?: return

        b.tvStatLib.setText(
            if (ClamAvSignatures.byteCount() + ClamAvSignatures.hashCount() > 0) {
                R.string.dashboard_stat_lib_ok
            } else {
                R.string.dashboard_stat_lib_missing
            }
        )
        b.tvStatScanned.text = java.text.NumberFormat.getIntegerInstance().format(scanned.toLong())
        b.tvStatProtected.text = getString(R.string.dashboard_stat_count_fmt, protectionCount(ctx))

        b.tvHeroMeta.text = if (lastScanAt > 0L) {
            getString(R.string.dashboard_hero_meta_fmt, relativeTime(lastScanAt))
        } else {
            getString(R.string.dashboard_hero_meta_never)
        }
        b.actScanTime.text = if (lastScanAt > 0L) {
            relativeTime(lastScanAt) + " · " + getString(
                if (threats > 0) R.string.activity_scan_bad_fmt else R.string.activity_scan_clean,
                threats
            )
        } else {
            getString(R.string.activity_scan_never)
        }
        b.actRiskTime.text = if (risky > 0) {
            getString(R.string.activity_risk_fmt, risky)
        } else {
            getString(R.string.activity_risk_none)
        }
        if (locked > 0) {
            b.actCleanTitle.setText(R.string.feature_applock)
            b.actCleanTime.text = getString(R.string.tile_applock_count, locked)
        }
    }

    /** 生效中的防护项:常驻特征引擎 + 用户打开的开关数(与工具箱里的开关同一份偏好) */
    private fun protectionCount(ctx: Context): Int {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        var n = 1
        if (prefs.getBoolean("realtime_enabled", false)) n++
        if (prefs.getBoolean("daily_scan_enabled", false)) n++
        if (prefs.getBoolean("sim_guard_enabled", false)) n++
        if (RootGuard.isRootMode(ctx)) n++
        return n
    }

    /** 今天 / 昨天 HH:mm,更早显示 M月d日 HH:mm */
    private fun relativeTime(ms: Long): String {
        val clock = TimeFmt.of("HH:mm").format(ms)
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val sameDay = today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val isYesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        return when {
            sameDay -> getString(R.string.dashboard_time_today, clock)
            isYesterday -> getString(R.string.dashboard_time_yesterday, clock)
            else -> TimeFmt.of("M月d日 HH:mm").format(ms)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
