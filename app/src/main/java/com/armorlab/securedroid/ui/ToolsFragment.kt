package com.armorlab.securedroid.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentToolsBinding
import com.armorlab.securedroid.feature.ScanScheduler
import com.armorlab.securedroid.realtime.RealtimeProtectionService
import com.armorlab.securedroid.root.RootGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 安全工具箱:防护开关(实时防护 / Root 自动处置)+ 需主动触发的高价值工具
 * (全量体检 · 网络审计 · 缓存清理)。
 *
 * 实时防护与 Root 面板原先放在首页,首页改版后只保留"看一眼就知道结果"的评分与四宫格,
 * 这些**会改变设备行为的开关**下沉到这里:开关有后果,就不该摆在首屏被误触。
 */
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!
    private val adapter = ToolsAdapter { entry -> open(entry) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvTools.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTools.addItemDecoration(InsetDividerDecoration(requireContext()))
        binding.rvTools.adapter = adapter
        adapter.submitList(entries())

        val ctx = requireContext()
        val prefs = settings(ctx)
        binding.swDaily.isChecked = prefs.getBoolean("daily_scan_enabled", false)
        binding.swDaily.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("daily_scan_enabled", checked).apply()
            ScanScheduler.sync(ctx)
            toast(if (checked) R.string.daily_scan_on else R.string.daily_scan_off)
        }
        binding.swSim.isChecked = prefs.getBoolean("sim_guard_enabled", false)
        binding.swSim.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sim_guard_enabled", checked).apply()
            toast(if (checked) R.string.sim_guard_on else R.string.sim_guard_off)
        }

        initRealtime(ctx)
        initRootPanel(ctx)
        refreshRootState()
    }

    /** ===== 实时防护 ===== */

    private fun initRealtime(ctx: Context) {
        binding.swRealtime.isChecked = settings(ctx).getBoolean(KEY_REALTIME, false)
        binding.swRealtime.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                RealtimeProtectionService.start(ctx)
            } else {
                RealtimeProtectionService.stop(ctx)
            }
            settings(ctx).edit().putBoolean(KEY_REALTIME, checked).apply()
        }
    }

    /** ===== Root 模式面板(即时检测 + 自动处置)===== */

    private fun initRootPanel(ctx: Context) {
        binding.swAutoDisinfect.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                RootGuard.setAutoDisinfect(ctx, false)
                return@setOnCheckedChangeListener
            }
            enableWithRoot(ctx) { RootGuard.setAutoDisinfect(ctx, true) }
        }
        binding.swAutoUninstall.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                RootGuard.setAutoUninstall(ctx, false)
                return@setOnCheckedChangeListener
            }
            enableWithRoot(ctx) { RootGuard.setAutoUninstall(ctx, true) }
        }
    }

    private fun enableWithRoot(ctx: Context, apply: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = RootGuard.probeRoot(ctx)
            withContext(Dispatchers.Main) {
                if (ok) {
                    RootGuard.setRootMode(ctx, true)
                    apply()
                    toast(R.string.toast_root_enabled)
                } else {
                    toast(R.string.toast_need_root)
                }
                refreshRootState()
            }
        }
    }

    private fun refreshRootState() {
        val ctx = requireContext()
        binding.tvRootState.setText(
            if (RootGuard.isRootMode(ctx)) R.string.root_mode_on else R.string.root_mode_off
        )
        // 先摘掉监听再回填状态,避免回填本身触发一次处置授权
        binding.swAutoDisinfect.setOnCheckedChangeListener(null)
        binding.swAutoDisinfect.isChecked = RootGuard.isAutoDisinfect(ctx)
        binding.swAutoUninstall.setOnCheckedChangeListener(null)
        binding.swAutoUninstall.isChecked = RootGuard.isAutoUninstall(ctx)
        initRootPanel(ctx)
    }

    private fun entries() = listOf(
        ToolsAdapter.ToolEntry(getString(R.string.tool_full_audit), getString(R.string.tool_full_audit_sub), ToolsAdapter.Kind.FULL_AUDIT),
        ToolsAdapter.ToolEntry(getString(R.string.tool_network), getString(R.string.tool_network_sub), ToolsAdapter.Kind.NETWORK),
        ToolsAdapter.ToolEntry(getString(R.string.tool_cleaner), getString(R.string.tool_cleaner_sub), ToolsAdapter.Kind.CLEANER)
    )

    private fun open(entry: ToolsAdapter.ToolEntry) {
        val target = when (entry.kind) {
            ToolsAdapter.Kind.FULL_AUDIT -> FullAuditActivity::class.java
            ToolsAdapter.Kind.NETWORK -> NetworkAuditActivity::class.java
            ToolsAdapter.Kind.CLEANER -> CleanerActivity::class.java
        }
        startActivity(Intent(requireContext(), target))
    }

    private fun toast(res: Int) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
    }

    private fun settings(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_REALTIME = "realtime_enabled"
    }
}
