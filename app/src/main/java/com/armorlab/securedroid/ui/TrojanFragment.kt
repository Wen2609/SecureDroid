package com.armorlab.securedroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentTrojanBinding

class TrojanFragment : Fragment() {

    private var _binding: FragmentTrojanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrojanViewModel by viewModels()
    private val adapter = TrojanAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrojanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvTrojan.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrojan.adapter = adapter

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is TrojanUiState.Idle -> {
                    setBusy(false)
                    binding.progress.isIndeterminate = false
                    binding.progress.progress = 0
                    binding.tvStatus.setText(R.string.trojan_idle)
                }
                is TrojanUiState.Scanning -> {
                    setBusy(true)
                    if (state.total > 0) {
                        binding.progress.isIndeterminate = false
                        binding.progress.max = state.total
                        binding.progress.progress = state.progress
                    } else {
                        binding.progress.isIndeterminate = true
                    }
                    binding.tvStatus.text = state.label
                }
                is TrojanUiState.Done -> {
                    setBusy(false)
                    binding.progress.isIndeterminate = false
                    adapter.submitList(state.items)
                    binding.tvStatus.text = state.summary
                }
            }
        }

        binding.btnTrojanScan.setOnClickListener { viewModel.startScan() }
        binding.btnRootkit.setOnClickListener { viewModel.startRootkit() }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnTrojanScan.isEnabled = !busy
        binding.btnRootkit.isEnabled = !busy
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
