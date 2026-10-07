package com.armorlab.securedroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.armorlab.securedroid.databinding.ActivityMainBinding
import com.armorlab.securedroid.security.IntegrityGuard
import com.armorlab.securedroid.web.NativeBridge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 主界面 = 上传稿 deepseek_html_20261003_008012.html 渲染的 WebView UI。
 *
 * 首页 / 检测 / 防护三个板块、底部胶囊导航以及全部按钮、开关、列表都由该 HTML 承载,
 * 经 [NativeBridge](window.AndroidBridge) 桥接到真实原生功能:
 * 首页概览 · 病毒/木马/专项扫描 · 应用锁 · 权限审计 · 工具箱开关 · 检查更新 · 关于 · 工具入口。
 * 深层工具(病毒中心 / 深度查杀 / 网络审计 / 隐私检测 / 漏洞扫描)保留原生 Activity,由桥拉起。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bridge: NativeBridge
    private var pendingGoto: Pair<String, String>? = null
    private var backPressedAt = 0L
    private var splashShown = true

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** DNS 防护:VpnService 用户授权回调(系统授权对话框 → 结果决定开关走向) */
    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onVpnConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式:HTML 的毛玻璃/光晕背景延伸到状态栏与系统导航栏之后,
        // 页面用 env(safe-area-inset-*) 为顶部问候与底部胶囊留出安全区
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        checkIntegrity()

        // 快捷设置磁贴 / 小部件跳转直达"安全防护 → 病毒扫描"(页面加载完成后下发)
        pendingGoto = when (intent?.getStringExtra("goto")) {
            "scan" -> "security" to "scan"
            else -> null
        }

        val web = binding.webView
        bridge = NativeBridge(this, web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        web.addJavascriptInterface(bridge, "AndroidBridge")
        // 下拉刷新:重新拉取当前面板数据;转圈固定时长后收起
        // (数据加载走同步桥调用,时长足够覆盖,无需等待回调)
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.c_primary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            try {
                binding.webView.evaluateJavascript("window.__sdReady && window.__sdReady();", null)
            } catch (_: Exception) {
            }
            binding.swipeRefresh.postDelayed(
                { binding.swipeRefresh.isRefreshing = false }, 650L
            )
        }
        // UI 滚动交给页面自绘,隐藏系统滚动条与边缘光晕,避免与毛玻璃风格冲突
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        // 明确硬件加速渲染,配合页面 will-change 提升动画流畅度
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): WebResourceResponse? {
                // 应用图标服务:页面用 <img src="https://appicon.local/<包名>"> 拉取真实应用图标,
                // 由原生渲染成 WEBP 流式返回(带内存 LRU 缓存),比 JS 桥传 base64 高效得多
                val url = request?.url ?: return null
                if (url.host != "appicon.local") return null
                val pkg = url.path?.removePrefix("/").orEmpty()
                val png = appIconPng(pkg)
                return if (png != null) {
                    WebResourceResponse(
                        "image/webp", "binary", 200, "OK",
                        mapOf("Cache-Control" to "public, max-age=86400"),
                        ByteArrayInputStream(png)
                    )
                } else {
                    WebResourceResponse(
                        "image/png", "binary", 404, "Not Found",
                        mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pendingGoto?.let { (panel, seg) ->
                    pendingGoto = null
                    view?.evaluateJavascript(
                        "window.__sdGoto && window.__sdGoto('$panel','$seg');", null
                    )
                }
                view?.evaluateJavascript("window.__sdReady && window.__sdReady();", null)
                // 首帧就绪:淡出启动品牌遮罩,避免白屏闪烁
                if (splashShown) {
                    splashShown = false
                    binding.splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(280L)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction { binding.splashOverlay.visibility = View.GONE }
                        .start()
                }
            }
        }
        web.loadUrl("file:///android_asset/ui/index.html")

        // 返回键:子页面先返回上一级;非首页板块先回首页;首页时双击退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    binding.webView.evaluateJavascript(
                        "(function(){return window.__sdBack && window.__sdBack();})()",
                        object : ValueCallback<String> {
                            override fun onReceiveValue(value: String?) {
                                val handled = value?.trim()?.trim('"') == "true"
                                if (handled) return
                                // JS 返回 false 或未定义:走首页双击退出逻辑
                                val now = System.currentTimeMillis()
                                if (now - backPressedAt < 2000L) {
                                    finish()
                                } else {
                                    backPressedAt = now
                                    Toast.makeText(
                                        this@MainActivity,
                                        R.string.press_again_to_exit,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                } catch (_: Exception) {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 从深层原生页面返回后:恢复页面动画并刷新 WebUI 面板数据
        try {
            binding.webView.evaluateJavascript(
                "document.documentElement.classList.remove('page-hidden');", null
            )
            binding.webView.evaluateJavascript("window.__sdReady && window.__sdReady();", null)
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        // 转后台即暂停页面动画(光晕漂移 / 扫描脉冲):WebView 不可见时继续
        // 合成 4 个 blur(64px) 图层纯属耗电,恢复可见时 onResume 会移除标记
        try {
            binding.webView.evaluateJavascript(
                "document.documentElement.classList.add('page-hidden');", null
            )
        } catch (_: Exception) {
        }
        super.onPause()
    }

    override fun onDestroy() {
        try {
            binding.webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
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

    /* ================================================================
       DNS 防护:VpnService 授权流程(开关经桥进入,结果回推 WebUI)
       ================================================================ */

    /** 已授权时立即返回;否则拉起系统授权对话框,结果走 vpnConsent 回调 */
    fun requestVpnConsent() {
        val intent = com.armorlab.securedroid.feature.DnsGuardVpnService.prepare(this)
        if (intent == null) {
            onVpnConsentResult(true)
        } else {
            try {
                vpnConsent.launch(intent)
            } catch (_: Exception) {
                onVpnConsentResult(false)
            }
        }
    }

    private fun onVpnConsentResult(granted: Boolean) {
        if (granted) {
            com.armorlab.securedroid.feature.DnsGuardState.setEnabled(this, true)
            try {
                com.armorlab.securedroid.feature.DnsGuardVpnService.start(this)
                Toast.makeText(this, R.string.dns_guard_enabled, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        } else {
            com.armorlab.securedroid.feature.DnsGuardState.setEnabled(this, false)
            Toast.makeText(this, R.string.dns_guard_denied, Toast.LENGTH_SHORT).show()
        }
        try {
            binding.webView.evaluateJavascript(
                "window.__sdEvent && window.__sdEvent('dnsGuardState', '{\"on\":$granted}');", null
            )
        } catch (_: Exception) {
        }
    }

    /* ================================================================
       应用图标服务(供 WebUI 列表显示真实应用图标)
       ================================================================ */

    private val iconCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /** 渲染指定应用的图标为 128×128 WEBP;失败(包不存在)返回 null,由页面回退到字母占位 */
    private fun appIconPng(pkg: String): ByteArray? {
        if (pkg.isBlank() || !pkg.contains('.')) return null
        iconCache.get(pkg)?.let { return it }
        return try {
            val drawable = packageManager.getApplicationIcon(pkg)
            // 128px 覆盖 3x+ 密度下 40dp 图标的清晰度需求(96px 在 3x 屏上轻微发虚)
            val size = 128
            val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
            } else {
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { out ->
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(out))
                }
            }
            // WEBP 无损(API 30+)或高质量有损:纯色图标下体积比 PNG 小一半以上,解码更快
            val format = if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val bytes = ByteArrayOutputStream().use { out ->
                bmp.compress(format, 92, out)
                out.toByteArray()
            }
            iconCache.put(pkg, bytes)
            bytes
        } catch (_: Exception) {
            null
        }
    }
}
