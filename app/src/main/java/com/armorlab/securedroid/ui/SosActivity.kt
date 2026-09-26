package com.armorlab.securedroid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivitySosBinding
import com.google.android.material.textfield.TextInputEditText

/** SOS 紧急求助:向预置联系人发送求助短信 + 警报音 + 震动 */
class SosActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosBinding
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val smsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) doSend() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(R.string.tool_sos)
        binding.etPhone.setText(prefs.getString("sos_phone", ""))
        binding.etMsg.setText(prefs.getString("sos_msg", getString(R.string.sos_default_msg)))
        binding.btnSend.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            val msg = binding.etMsg.text?.toString()?.trim() ?: ""
            if (phone.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, R.string.sos_need_config, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("sos_phone", phone).putString("sos_msg", msg).apply()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) doSend()
            else smsPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun doSend() {
        try {
            val phone = binding.etPhone.text.toString().trim()
            val msg = binding.etMsg.text.toString().trim()
            SmsManager.getDefault().sendTextMessage(phone, null, msg, null, null)
            val vib = getSystemService(Vibrator::class.java)
            vib?.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                ?.play()
            Toast.makeText(this, R.string.sos_sent, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.sos_fail) + " " + e.message, Toast.LENGTH_LONG).show()
        }
    }
}
