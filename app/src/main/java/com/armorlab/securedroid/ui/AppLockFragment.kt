package com.armorlab.securedroid.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.DialogSetPinBinding
import com.armorlab.securedroid.databinding.FragmentAppLockBinding
import com.armorlab.securedroid.lock.AppLockAdapter
import com.armorlab.securedroid.lock.AppLockStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockFragment : Fragment() {

    private var _binding: FragmentAppLockBinding? = null
    private val binding get() = _binding!!
    private val adapter = AppLockAdapter { item, locked ->
        AppLockStore.setLocked(requireContext(), item.packageName, locked)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppLockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLockApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLockApps.adapter = adapter
        binding.btnSetPin.setOnClickListener { showPinDialog() }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
        loadApps()
    }

    private fun refreshHeader() {
        val ctx = requireContext()
        binding.swDecoy.setOnCheckedChangeListener(null)
        binding.swDecoy.isChecked = AppLockStore.isDecoyEnabled(ctx)
        binding.swDecoy.setOnCheckedChangeListener { _, checked ->
            AppLockStore.setDecoyEnabled(ctx, checked)
        }
        binding.tvPinState.setText(
            if (AppLockStore.hasPin(ctx)) R.string.lock_pin_state_set
            else R.string.lock_pin_state_unset
        )
        binding.btnAccessibility.setText(
            if (isAccessibilityEnabled()) R.string.lock_accessibility_on
            else R.string.lock_accessibility_go
        )
        binding.btnAccessibility.isEnabled = !isAccessibilityEnabled()
    }

    private fun loadApps() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val self = ctx.packageName
            val items = pm.getInstalledApplications(0)
                .filter { it.packageName != self }
                .map {
                    AppLockAdapter.Item(
                        it.loadLabel(pm).toString(),
                        it.packageName,
                        AppLockStore.isLocked(ctx, it.packageName)
                    )
                }
                .sortedBy { it.appName.lowercase() }
            withContext(Dispatchers.Main) {
                adapter.submitList(items)
            }
        }
    }

    private fun showPinDialog() {
        val dialogBinding = DialogSetPinBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.lock_pin_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.lock_save) { _, _ ->
                val pin = dialogBinding.etPin.text?.toString() ?: ""
                val confirm = dialogBinding.etPinConfirm.text?.toString() ?: ""
                if (pin.length == 4 && pin == confirm) {
                    AppLockStore.setPin(requireContext(), pin)
                    Toast.makeText(requireContext(), R.string.lock_pin_saved, Toast.LENGTH_SHORT).show()
                    refreshHeader()
                } else {
                    Toast.makeText(requireContext(), R.string.lock_pin_mismatch, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val services = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.contains(requireContext().packageName)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
