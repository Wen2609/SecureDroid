package com.armorlab.securedroid.ui

import com.armorlab.securedroid.scan.ThreatLevel

/**
 * 全 HTML 化后保留的统一列表数据模型(纯数据,不再含 RecyclerView / ViewHolder UI)。
 * 扫描引擎产出 [UiItem],由 Web 桥接层序列化为 JSON 供 HTML 列表渲染。
 */
object TrojanAdapter {

    data class UiItem(
        val title: String,
        val sub: String,
        val detail: String,
        val level: ThreatLevel?,
        val suggestion: String?,
        val uninstallPkg: String? = null,
        val evidence: String? = null,
        val fixCommand: String? = null,
        val fixLabel: String? = null
    )
}