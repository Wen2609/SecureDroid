package com.armorlab.securedroid.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivityDeepScanBinding
import com.armorlab.securedroid.deep.DeepScanEngine

/** 深度查杀:运行内存进程 + 全设备目录 + 底层分区,三阶段极致扫描 */
class DeepScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeepScanBinding
    private val adapter = TrojanAdapter()
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityDeepScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val res = v.resources
            v.updatePadding(
                left = bars.left + res.getDimensionPixelSize(R.dimen.sd_gutter),
                right = bars.right + res.getDimensionPixelSize(R.dimen.sd_gutter),
                top = bars.top + res.getDimensionPixelSize(R.dimen.sd_space_2),
                bottom = bars.bottom
            )
            insets
        }
        binding.tvTitle.setText(R.string.deep_scan_title)
        binding.btnBack.setOnClickListener { finish() }
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.setHasFixedSize(true)
        binding.rvList.adapter = adapter
        binding.btnStart.setOnClickListener { if (!running) start() }
    }

    private fun start() {
        running = true
        binding.btnStart.isEnabled = false
        binding.progress.isIndeterminate = true
        Thread {
            val items = try {
                DeepScanEngine.run(this) { phase ->
                    runOnUiThread { binding.tvPhase.text = phase }
                }
            } catch (e: Exception) {
                emptyList<TrojanAdapter.UiItem>()
            }
            runOnUiThread {
                adapter.submitList(items.sortedByDescending { it.level?.ordinal ?: -1 })
                binding.tvPhase.text = getString(R.string.deep_done_fmt, items.size)
                binding.progress.isIndeterminate = false
                binding.btnStart.isEnabled = true
                running = false
            }
        }.start()
    }
}
