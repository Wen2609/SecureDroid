package com.armorlab.securedroid.feature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 剪贴板敏感信息检查:手动触发,检测手机号 / 身份证 / 卡号 / 验证码等 */
object ClipboardGuard {

    data class Result(val text: String, val findings: List<String>)

    fun check(context: Context): Result {
        val cm = context.getSystemService(ClipboardManager::class.java)
            ?: return Result("", listOf("无法访问剪贴板"))
        val text = try {
            cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (_: Exception) { "" }
        if (text.isEmpty()) return Result("", listOf("剪贴板为空"))

        val findings = mutableListOf<String>()
        if (Regex("1[3-9]\\d{9}").containsMatchIn(text)) findings.add("手机号")
        if (Regex("\\d{17}[0-9Xx]").containsMatchIn(text)) findings.add("身份证号")
        if (Regex("\\b\\d{16,19}\\b").containsMatchIn(text)) findings.add("疑似银行卡号")
        if (Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+").containsMatchIn(text)) findings.add("邮箱地址")
        if (Regex("\\d{4,8}").matches(text)) findings.add("疑似验证码 / 数字密码")
        return Result(text, findings)
    }

    fun clear(context: Context) {
        try {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {
        }
    }
}
