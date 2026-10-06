package com.armorlab.securedroid.ui

import com.armorlab.securedroid.R
import com.armorlab.securedroid.feature.DnsGuard
import com.armorlab.securedroid.feature.HostsGuard
import com.armorlab.securedroid.feature.NetAudit
import com.armorlab.securedroid.scan.ThreatLevel

/** 网络安全检测:DNS 劫持对比 + hosts 篡改 + 实时连接审计 */
class NetworkAuditActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_network

    override fun subtitleRes(): Int? = R.string.tool_network_sub

    override fun load(): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()

        // DNS 劫持检测
        for (r in DnsGuard.probe()) {
            items.add(
                TrojanAdapter.UiItem(
                    title = if (r.hijacked) "DNS.HijackSuspect · " + r.host
                            else if (r.verified) "DNS.Clean · " + r.host
                            else "DNS.Unverified · " + r.host,
                    sub = "系统解析: " + r.systemIps.joinToString(", ").ifEmpty { "解析失败" } +
                        " | DoH: " + r.dohIps.joinToString(", ").ifEmpty { "加密解析失败" },
                    detail = "",
                    level = when {
                        r.hijacked -> ThreatLevel.HIGH
                        r.verified -> ThreatLevel.LOW
                        else -> ThreatLevel.MEDIUM
                    },
                    suggestion = if (r.hijacked) "系统 DNS 与加密 DNS 结果不一致,怀疑被劫持;建议更换可信 DNS(如 223.5.5.5)" else null,
                    uninstallPkg = null
                )
            )
        }

        // hosts 篡改检测
        val hosts = HostsGuard.analyze(HostsGuard.readHosts())
        for (h in hosts) {
            items.add(
                TrojanAdapter.UiItem(
                    title = if (h.sensitiveHit) "Hosts.SensitiveRedirect" else "Hosts.CustomEntry",
                    sub = h.line,
                    detail = "",
                    level = if (h.sensitiveHit) ThreatLevel.HIGH else ThreatLevel.MEDIUM,
                    suggestion = if (h.sensitiveHit) "hosts 中出现支付/银行相关域名映射,高度可疑" else "非标准 hosts 条目,确认是否自己添加",
                    uninstallPkg = null
                )
            )
        }
        if (hosts.isEmpty()) {
            items.add(TrojanAdapter.UiItem("Hosts.Clean", "/etc/hosts 无自定义条目", "", ThreatLevel.LOW, null, null))
        }

        // 实时连接审计
        for (c in NetAudit.established(this)) {
            items.add(
                TrojanAdapter.UiItem(
                    title = "Net.Conn · " + c.appLabel,
                    sub = c.pkg + " → " + c.remote,
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
