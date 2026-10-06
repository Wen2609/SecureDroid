package com.armorlab.securedroid.ui

import com.armorlab.securedroid.R
import com.armorlab.securedroid.root.LockerDetector
import com.armorlab.securedroid.feature.SystemBaseline
import com.armorlab.securedroid.scan.ThreatLevel

/** 一键全面体检:安全基线检查 + 锁机/管理员风险汇总 */
class FullAuditActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_full_audit

    override fun subtitleRes(): Int? = R.string.tool_full_audit_sub

    override fun load(): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for (c in SystemBaseline.checks(this)) {
            items.add(
                TrojanAdapter.UiItem(
                    title = c.name,
                    sub = c.detail,
                    detail = if (c.ok) "" else c.advice,
                    level = c.level,
                    suggestion = if (c.ok) null else c.advice,
                    uninstallPkg = null
                )
            )
        }
        for (f in LockerDetector.scan(this)) {
            items.add(
                TrojanAdapter.UiItem(
                    title = f.title,
                    sub = f.sub,
                    detail = f.detail,
                    level = f.level,
                    suggestion = f.suggestion,
                    uninstallPkg = null,
                    fixCommand = f.fixCommand,
                    fixLabel = f.fixLabel
                )
            )
        }
        return items
    }
}
