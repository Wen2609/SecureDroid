package com.armorlab.securedroid.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivityVaultBinding
import com.armorlab.securedroid.feature.VaultCrypto
import java.io.File

/** 文件保险箱:选择文件 + 密码,AES-256-GCM 加密 / 解密 */
class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private var pendingUri: Uri? = null
    private var pendingName = "file"
    private var decryptMode = false

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingName = queryName(uri)
            askPassword()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 保险箱涉及密码与文件内容:禁止截屏 / 录屏 / 最近任务缩略图
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(R.string.tool_vault)
        binding.btnEncrypt.setOnClickListener { decryptMode = false; pick.launch(arrayOf("*/*")) }
        binding.btnDecrypt.setOnClickListener { decryptMode = true; pick.launch(arrayOf("*/*")) }
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) return c.getString(idx) ?: "file"
        }
        return "file"
    }

    private fun askPassword() {
        val input = EditText(this)
        input.hint = getString(R.string.vault_input_hint)
        AlertDialog.Builder(this)
            .setTitle(if (decryptMode) R.string.vault_btn_decrypt else R.string.vault_btn_encrypt)
            .setView(input)
            .setPositiveButton(R.string.fix_run) { _, _ ->
                val pw = input.text.toString()
                if (pw.length < 6) {
                    toast(R.string.vault_password_short)
                    return@setPositiveButton
                }
                Thread { process(pw) }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun process(password: String) {
        val uri = pendingUri ?: return
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("读取失败")
            if (bytes.size > 50L * 1024 * 1024) {
                fail(R.string.vault_too_large)
                return
            }
            val outDir = File(getExternalFilesDir(null), "vault_out").apply { mkdirs() }
            val outName = if (decryptMode) {
                pendingName.removeSuffix(".vault")
            } else {
                pendingName + ".vault"
            }
            val out = File(outDir, outName)
            val data = if (decryptMode) VaultCrypto.decrypt(password, bytes)
                       else VaultCrypto.encrypt(password, bytes)
            out.writeBytes(data)
            runOnUiThread { toast(getString(R.string.vault_done, out.absolutePath)) }
        } catch (e: Exception) {
            runOnUiThread { toast(getString(R.string.vault_fail, e.message ?: "未知错误")) }
        }
    }

    private fun fail(res: Int) {
        runOnUiThread { toast(res) }
    }

    private fun toast(res: Int) {
        toast(getString(res))
    }

    private fun toast(msg: CharSequence) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
