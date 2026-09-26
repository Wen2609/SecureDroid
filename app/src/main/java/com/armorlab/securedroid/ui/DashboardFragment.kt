package com.armorlab.securedroid.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.data.AppDatabase
import com.armorlab.securedroid.databinding.FragmentDashboardBinding
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.realtime.RealtimeProtectionService
import com.armorlab.securedroid.root.RootGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        binding.btnGoScan.setOnClickListener { (activity as? MainActivity)?.navigateTo(R.id.nav_scanner) }
        binding.btnGoAudit.setOnClickListener { (activity as? MainActivity)?.navigateTo(R.id.nav_audit) }
        binding.btnGoLock.setOnClickListener { (activity as? MainActivity)?.navigateTo(R.id.nav_lock) }
        binding.btnRealtime.setOnClickListener { toggleRealtime(ctx) }
        refreshRealtimeButton()
        initRootPanel(ctx)
        refreshRootState()
        refreshScore()
    }

    private fun refreshScore() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val threats = AppDatabase.get(ctx).scanRecordDao().threatCount()
            val risky = PermissionAuditor.audit(ctx).count { it.score >= 40 }
            val score = (100 - threats * 20 - risky * 5).coerceIn(5, 100)
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.tvScore.text = score.toString()
                b.tvState.setText(
                    when {
                        threats > 0 -> R.string.dashboard_state_bad
                        risky > 0 -> R.string.dashboard_state_warn
                        else -> R.string.dashboard_state_good
                    }
                )
            }
        }
    }

    private fun toggleRealtime(ctx: Context) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("realtime_enabled", false)
        if (enabled) {
            RealtimeProtectionService.stop(ctx)
            prefs.edit().putBoolean("realtime_enabled", false).apply()
        } else {
            RealtimeProtectionService.start(ctx)
            prefs.edit().putBoolean("realtime_enabled", true).apply()
        }
        refreshRealtimeButton()
    }

    /** ===== Root 模式面板(即时检测 + 自动杀毒)===== */

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
                    Toast.makeText(ctx, R.string.toast_root_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, R.string.toast_need_root, Toast.LENGTH_SHORT).show()
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
        // 先摘掉监听再回填状态,避免误触发
        binding.swAutoDisinfect.setOnCheckedChangeListener(null)
        binding.swAutoDisinfect.isChecked = RootGuard.isAutoDisinfect(ctx)
        binding.swAutoUninstall.setOnCheckedChangeListener(null)
        binding.swAutoUninstall.isChecked = RootGuard.isAutoUninstall(ctx)
        initRootPanel(ctx)
    }

    private fun refreshRealtimeButton() {
        val enabled = requireContext()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("realtime_enabled", false)
        binding.btnRealtime.setText(
            if (enabled) R.string.btn_realtime_stop else R.string.btn_realtime_start
        )
    }

    override fun onResume() {
        super.onResume()
        refreshRealtimeButton()
        refreshRootState()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
