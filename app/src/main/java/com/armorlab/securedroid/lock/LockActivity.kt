package com.armorlab.securedroid.lock

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivityLockBinding
import com.google.android.material.button.MaterialButton

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val input = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 未设置 PIN 时直接放行,避免把自己锁死
        if (!AppLockStore.hasPin(this)) {
            finish()
            return
        }
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
    }

    private fun refresh() {
        binding.tvDots.text = "●".repeat(input.length)
    }

    private fun check() {
        if (AppLockStore.verifyPin(this, input.toString())) {
            AppLockStore.markUnlocked(this)
            Toast.makeText(this, R.string.lock_unlock_ok, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.lock_wrong_pin, Toast.LENGTH_SHORT).show()
            input.clear()
            refresh()
        }
    }
}
