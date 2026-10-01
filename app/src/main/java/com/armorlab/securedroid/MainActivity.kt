package com.armorlab.securedroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.armorlab.securedroid.databinding.ActivityMainBinding
import com.armorlab.securedroid.ui.AppLockFragment
import com.armorlab.securedroid.ui.DashboardFragment
import com.armorlab.securedroid.ui.PermissionAuditFragment
import com.armorlab.securedroid.ui.ScannerFragment
import com.armorlab.securedroid.ui.ToolsFragment
import com.armorlab.securedroid.ui.TrojanFragment
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 入口定义来自 R.menu.bottom_nav,与顺序一一对应 */
    private val tabItemIds = mutableListOf<Int>()
    private val tabTitles = mutableListOf<String>()

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

        // 快捷设置磁贴 / 小部件跳转直达扫描页
        val goto = intent?.getStringExtra("goto")
        if (goto == "scan") {
            navigateTo(R.id.nav_scanner)
        } else if (savedInstanceState == null) {
            binding.bottomNav.getTabAt(0)?.select()
        }
    }

    /**
     * 从菜单资源构建底部标签页。
     *
     * 使用可滚动 TabLayout 而非 BottomNavigationView:后者最多支持 5 个条目,
     * 本应用有 6 个功能入口,直接使用会在布局膨胀时抛异常导致无法启动。
     * 菜单 XML 仍作为入口的唯一定义源,避免标题/图标重复维护。
     */
    private fun setupTabs() {
        // 锚点用根视图:此处只为借用菜单资源解析入口定义,弹窗本身不会显示
        val menu = PopupMenu(this, binding.root).menu
        MenuInflater(this).inflate(R.menu.bottom_nav, menu)

        tabItemIds.clear()
        tabTitles.clear()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            tabItemIds.add(item.itemId)
            tabTitles.add(item.title?.toString() ?: "")
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
            R.id.nav_scanner -> ScannerFragment()
            R.id.nav_audit -> PermissionAuditFragment()
            R.id.nav_lock -> AppLockFragment()
            R.id.nav_trojan -> TrojanFragment()
            R.id.nav_tools -> ToolsFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
        binding.toolbar.title = tabTitles.getOrNull(position) ?: getString(R.string.app_name)
    }

    /** 供页面内快捷入口跳转(仪表盘按钮等) */
    fun navigateTo(itemId: Int) {
        val index = tabItemIds.indexOf(itemId)
        if (index >= 0) binding.bottomNav.getTabAt(index)?.select()
    }
}
