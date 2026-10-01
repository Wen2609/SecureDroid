package com.armorlab.securedroid.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentToolsBinding
import com.armorlab.securedroid.feature.ScanScheduler

/** 安全工具箱:仅保留需 root / 需主动触发的高价值工具(全量体检 · 网络审计 · 缓存清理) */
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

        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        binding.swDaily.isChecked = prefs.getBoolean("daily_scan_enabled", false)
        binding.swDaily.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("daily_scan_enabled", checked).apply()
            ScanScheduler.sync(requireContext())
            toast(if (checked) R.string.daily_scan_on else R.string.daily_scan_off)
        }
        binding.swSim.isChecked = prefs.getBoolean("sim_guard_enabled", false)
        binding.swSim.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sim_guard_enabled", checked).apply()
            toast(if (checked) R.string.sim_guard_on else R.string.sim_guard_off)
        }
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
