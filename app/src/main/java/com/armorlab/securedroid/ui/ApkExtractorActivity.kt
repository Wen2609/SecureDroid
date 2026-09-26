package com.armorlab.securedroid.ui

import com.armorlab.securedroid.R
import com.armorlab.securedroid.scan.ThreatLevel

/** APK 提取器(root):把已安装应用的安装包导出到 /sdcard/Download */
class ApkExtractorActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_apk

    override fun load(): List<TrojanAdapter.UiItem> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(150)
            .map { ai ->
                val name = ai.loadLabel(pm).toString()
                val apk = ai.sourceDir
                TrojanAdapter.UiItem(
                    title = name,
                    sub = ai.packageName,
                    detail = apk ?: "",
                    level = ThreatLevel.LOW,
                    suggestion = null,
                    uninstallPkg = null,
                    fixCommand = if (apk != null)
                        "cp '" + apk + "' '/sdcard/Download/" + ai.packageName + ".apk' && chmod 644 '/sdcard/Download/" + ai.packageName + ".apk'"
                    else null,
                    fixLabel = "导出 APK"
                )
            }
    }
}
