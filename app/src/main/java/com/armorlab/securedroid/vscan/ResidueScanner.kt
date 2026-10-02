package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.core.PackageSnapshot
import android.content.Context
import com.armorlab.securedroid.R
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/** 卸载残留检查:已卸载应用遗留的 /data/data 目录(root) */
object ResidueScanner {

    fun scan(context: Context): List<TrojanAdapter.UiItem> {
        val entries = ShellBridge.runSu("ls -1 /data/data 2>/dev/null", 20_000L)
            ?: return listOf(
                TrojanAdapter.UiItem(
                    "Residue.NeedRoot", "需要 root 才能读取 /data/data", "",
                    ThreatLevel.MEDIUM, null, null
                )
            )
        val installed = PackageSnapshot.installedPackages(context, 0)
            .map { it.packageName }.toSet()
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for (pkg in entries.lines()) {
            val t = pkg.trim()
            if (t.isEmpty() || t in installed || t == "lost+found") continue
            if (!t.contains('.')) continue
            items.add(
                TrojanAdapter.UiItem(
                    "Virus.Residue · " + t,
                    "/data/data/" + t,
                    "已卸载应用残留数据目录,可能包含历史敏感数据或恶意载荷",
                    ThreatLevel.MEDIUM,
                    "确认应用确实已卸载后可清理残留",
                    null, null,
                    "rm -rf " + ShellBridge.quote("/data/data/" + t),
                    "清理残留"
                )
            )
        }
        if (items.isEmpty()) {
            items.add(
                TrojanAdapter.UiItem(
                    "Residue.Clean", "未发现卸载残留目录", "", ThreatLevel.LOW, null, null
                )
            )
        }
        return items
    }
}
