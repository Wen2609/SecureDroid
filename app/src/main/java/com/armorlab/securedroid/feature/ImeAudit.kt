package com.armorlab.securedroid.feature

import android.app.AlertDialog
import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.armorlab.securedroid.scan.ThreatLevel

/** 输入法安全审计:输入法可读取全部键入内容(含密码),第三方输入法需重点确认来源 */
object ImeAudit {

    data class ImeInfo(val label: String, val pkg: String, val isSystem: Boolean, val enabled: Boolean)

    fun list(context: Context): List<ImeInfo> {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        val enabledIds = imm.enabledInputMethodList.map { it.id }.toSet()
        return imm.inputMethodList.map { info ->
            val ai = info.serviceInfo.applicationInfo
            ImeInfo(
                label = info.loadLabel(context.packageManager).toString(),
                pkg = info.packageName,
                isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                enabled = enabledIds.contains(info.id)
            )
        }
    }

    /** 可选:为保险箱/锁屏密码输入建议切换到系统输入法 */
    fun suggestSystemIme(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("输入法安全提示")
            .setMessage("输入法可读取全部键入内容。在保险箱输入密码等敏感场景,建议临时切换到系统输入法(设置 → 系统输入法)。")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun level(info: ImeInfo): ThreatLevel =
        if (info.isSystem) ThreatLevel.LOW else ThreatLevel.MEDIUM
}
