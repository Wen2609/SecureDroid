package com.armorlab.securedroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentPermissionAuditBinding
import com.armorlab.securedroid.permissions.PermissionAuditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PermissionAuditFragment : Fragment() {

    private var _binding: FragmentPermissionAuditBinding? = null
    private val binding get() = _binding!!
    private val adapter = PermissionAuditAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionAuditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvAudit.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAudit.setHasFixedSize(true)
        binding.rvAudit.addItemDecoration(InsetDividerDecoration(requireContext()))
        binding.rvAudit.adapter = adapter
        runAudit()
    }

    private fun runAudit() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val results = PermissionAuditor.audit(requireContext())
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                adapter.submitList(results)
                b.tvSummary.text = getString(R.string.audit_summary, results.size)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
