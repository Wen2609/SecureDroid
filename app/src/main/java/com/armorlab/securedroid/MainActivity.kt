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
                // 由原生渲染成 PNG 流式返回(带内存 LRU 缓存),比 JS 桥传 base64 高效得多
                val url = request?.url ?: return null
                if (url.host != "appicon.local") return null
                val pkg = url.path?.removePrefix("/").orEmpty()
                val png = appIconPng(pkg)
                return if (png != null) {
                    WebResourceResponse(
                        "image/png", "binary", 200, "OK",
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

        // 返回键:非首页板块先回首页;首页时双击退出(2 秒内再按一次才退出),符合安卓基础体验
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    binding.webView.evaluateJavascript(
                        "(function(){var p=document.querySelector('.panel.is-active');" +
                            "return p?p.dataset.panel:'home';})()",
                        object : ValueCallback<String> {
                            override fun onReceiveValue(value: String?) {
                                val panel = value?.trim()?.trim('"') ?: "home"
                                if (panel != "home") {
                                    binding.webView.evaluateJavascript(
                                        "window.__sdGoto && window.__sdGoto('home', null);", null
                                    )
                                } else {
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
        // 从深层原生页面返回后刷新 WebUI 面板数据
        try {
            binding.webView.evaluateJavascript("window.__sdReady && window.__sdReady();", null)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            binding.webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    /** 供旧入口(宫格 / 小部件)兼容调用:直达 HTML 面板与二级分段 */
    fun navigateTo(panel: String, segment: String? = null) {
        val js = if (segment != null) {
            "window.__sdGoto && window.__sdGoto('$panel','$segment');"
        } else {
            "window.__sdGoto && window.__sdGoto('$panel', null);"
        }
        try {
            binding.webView.evaluateJavascript(js, null)
        } catch (_: Exception) {
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

    /* ================================================================
       应用图标服务(供 WebUI 列表显示真实应用图标)
       ================================================================ */

    private val iconCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /** 渲染指定应用的图标为 96×96 PNG;失败(包不存在)返回 null,由页面回退到字母占位 */
    private fun appIconPng(pkg: String): ByteArray? {
        if (pkg.isBlank() || !pkg.contains('.')) return null
        iconCache.get(pkg)?.let { return it }
        return try {
            val drawable = packageManager.getApplicationIcon(pkg) ?: return null
            val size = 96
            val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
            } else {
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { out ->
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(out))
                }
            }
            val bytes = ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            iconCache.put(pkg, bytes)
            bytes
        } catch (_: Exception) {
            null
        }
    }
}
