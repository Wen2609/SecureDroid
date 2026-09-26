package com.armorlab.securedroid.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentToolsBinding
import com.armorlab.securedroid.feature.ClipboardGuard
import com.armorlab.securedroid.feature.LogExporter
import com.armorlab.securedroid.feature.ScanScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 安全工具箱:20 项安全工具的统一入口 */
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
        binding.rvTools.adapter = adapter
        adapter.submitList(entries())

        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
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
        ToolsAdapter.ToolEntry(getString(R.string.tool_vault), getString(R.string.tool_vault_sub), ToolsAdapter.Kind.VAULT),
        ToolsAdapter.ToolEntry(getString(R.string.tool_shred), getString(R.string.tool_shred_sub), ToolsAdapter.Kind.SHRED),
        ToolsAdapter.ToolEntry(getString(R.string.tool_cleaner), getString(R.string.tool_cleaner_sub), ToolsAdapter.Kind.CLEANER),
        ToolsAdapter.ToolEntry(getString(R.string.tool_freeze), getString(R.string.tool_freeze_sub), ToolsAdapter.Kind.FREEZE),
        ToolsAdapter.ToolEntry(getString(R.string.tool_apk), getString(R.string.tool_apk_sub), ToolsAdapter.Kind.APK_EXTRACT),
        ToolsAdapter.ToolEntry(getString(R.string.tool_ime), getString(R.string.tool_ime_sub), ToolsAdapter.Kind.IME),
        ToolsAdapter.ToolEntry(getString(R.string.tool_sos), getString(R.string.tool_sos_sub), ToolsAdapter.Kind.SOS),
        ToolsAdapter.ToolEntry(getString(R.string.tool_clipboard), getString(R.string.tool_clipboard_sub), ToolsAdapter.Kind.CLIPBOARD),
        ToolsAdapter.ToolEntry(getString(R.string.tool_log_export), getString(R.string.tool_log_export_sub), ToolsAdapter.Kind.LOG_EXPORT)
    )

    private fun open(entry: ToolsAdapter.ToolEntry) {
        val ctx = requireContext()
        val intent = Intent(ctx, when (entry.kind) {
            ToolsAdapter.Kind.FULL_AUDIT -> FullAuditActivity::class.java
            ToolsAdapter.Kind.NETWORK -> NetworkAuditActivity::class.java
            ToolsAdapter.Kind.VAULT -> VaultActivity::class.java
            ToolsAdapter.Kind.SHRED -> ShredActivity::class.java
            ToolsAdapter.Kind.CLEANER -> CleanerActivity::class.java
            ToolsAdapter.Kind.FREEZE -> FreezeActivity::class.java
            ToolsAdapter.Kind.APK_EXTRACT -> ApkExtractorActivity::class.java
            ToolsAdapter.Kind.IME -> ImeAuditActivity::class.java
            ToolsAdapter.Kind.SOS -> SosActivity::class.java
            else -> null
        })
        if (intent != null) {
            startActivity(intent)
            return
        }
        when (entry.kind) {
            ToolsAdapter.Kind.CLIPBOARD -> runClipboardCheck()
            ToolsAdapter.Kind.LOG_EXPORT -> exportLogs()
            else -> Unit
        }
    }

    private fun runClipboardCheck() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = ClipboardGuard.check(requireContext())
            withContext(Dispatchers.Main) {
                val ctx = requireContext()
                if (result.findings.isEmpty() || result.findings.contains("剪贴板为空")) {
                    Toast.makeText(ctx, R.string.clipboard_safe, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.clipboard_dialog_title)
                    .setMessage(
                        "剪贴板内容(前 80 字):\n" + result.text.take(80) +
                            "\n\n检测到敏感信息: " + result.findings.joinToString("、")
                    )
                    .setPositiveButton(R.string.clipboard_clear) { _, _ -> ClipboardGuard.clear(ctx) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun exportLogs() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val log = LogExporter.build(requireContext())
            withContext(Dispatchers.Main) {
                val send = Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_export_title))
                    .putExtra(Intent.EXTRA_TEXT, log)
                startActivity(Intent.createChooser(send, getString(R.string.log_export_title)))
            }
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
