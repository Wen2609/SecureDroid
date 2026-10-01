package com.armorlab.securedroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentScannerBinding
import com.armorlab.securedroid.scan.ScanAdapter

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanViewModel by viewModels()
    private val adapter = ScanAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.addItemDecoration(InsetDividerDecoration(requireContext()))
        binding.rvResults.adapter = adapter

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScanUiState.Idle -> {
                    binding.progress.isIndeterminate = false
                    binding.progress.progress = 0
                    binding.tvStatus.setText(R.string.scan_idle)
                    binding.btnStartScan.isEnabled = true
                }
                is ScanUiState.Scanning -> {
                    binding.btnStartScan.isEnabled = false
                    if (state.total > 0) {
                        binding.progress.isIndeterminate = false
                        binding.progress.max = state.total
                        binding.progress.progress = state.progress
                    } else {
                        binding.progress.isIndeterminate = true
                    }
                    binding.tvStatus.setText(R.string.scan_scanning)
                }
                is ScanUiState.Done -> {
                    binding.btnStartScan.isEnabled = true
                    binding.progress.isIndeterminate = false
                    adapter.submitList(state.results.sortedWith(
                        compareByDescending<com.armorlab.securedroid.trojan.TrojanScanner.Report> { it.isInfected }
                            .thenByDescending { it.worstLevel?.ordinal ?: -1 }
                    ))
                    val threats = state.results.count { it.isInfected }
                    binding.tvStatus.text =
                        getString(R.string.scan_done, state.results.size, threats)
                }
            }
        }

        binding.btnStartScan.setOnClickListener { viewModel.startScan() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
