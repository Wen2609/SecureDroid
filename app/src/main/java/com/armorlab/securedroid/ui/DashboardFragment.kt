package com.armorlab.securedroid.ui

import android.content.Context
import android.os.Bundle
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
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.realtime.RealtimeProtectionService
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
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
