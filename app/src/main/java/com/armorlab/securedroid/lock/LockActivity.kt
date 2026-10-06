package com.armorlab.securedroid.lock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivityLockBinding
import com.google.android.material.button.MaterialButton

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val input = StringBuilder()

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
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val res = v.resources
            v.updatePadding(
                left = bars.left + res.getDimensionPixelSize(R.dimen.sd_space_4),
                right = bars.right + res.getDimensionPixelSize(R.dimen.sd_space_4),
                top = bars.top + res.getDimensionPixelSize(R.dimen.sd_space_4),
                bottom = bars.bottom + res.getDimensionPixelSize(R.dimen.sd_space_4)
            )
            insets
        }

        val listener = { v: android.view.View ->
            when (v.id) {
                R.id.btnDel -> if (input.isNotEmpty()) input.deleteCharAt(input.length - 1)
                else -> {
                    val digit = (v as MaterialButton).text.toString()
                    if (input.length < 4) input.append(digit)
                }
            }
            refresh()
            if (input.length == 4) check()
        }

        val ids = intArrayOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )
        for (id in ids) findViewById<MaterialButton>(id).setOnClickListener(listener)
        binding.btnDel.setOnClickListener(listener)

        // 拦截返回键:锁定期间不允许绕过(解锁窗口仍由 60 秒逻辑控制)。
        // 使用 OnBackPressedDispatcher 替代已弃用的 onBackPressed(),
        // 在 Android 13+ 上同样生效且不触发 lint MissingSuperCall。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 故意不执行任何操作:阻止返回键关闭锁屏
            }
        })
    }

    private fun refresh() {
        binding.tvDots.text = "●".repeat(input.length)
    }

    private fun check() {
        // 防暴力破解:锁定窗口内拒绝输入
        val remaining = AppLockStore.lockoutRemainingMs(this)
        if (remaining > 0) {
            Toast.makeText(
                this,
                getString(R.string.lock_too_many_attempts, (remaining / 1000 + 1)),
                Toast.LENGTH_SHORT
            ).show()
            input.clear()
            refresh()
            return
        }
        if (AppLockStore.verifyPin(this, input.toString())) {
            AppLockStore.resetAttempts(this)
            AppLockStore.markUnlocked(this)
            Toast.makeText(this, R.string.lock_unlock_ok, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            val attempts = AppLockStore.registerFailedAttempt(this)
            // 暴力破解告警:每满 5 次错误鸣响警报
            if (attempts >= 5 && attempts % 5 == 0) {
                try {
                    android.media.RingtoneManager.getRingtone(
                        this,
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    )?.play()
                } catch (_: Exception) {
                }
                Toast.makeText(this, R.string.lock_alarm_triggered, Toast.LENGTH_LONG).show()
            }
            // 假崩溃诱骗:让偷窥者以为应用已崩溃
            if (AppLockStore.isDecoyEnabled(this) && attempts % 2 == 0) {
                showDecoyCrash()
            }
            Toast.makeText(this, R.string.lock_wrong_pin, Toast.LENGTH_SHORT).show()
            input.clear()
            refresh()
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
            try { dialog.dismiss() } catch (_: Exception) {
            }
        }, 2000)
    }
}
