package com.armorlab.securedroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.armorlab.securedroid.databinding.ActivityMainBinding
import com.armorlab.securedroid.security.IntegrityGuard
import com.armorlab.securedroid.ui.DashboardFragment
import com.armorlab.securedroid.ui.DetectFragment
import com.armorlab.securedroid.ui.ProtectFragment
import com.armorlab.securedroid.ui.SectionHost
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 入口定义来自 R.menu.bottom_nav,与顺序一一对应 */
    private val tabItemIds = mutableListOf<Int>()

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

        setupTabs()
        checkIntegrity()

        // 快捷设置磁贴 / 小部件跳转直达"检测"板块
        val goto = intent?.getStringExtra("goto")
        if (goto == "scan") {
            navigateTo(R.id.nav_detect)
        } else if (savedInstanceState == null) {
            binding.bottomNav.getTabAt(0)?.select()
        }
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

    /**
     * 从菜单资源构建底部入口。
     *
     * 顶层只有三个板块(首页 / 检测 / 防护),二级功能收进板块内的分段控件 ——
     * 六个平级入口是上一版界面"杂乱"的根源:用户每次操作都要在六个等价选项里做一次决策。
     * 用 TabLayout 而非 BottomNavigationView 是为了避免后者 5 项硬上限,以及便于自定义指示器。
     */
    private fun setupTabs() {
        // 锚点用根视图:此处只为借用菜单资源解析入口定义,弹窗本身不会显示
        val menu = PopupMenu(this, binding.root).menu
        MenuInflater(this).inflate(R.menu.bottom_nav, menu)

        tabItemIds.clear()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            tabItemIds.add(item.itemId)
            binding.bottomNav.addTab(
                binding.bottomNav.newTab().setText(item.title).setIcon(item.icon)
            )
        }

        binding.bottomNav.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = openTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun openTab(position: Int) {
        val itemId = tabItemIds.getOrNull(position) ?: return
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
     * 供页面内快捷入口跳转(状态页的快捷卡、小部件、快捷设置磁贴)。
     * [segment] 为板块内的二级分段下标,0 表示默认分段。
     */
    fun navigateTo(itemId: Int, segment: Int = 0) {
        val index = tabItemIds.indexOf(itemId)
        if (index < 0) return
        binding.bottomNav.getTabAt(index)?.select()
        if (segment > 0) {
            // 提交是异步的,先落地再下发分段,避免目标 Fragment 尚未创建
            supportFragmentManager.executePendingTransactions()
            (supportFragmentManager.findFragmentById(R.id.container) as? SectionHost)?.selectSegment(segment)
        }
    }
}
