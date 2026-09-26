package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/**
 * 断网应急(root):用 iptables owner 模块按 UID 切断应用全部联网,
 * 用于处置正在外传数据的木马(比卸载更快、可逆)。需内核支持 owner match。
 */
object NetKill {

    private fun uidOf(context: Context, pkg: String): Int? = try {
        context.packageManager.getApplicationInfo(pkg, 0)?.uid
    } catch (_: Exception) { null }

    private fun blockedUids(): Set<Int>? {
        val out = ShellBridge.runSu("iptables -S OUTPUT 2>/dev/null") ?: return null
        return Regex("--uid-owner (\\d+)").findAll(out)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()
    }

    fun isBlocked(context: Context, pkg: String): Boolean {
        val uid = uidOf(context, pkg) ?: return false
        return blockedUids()?.contains(uid) == true
    }

    fun block(context: Context, pkg: String): Boolean {
        val uid = uidOf(context, pkg) ?: return false
        return ShellBridge.runSu("iptables -I OUTPUT 1 -m owner --uid-owner " + uid + " -j DROP") != null
    }

    fun unblock(context: Context, pkg: String): Boolean {
        val uid = uidOf(context, pkg) ?: return false
        return ShellBridge.runSu("iptables -D OUTPUT -m owner --uid-owner " + uid + " -j DROP") != null
    }

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        val pm = context.packageManager
        val items = mutableListOf<TrojanAdapter.UiItem>()
        var blockedCount = 0
        // 单次拉取 iptables 规则表,内存匹配(避免每应用一次 su)
        val blockedUids = blockedUids() ?: emptySet()
        for (info in pm.getInstalledApplications(0).take(100)) {
            if (info.packageName == context.packageName) continue
            val blocked = info.uid in blockedUids
            val label = info.loadLabel(pm).toString()
            if (blocked) blockedCount++
            items.add(
                TrojanAdapter.UiItem(
                    (if (blocked) "NetKill.Blocked · " else "NetKill.Online · ") + label,
                    info.packageName,
                    if (blocked) "该应用已被 iptables 切断联网" else "正常联网",
                    if (blocked) ThreatLevel.HIGH else ThreatLevel.LOW,
                    null, null,
                    if (blocked) "iptables -D OUTPUT -m owner --uid-owner " + info.uid + " -j DROP"
                    else "iptables -I OUTPUT 1 -m owner --uid-owner " + info.uid + " -j DROP",
                    if (blocked) "恢复联网" else "切断联网"
                )
            )
        }
        items.add(
            0,
            TrojanAdapter.UiItem(
                "NetKill.Summary", "应急断网中 " + blockedCount + " 个应用",
                "处置外传数据的木马时,先断网再卸载;需内核支持 owner match",
                ThreatLevel.LOW, null, null
            )
        )
        return items
    }
}
