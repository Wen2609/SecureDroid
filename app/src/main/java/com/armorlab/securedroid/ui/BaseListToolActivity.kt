package com.armorlab.securedroid.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivityResultListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 列表型工具页基类:返回栏 + 副标题 + 状态卡 + 结果列表(复用 TrojanAdapter) */
abstract class BaseListToolActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityResultListBinding
    private val adapter = TrojanAdapter()

    protected abstract fun titleRes(): Int

    /** 顶部副标题文案;返回 null 时隐藏副标题 */
    protected open fun subtitleRes(): Int? = null

    protected abstract fun load(): List<TrojanAdapter.UiItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityResultListBinding.inflate(layoutInflater)
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
        binding.tvTitle.setText(titleRes())
        val subtitleRes = subtitleRes()
        if (subtitleRes == null) {
            binding.tvSubtitle.visibility = android.view.View.GONE
        } else {
            binding.tvSubtitle.setText(subtitleRes)
        }
        binding.btnBack.setOnClickListener { finish() }
        binding.tvStatus.setText(R.string.status_loading)
        binding.progress.isIndeterminate = true
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.setHasFixedSize(true)
        binding.rvList.adapter = adapter
        lifecycleScope.launch(Dispatchers.IO) {
            val items = try { load() } catch (_: Exception) { emptyList<TrojanAdapter.UiItem>() }
            withContext(Dispatchers.Main) {
                adapter.submitList(items)
                binding.progress.isIndeterminate = false
                binding.tvStatus.text = getString(R.string.status_done_fmt, items.size)
            }
        }
    }
}