package com.armorlab.securedroid.ui

import com.armorlab.securedroid.R
import com.armorlab.securedroid.feature.FreezeTool
import com.armorlab.securedroid.scan.ThreatLevel

/** 应用冻结(root):冻结后桌面隐藏、不可运行;可随时解冻 */
class FreezeActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_freeze

    override fun load(): List<TrojanAdapter.UiItem> {
        val frozen = FreezeTool.frozenPackages()
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(150)
            .map { ai ->
                val name = ai.loadLabel(pm).toString()
                val isFrozen = frozen.contains(ai.packageName)
                TrojanAdapter.UiItem(
                    title = name,
                    sub = ai.packageName,
                    detail = if (isFrozen) "已冻结(桌面隐藏、无法运行)" else "正常运行",
                    level = if (isFrozen) ThreatLevel.MEDIUM else ThreatLevel.LOW,
                    suggestion = null,
                    uninstallPkg = null,
                    fixCommand = if (isFrozen) "pm enable '" + ai.packageName + "'"
                                 else "pm disable-user --user 0 '" + ai.packageName + "'",
                    fixLabel = if (isFrozen) "解冻" else "冻结"
                )
            }
    }
}
