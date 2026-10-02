package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.core.PackageSnapshot
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/** 安装来源检测:来源不明(侧载)的应用告警 —— 木马绕过商店审核的主要通道 */
object InstallerOrigin {

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        val pm = context.packageManager
        val items = mutableListOf<TrojanAdapter.UiItem>()
        var sideloaded = 0
        for (info in PackageSnapshot.installedPackages(context, 0)) {
            val pkg = info.packageName
            val app = info.applicationInfo ?: continue
            if ((app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val installer: String? = if (Build.VERSION.SDK_INT >= 30) {
                try { pm.getInstallSourceInfo(pkg).installingPackageName } catch (_: Exception) { null }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(pkg)
                } catch (_: Exception) { null }
            }
            val label = PackageSnapshot.label(context, app)
            if (installer == null) {
                sideloaded++
                items.add(
                    TrojanAdapter.UiItem(
                        "Virus.Sideloaded · " + label, pkg,
                        "安装来源不明(非应用商店安装)",
                        ThreatLevel.MEDIUM,
                        "侧载是木马绕过商店审核的主要通道,确认 APK 来源",
                        null, null, null, null
                    )
                )
            } else if (installer != "com.android.vending") {
                items.add(
                    TrojanAdapter.UiItem(
                        "Virus.ThirdPartyStore · " + label, pkg,
                        "安装来源: " + installer,
                        ThreatLevel.LOW, null, null, null, null
                    )
                )
            }
        }
        items.add(
            TrojanAdapter.UiItem(
                "Origin.Summary", "第三方应用 " + items.count { it.level == ThreatLevel.LOW || it.level == ThreatLevel.MEDIUM } +
                    " 个,其中侧载 " + sideloaded + " 个",
                "", ThreatLevel.LOW, null, null
            )
        )
        return items
    }
}
