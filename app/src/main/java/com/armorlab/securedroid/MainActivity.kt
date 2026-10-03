package com.armorlab.securedroid

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.armorlab.securedroid.databinding.ActivityMainBinding
import com.armorlab.securedroid.security.IntegrityGuard
import com.armorlab.securedroid.ui.DashboardFragment
import com.armorlab.securedroid.ui.DetectFragment
import com.armorlab.securedroid.ui.ProtectFragment
import com.armorlab.securedroid.ui.SectionHost

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 底部入口顺序:首页 / 检测 / 防护(对应上传稿的三个 dock-item) */
    private val dockItemIds = listOf(R.id.nav_status, R.id.nav_detect, R.id.nav_protect)
    private lateinit var dockItems: List<View>
    private var selectedIndex = -1

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setupDock()
        startAurora()
        checkIntegrity()

        // 快捷设置磁贴 / 小部件跳转直达"检测"板块
        val goto = intent?.getStringExtra("goto")
        when {
            goto == "scan" -> selectTab(dockItemIds.indexOf(R.id.nav_detect))
            savedInstanceState == null -> selectTab(0)
            // 旋转/重建:FragmentManager 会恢复当前板块,只还原高亮即可
            else -> highlight(savedInstanceState.getInt(KEY_TAB, 0))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, selectedIndex)
    }

    /**
     * 校验自身安装包签名(防重打包)。
     *
     * 只在**不一致**时告警:首次运行(TOFU)与正常升级都不会打扰用户;
     * 取不到签名信息时既不告警也不放行,保持沉默但记录在诊断信息中。
     */
    private fun checkIntegrity() {
        val result = IntegrityGuard.check(this)
        if (result.shouldWarn) {
            binding.integrityBanner.visibility = View.VISIBLE
            binding.integrityBanner.text = getString(
                if (result.multipleSigners) R.string.integrity_multisigner
                else R.string.integrity_tamper
            )
        } else {
            binding.integrityBanner.visibility = View.GONE
        }
    }

    /** 绑定三个悬浮导航入口(布局就是三个等分的 LinearLayout,不再是 TabLayout) */
    private fun setupDock() {
        dockItems = dockItemIds.map { findViewById(it) }
        dockItems.forEachIndexed { index, item ->
            item.setOnClickListener { selectTab(index) }
        }
    }

    /** 只还原视觉状态(高亮 + 分隔线),不重建 Fragment */
    private fun highlight(index: Int) {
        selectedIndex = index
        dockItems.forEachIndexed { i, item -> item.isSelected = i == index }
        // 上传稿:选中项两侧的细分隔线淡出
        binding.navDivider1.visibility = if (index <= 1) View.INVISIBLE else View.VISIBLE
        binding.navDivider2.visibility = if (index >= 1) View.INVISIBLE else View.VISIBLE
    }

    private fun selectTab(index: Int) {
        val itemId = dockItemIds.getOrNull(index) ?: return
        highlight(index)
        openTab(itemId)
    }

    private fun openTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_detect -> DetectFragment()
            R.id.nav_protect -> ProtectFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    /**
     * 供页面内快捷入口跳转(首页宫格、小部件、快捷设置磁贴)。
     * [segment] 为板块内的二级分段下标,0 表示默认分段。
     */
    fun navigateTo(itemId: Int, segment: Int = 0) {
        val index = dockItemIds.indexOf(itemId)
        if (index < 0) return
        highlight(index)
        openTab(itemId)
        if (segment > 0) {
            // 提交是异步的,先落地再下发分段,避免目标 Fragment 尚未创建
            supportFragmentManager.executePendingTransactions()
            (supportFragmentManager.findFragmentById(R.id.container) as? SectionHost)?.selectSegment(segment)
        }
    }

    /**
     * 背景光晕漂移:与上传稿的 drift1-4 关键帧对应(位移 + 缩放,26-32 秒往复)。
     * 用 ViewPropertyAnimator 而不是逐帧重绘,动画落在渲染线程上,几乎不占 CPU;
     * 系统"动画时长缩放"为 0 时自动变成瞬时,尊重无障碍设置。
     */
    private fun startAurora() {
        val keyframes = listOf(
            Triple(R.id.blob1, 26_000L, floatArrayOf(46f, 34f, 1.16f)),
            Triple(R.id.blob2, 30_000L, floatArrayOf(-50f, 40f, 0.90f)),
            Triple(R.id.blob3, 28_000L, floatArrayOf(38f, -44f, 1.20f)),
            Triple(R.id.blob4, 32_000L, floatArrayOf(-34f, -30f, 0.88f))
        )
        for ((id, duration, delta) in keyframes) {
            val blob = findViewById<View>(id)
            ObjectAnimator.ofPropertyValuesHolder(
                blob,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, delta[0]),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, delta[1]),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, delta[2]),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, delta[2])
            ).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }
    }

    private companion object {
        const val KEY_TAB = "selected_tab"
    }
}
