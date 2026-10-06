package com.armorlab.securedroid.lock

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.armorlab.securedroid.R
import org.json.JSONObject

/**
 * PIN 解锁界面 — HTML/WebView 版本。
 * 保留独立 Activity + FLAG_SECURE 防截屏，UI 完全由 lock.html 渲染。
 */
class LockActivity : AppCompatActivity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 禁止截屏 / 录屏 / 最近任务缩略图:防止 PIN 被截屏木马或被旁人看到
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        // 未设置 PIN 时直接放行,避免把自己锁死
        if (!AppLockStore.hasPin(this)) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        web.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        web.addJavascriptInterface(LockJsBridge(), "AndroidBridge")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 页面加载完成
            }
        }
        web.loadUrl("file:///android_asset/ui/lock.html")

        // 拦截返回键:锁定期间不允许绕过
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 故意不执行任何操作:阻止返回键关闭锁屏
            }
        })
    }

    private inner class LockJsBridge {

        @JavascriptInterface
        fun post(action: String?, payload: String?) {
            if (action == null) return
            when (action) {
                "verifyPin" -> handleVerifyPin(payload)
            }
        }

        private fun handleVerifyPin(payload: String?) {
            val json = try { JSONObject(payload ?: "{}") } catch (_: Exception) { JSONObject() }
            val pin = json.optString("pin")

            // 防暴力破解:锁定窗口内拒绝输入
            val remaining = AppLockStore.lockoutRemainingMs(this@LockActivity)
            if (remaining > 0) {
                runOnUiThread {
                    Toast.makeText(
                        this@LockActivity,
                        getString(R.string.lock_too_many_attempts, (remaining / 1000 + 1)),
                        Toast.LENGTH_SHORT
                    ).show()
                    postResult(false, getString(R.string.lock_too_many_attempts, (remaining / 1000 + 1)))
                }
                return
            }

            if (AppLockStore.verifyPin(this@LockActivity, pin)) {
                AppLockStore.resetAttempts(this@LockActivity)
                AppLockStore.markUnlocked(this@LockActivity)
                runOnUiThread {
                    Toast.makeText(this@LockActivity, R.string.lock_unlock_ok, Toast.LENGTH_SHORT).show()
                    postResult(true, null)
                }
                // 延迟 finish,让用户看到解锁成功提示
                web.postDelayed({ finish() }, 300)
            } else {
                val attempts = AppLockStore.registerFailedAttempt(this@LockActivity)
                // 暴力破解告警:每满 5 次错误鸣响警报
                if (attempts >= 5 && attempts % 5 == 0) {
                    try {
                        android.media.RingtoneManager.getRingtone(
                            this@LockActivity,
                            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        )?.play()
                    } catch (_: Exception) {
                    }
                    runOnUiThread {
                        Toast.makeText(
                            this@LockActivity, R.string.lock_alarm_triggered, Toast.LENGTH_LONG
                        ).show()
                    }
                }
                // 假崩溃诱骗
                if (AppLockStore.isDecoyEnabled(this@LockActivity) && attempts % 2 == 0) {
                    runOnUiThread { showDecoyCrash() }
                }
                runOnUiThread {
                    postResult(false, getString(R.string.lock_wrong_pin))
                }
            }
        }

        private fun postResult(ok: Boolean, message: String?) {
            web.evaluateJavascript(
                "window.__sdPinResult && window.__sdPinResult($ok, ${
                    if (message != null) "'${message.replace("'", "\\'")}'" else "null"
                });",
                null
            )
        }
    }

    /** 假崩溃诱骗:模拟系统崩溃对话框,2 秒后自动消失回到键盘 */
    private fun showDecoyCrash() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("SecureDroid")
            .setMessage("很抱歉,\"SecureDroid\" 已停止运行。")
            .setCancelable(false)
            .show()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { dialog.dismiss() } catch (_: Exception) { }
        }, 2000)
    }

    override fun onDestroy() {
        try { web.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }
}
