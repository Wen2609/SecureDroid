package com.armorlab.securedroid.ui

import com.armorlab.securedroid.R
import com.armorlab.securedroid.feature.ImeAudit
import com.armorlab.securedroid.scan.ThreatLevel

/** 输入法审计:输入法可读取全部键入内容,第三方输入法标记确认 */
class ImeAuditActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_ime

    override fun load(): List<TrojanAdapter.UiItem> {
        val list = ImeAudit.list(this)
        if (list.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("IME.None", "未读取到输入法信息", "", ThreatLevel.LOW, null, null))
        }
        return list.map { info ->
            TrojanAdapter.UiItem(
                title = (if (info.isSystem) "IME.System · " else "IME.ThirdParty · ") + info.label,
                sub = info.pkg + (if (info.enabled) " · 已启用" else " · 未启用"),
                detail = "",
                level = ImeAudit.level(info),
                suggestion = if (!info.isSystem) "第三方输入法可读取全部键入内容(含密码),请确认来源可信" else null,
                uninstallPkg = null
            )
        }
    }
}
