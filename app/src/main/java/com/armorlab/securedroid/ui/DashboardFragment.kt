package com.armorlab.securedroid.ui

import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.databinding.FragmentDashboardBinding
import com.armorlab.securedroid.feature.NetAudit
import com.armorlab.securedroid.lock.AppLockStore
import com.armorlab.securedroid.permissions.PermissionAuditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页:环形安全评分 + 一键优化 + 四宫格快捷入口。
 *
 * 评分与环形进度同源 —— 数字、弧长、状态文案表达同一件事,不做纯装饰的"假环"。
 * 四宫格的副标题都是**真实数据**(可用空间 / 待处理威胁 / 活动连接 / 已锁定应用),
 * 不用静态文案充数,这样首屏本身就能回答"我现在安全吗"。
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
        binding.btnOptimize.setOnClickListener {
            startActivity(Intent(ctx, FullAuditActivity::class.java))
        }
        binding.tileClean.setOnClickListener { startActivity(Intent(ctx, CleanerActivity::class.java)) }
        binding.tileVirus.setOnClickListener { startActivity(Intent(ctx, VirusCenterActivity::class.java)) }
        binding.tileNetwork.setOnClickListener { startActivity(Intent(ctx, NetworkAuditActivity::class.java)) }
        binding.tileApplock.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_protect, 0)
        }
        refresh()
    }

    private var scoreCacheAt = 0L
    private var scoreCacheScore = 0
    private var scoreCacheState = 0

    private fun refresh() {
        val ctx = requireContext()
        // 5 分钟缓存:权限审计要遍历全部已安装应用,不能在每次回到首页时重跑
        val fresh = scoreCacheScore == 0 || System.currentTimeMillis() - scoreCacheAt >= 300_000L
        if (!fresh) applyScore(scoreCacheScore, scoreCacheState)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val threats = AppDatabase.get(ctx).scanRecordDao().threatCount()
            val conns = try {
                NetAudit.established(ctx).size
            } catch (_: Exception) {
                0
            }
            val locked = try {
                AppLockStore.lockedApps(ctx).size
            } catch (_: Exception) {
                0
            }
            val free = try {
                StatFs(ctx.filesDir.absolutePath).availableBytes
            } catch (_: Exception) {
                0L
            }

            var score = scoreCacheScore
            var stateRes = scoreCacheState
            if (fresh) {
                val risky = PermissionAuditor.audit(ctx).count { it.score >= 40 }
                score = (100 - threats * 20 - risky * 5).coerceIn(5, 100)
                stateRes = when {
                    threats > 0 -> R.string.dashboard_state_bad
                    risky > 0 -> R.string.dashboard_state_warn
                    else -> R.string.dashboard_state_good
                }
                scoreCacheAt = System.currentTimeMillis()
                scoreCacheScore = score
                scoreCacheState = stateRes
            }

            withContext(Dispatchers.Main) {
                applyScore(score, stateRes)
                applyTiles(threats, free, locked, conns)
            }
        }
    }

    private fun applyScore(score: Int, stateRes: Int) {
        val b = _binding ?: return
        val ctx = b.root.context
        b.tvScore.text = score.toString()
        b.tvState.setText(stateRes)
        b.tvState.setTextColor(
            ContextCompat.getColor(
                ctx,
                when (stateRes) {
                    R.string.dashboard_state_bad -> R.color.c_destructive
                    R.string.dashboard_state_warn -> R.color.c_gold
                    else -> R.color.c_muted_foreground
                }
            )
        )
        b.scoreRing.progress = score
        b.scoreRing.setIndicatorColor(
            ContextCompat.getColor(
                ctx,
                when (stateRes) {
                    R.string.dashboard_state_bad -> R.color.sd_danger
                    R.string.dashboard_state_warn -> R.color.sd_warn
                    else -> R.color.c_ring
                }
            )
        )
    }

    private fun applyTiles(threats: Int, freeBytes: Long, locked: Int, conns: Int) {
        val b = _binding ?: return
        val ctx = b.root.context
        b.tvCleanSub.text = if (freeBytes > 0) {
            getString(R.string.tile_clean_free, Formatter.formatShortFileSize(ctx, freeBytes))
        } else {
            getString(R.string.tile_clean_sub)
        }
        if (threats > 0) {
            b.tvVirusSub.text = getString(R.string.tile_virus_bad, threats)
            b.tvVirusSub.setTextColor(ContextCompat.getColor(ctx, R.color.c_destructive))
        } else {
            b.tvVirusSub.setText(R.string.tile_virus_sub)
            b.tvVirusSub.setTextColor(ContextCompat.getColor(ctx, R.color.c_muted_foreground))
        }
        b.tvNetworkSub.text = getString(R.string.tile_network_count, conns)
        b.tvApplockSub.text = getString(R.string.tile_applock_count, locked)
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
