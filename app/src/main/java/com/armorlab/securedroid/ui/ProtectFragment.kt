package com.armorlab.securedroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentProtectBinding

/** 防护板块:应用锁 / 权限审计 / 工具箱 */
class ProtectFragment : Fragment(), SectionHost {

    private var _binding: FragmentProtectBinding? = null
    private val binding get() = _binding!!

    private var segment = 0
    private var shown = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProtectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        segment = savedInstanceState?.getInt(KEY_SEGMENT) ?: 0
        if (savedInstanceState != null && childFragmentManager.findFragmentById(R.id.protectContainer) != null) {
            shown = segment
        }
        binding.segProtect.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showSegment(if (checkedId == R.id.segProtectAudit) 1 else if (checkedId == R.id.segProtectTools) 2 else 0)
        }
        binding.segProtect.check(
            when (segment) {
                1 -> R.id.segProtectAudit
                2 -> R.id.segProtectTools
                else -> R.id.segProtectLock
            }
        )
        showSegment(segment)
    }

    override fun selectSegment(index: Int) {
        if (_binding == null) return
        binding.segProtect.check(
            when (index) {
                1 -> R.id.segProtectAudit
                2 -> R.id.segProtectTools
                else -> R.id.segProtectLock
            }
        )
        showSegment(index)
    }

    private fun showSegment(index: Int) {
        segment = index
        if (shown == index) return
        shown = index
        val target: Fragment = when (index) {
            1 -> PermissionAuditFragment()
            2 -> ToolsFragment()
            else -> AppLockFragment()
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.protectContainer, target, TAG)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SEGMENT, segment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val KEY_SEGMENT = "protect_segment"
        const val TAG = "protect_segment_fragment"
    }
}
