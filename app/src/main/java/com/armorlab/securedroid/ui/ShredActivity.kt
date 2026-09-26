package com.armorlab.securedroid.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ActivityShredBinding
import com.armorlab.securedroid.feature.ShredTool

/** 文件粉碎器:覆写随机数据后删除所选文件(尽力而为) */
class ShredActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShredBinding
    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirm(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShredBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.setText(R.string.tool_shred)
        binding.btnPick.setOnClickListener { pick.launch(arrayOf("*/*")) }
    }

    private fun confirm(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.shred_confirm_title)
            .setMessage(R.string.shred_confirm_msg)
            .setPositiveButton(R.string.fix_run) { _, _ ->
                Thread {
                    val ok = ShredTool.shred(this, uri)
                    runOnUiThread {
                        Toast.makeText(this, if (ok) R.string.shred_done else R.string.shred_fail,
                            Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
