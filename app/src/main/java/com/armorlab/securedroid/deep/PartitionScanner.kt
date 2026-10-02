package com.armorlab.securedroid.deep

import com.armorlab.securedroid.core.Re
import android.content.Context
import android.util.Base64
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/**
 * 底层分区查杀:
 * 1. 枚举命名分区(/dev/block/by-name)+ /proc/partitions 容量;
 * 2. 逐分区 dd 读取前 8MB(base64 通道),做:
 *    - ClamAV 字节码特征匹配;
 *    - 历史 root 方案痕迹(daemonsu / supersu);
 *    - boot/init 镜像中的 magisk 痕迹与 /data/local/tmp 引用(引导级持久化)。
 * 只读不改,不对分区内容做任何修改。
 */
object PartitionScanner {

    private val skip = setOf(
        "userdata", "userdata_a", "userdata_b",
        "fsc", "fsg", "modemst1", "modemst2"
    )

    private data class Partition(val name: String, val path: String, val sizeKb: Long)

    fun scanAll(context: Context): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()
        if (!RootGuard.isRootMode(context)) {
            items.add(
                TrojanAdapter.UiItem(
                    "Part.NeedRoot", "底层分区查杀需要 Root 模式",
                    "将枚举 /dev/block/by-name 并读取分区头部做特征扫描", ThreatLevel.MEDIUM, null, null
                )
            )
            return items
        }

        val names = ShellBridge.runSu("ls -1 /dev/block/by-name 2>/dev/null", 20_000L)
        if (names == null || names.isBlank()) {
            items.add(
                TrojanAdapter.UiItem(
                    "Part.NoByname", "未找到 /dev/block/by-name 分区表", "", ThreatLevel.MEDIUM, null, null
                )
            )
            return items
        }

        val sizes = HashMap<String, Long>()
        ShellBridge.runSu("cat /proc/partitions", 20_000L)?.let { pt ->
            for (line in pt.lines()) {
                val f = line.trim().split(Re.WS)
                if (f.size >= 4) sizes[f[3]] = f[2].toLongOrNull() ?: 0L
            }
        }

        val parts = names.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Partition(it, "/dev/block/by-name/" + it, sizes[it] ?: 0L) }

        items.add(
            TrojanAdapter.UiItem(
                "Part.Inventory", "发现 " + parts.size + " 个命名分区",
                parts.joinToString(", ") { it.name }.take(400),
                ThreatLevel.LOW, null, null
            )
        )

        var scanned = 0
        for (p in parts) {
            if (p.name in skip) continue
            val data = readRaw(p.path, 8L * 1024 * 1024) ?: continue
            scanned++

            for (sig in ClamAvSignatures.scanBytes(data)) {
                items.add(
                    TrojanAdapter.UiItem(
                        "Part.ClamAvHit · " + sig, p.name + " (" + p.path + ")",
                        "分区原始数据命中 ClamAV 字节码特征",
                        ThreatLevel.CRITICAL, "底层分区命中病毒特征,建议备份后重刷该分区镜像",
                        null, null, null, null
                    )
                )
            }

            val raw = String(data, Charsets.ISO_8859_1).lowercase()
            if (raw.contains("daemonsu") || raw.contains("supersu")) {
                items.add(
                    TrojanAdapter.UiItem(
                        "Part.SuTrace", p.name + " (" + p.path + ")",
                        "分区原始数据包含历史 root 方案痕迹(daemonsu/supersu)",
                        ThreatLevel.HIGH, "排查历史 root 方案是否卸载干净", null, null
                    )
                )
            }
            if ((p.name.startsWith("boot") || p.name.startsWith("init")) && raw.contains("magisk")) {
                items.add(
                    TrojanAdapter.UiItem(
                        "Part.MagiskTrace", p.name + " (" + p.path + ")",
                        "引导/初始化镜像包含 magisk 痕迹(已 root 设备属正常现象)",
                        ThreatLevel.LOW, "如非本人 root,请立即重刷官方镜像", null, null
                    )
                )
            }
            if (p.name.startsWith("boot") && raw.contains("/data/local/tmp")) {
                items.add(
                    TrojanAdapter.UiItem(
                        "Part.TmpInit", p.name + " (" + p.path + ")",
                        "引导镜像引用 /data/local/tmp,可能存在引导级持久化",
                        ThreatLevel.HIGH, "检查 boot 镜像 cmdline/init 脚本是否被篡改", null, null
                    )
                )
            }
        }

        if (scanned == 0) {
            items.add(
                TrojanAdapter.UiItem(
                    "Part.None", "未能读取任何分区数据", "请确认已授予 su 权限", ThreatLevel.MEDIUM, null, null
                )
            )
        }
        return items
    }

    private fun readRaw(path: String, cap: Long): ByteArray? {
        val b64 = ShellBridge.runSu(
            "dd if=" + ShellBridge.quote(path) + " bs=4096 count=" + (cap / 4096) +
                " 2>/dev/null | base64", 90_000L
        ) ?: return null
        return try { Base64.decode(b64.trim(), Base64.DEFAULT) } catch (_: Exception) { null }
    }
}
