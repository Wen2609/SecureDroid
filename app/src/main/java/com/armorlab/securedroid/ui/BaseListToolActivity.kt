package com.armorlab.securedroid.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.databinding.ActivityResultListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 列表型工具页基类:标题 + 进度条 + 结果列表(复用 TrojanAdapter) */
abstract class BaseListToolActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityResultListBinding
    private val adapter = TrojanAdapter()

    protected abstract fun titleRes(): Int
    protected abstract fun load(): List<TrojanAdapter.UiItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(titleRes())
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter
        lifecycleScope.launch(Dispatchers.IO) {
            val items = try { load() } catch (_: Exception) { emptyList<TrojanAdapter.UiItem>() }
            withContext(Dispatchers.Main) {
                adapter.submitList(items)
                binding.progress.isIndeterminate = false
            }
        }
    }
}
