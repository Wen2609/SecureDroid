package com.armorlab.securedroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.FragmentDetectBinding

/** 检测板块:病毒扫描 / 木马查杀 */
class DetectFragment : Fragment(), SectionHost {

    private var _binding: FragmentDetectBinding? = null
    private val binding get() = _binding!!

    private var segment = 0
    private var shown = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        segment = savedInstanceState?.getInt(KEY_SEGMENT) ?: 0
        // 系统已恢复子 Fragment 时不要重复创建
        if (savedInstanceState != null && childFragmentManager.findFragmentById(R.id.detectContainer) != null) {
            shown = segment
        }
        binding.segDetect.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showSegment(if (checkedId == R.id.segDetectTrojan) 1 else 0)
        }
        binding.segDetect.check(if (segment == 1) R.id.segDetectTrojan else R.id.segDetectVirus)
        showSegment(segment)
    }

    override fun selectSegment(index: Int) {
        if (_binding == null) return
        binding.segDetect.check(if (index == 1) R.id.segDetectTrojan else R.id.segDetectVirus)
        showSegment(index)
    }

    private fun showSegment(index: Int) {
        segment = index
        if (shown == index) return
        shown = index
        val target: Fragment = if (index == 1) TrojanFragment() else ScannerFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.detectContainer, target, TAG)
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
        const val KEY_SEGMENT = "detect_segment"
        const val TAG = "detect_segment_fragment"
    }
}
