package com.armorlab.securedroid.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        binding = ActivityDeepScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(R.string.deep_scan_title)
        binding.rvList.layoutManager = LinearLayoutManager(this)
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
                adapter.submitList(items)
                binding.tvPhase.text = getString(R.string.deep_done_fmt, items.size)
                binding.progress.isIndeterminate = false
                binding.btnStart.isEnabled = true
                running = false
            }
        }.start()
    }
}
