package com.armorlab.securedroid.ui

import com.armorlab.securedroid.R
import com.armorlab.securedroid.feature.CleanerTool
import com.armorlab.securedroid.scan.ThreatLevel

/** 应用缓存清理(root):按大小排行,一键清理 */
class CleanerActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_cleaner

    override fun subtitleRes(): Int? = R.string.tool_cleaner_sub

    override fun load(): List<TrojanAdapter.UiItem> {
        val entries = CleanerTool.stat(this)
        if (entries.isEmpty()) {
            return listOf(TrojanAdapter.UiItem("Cache.None", "未读到应用缓存数据(需要 root 并授权)", "", ThreatLevel.MEDIUM, null, null))
        }
        val total = entries.sumOf { it.sizeKb }
        val items = mutableListOf(
            TrojanAdapter.UiItem(
                title = "Cache.Total · " + String.format(java.util.Locale.US, "%.1f MB", total / 1024.0),
                sub = "共 " + entries.size + " 个应用存在缓存",
                detail = "",
                level = ThreatLevel.MEDIUM,
                suggestion = null,
                uninstallPkg = null,
                fixCommand = "rm -rf /data/data/*/cache/* 2>/dev/null",
                fixLabel = "立即清理"
            )
        )
        for (e in entries.take(30)) {
            items.add(
                TrojanAdapter.UiItem(
                    title = e.label,
                    sub = e.pkg + " · " + String.format(java.util.Locale.US, "%.1f MB", e.sizeKb / 1024.0),
                    detail = "",
                    level = ThreatLevel.LOW,
                    suggestion = null,
                    uninstallPkg = null
                )
            )
        }
        return items
    }
}
