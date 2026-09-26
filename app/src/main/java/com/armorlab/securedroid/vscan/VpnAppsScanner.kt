package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.Intent
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/** VPN / 流量劫持面检测:枚举声明 VpnService 的应用(可截获全部流量) */
object VpnAppsScanner {

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        val pm = context.packageManager
        val services = pm.queryIntentServices(
            Intent("android.net.VpnService"), 0
        ) ?: return listOf(
            TrojanAdapter.UiItem("Vpn.QueryFail", "无法查询 VpnService 服务", "", ThreatLevel.MEDIUM, null, null)
        )
        if (services.isEmpty()) {
            return listOf(
                TrojanAdapter.UiItem("Vpn.Clean", "未发现声明 VPN 能力的应用", "", ThreatLevel.LOW, null, null)
            )
        }
        return services.map { s ->
            val app = s.serviceInfo.applicationInfo
            val label = try { app?.loadLabel(pm)?.toString() } catch (_: Exception) { null }
            val isSystem = ((app?.flags ?: 0) and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            TrojanAdapter.UiItem(
                (if (isSystem) "Vpn.System · " else "Vpn.ThirdParty · ") + (label ?: s.serviceInfo.packageName),
                s.serviceInfo.packageName + " · " + s.serviceInfo.name,
                "该应用可建立 VPN 截获全部网络流量",
                if (isSystem) ThreatLevel.LOW else ThreatLevel.MEDIUM,
                if (!isSystem) "确认是否本人安装的 VPN/代理;非本人安装的 VPN 是流量劫持最强形态" else null,
                null, null, null, null
            )
        }
    }
}
