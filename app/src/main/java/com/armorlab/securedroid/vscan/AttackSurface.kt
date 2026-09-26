package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.pm.PackageManager
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/** 组件暴露面分析:统计各应用 exported 组件数量,暴露面过大/暴露 Provider 重点提示 */
object AttackSurface {

    private const val FLAGS = PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        val pm = context.packageManager
        val rows = mutableListOf<Triple<String, Int, Int>>() // label+pkg, exportedCount, exportedProviders
        for (info in pm.getInstalledPackages(FLAGS)) {
            val app = info.applicationInfo ?: continue
            if ((app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue
            var exported = 0
            var providers = 0
            info.activities?.forEach { if (it.exported) exported++ }
            info.services?.forEach { if (it.exported) exported++ }
            info.receivers?.forEach { if (it.exported) exported++ }
            info.providers?.forEach { if (it.exported) providers++ }
            if (exported + providers > 0) {
                rows.add(Triple((app.loadLabel(pm).toString()) + "|" + info.packageName, exported, providers))
            }
        }
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for ((labelPkg, exported, providers) in rows.sortedByDescending { it.second + it.third * 3 }.take(40)) {
            val label = labelPkg.substringBefore('|')
            val pkg = labelPkg.substringAfter('|')
            items.add(
                TrojanAdapter.UiItem(
                    "Surface.Exposed · " + label,
                    pkg,
                    "exported 组件 " + exported + " 个" + (if (providers > 0) ",其中 Provider " + providers + " 个(数据泄露面)" else ""),
                    if (providers > 0) ThreatLevel.MEDIUM else ThreatLevel.LOW,
                    if (providers > 0) "exported Provider 可被任意应用读写,确认是否收紧导出" else null,
                    null, null, null, null
                )
            )
        }
        if (items.isEmpty()) {
            items.add(TrojanAdapter.UiItem("Surface.Clean", "第三方应用未发现暴露组件", "", ThreatLevel.LOW, null, null))
        }
        return items
    }
}
