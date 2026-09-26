package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter

/** 木马家族分类:按检测项关键词组合归入已知家族谱系 */
object FamilyClassifier {

    fun classify(detectionNames: List<String>): String {
        val j = detectionNames.joinToString(" ")
        return when {
            j.contains("Sms") || j.contains("Premium") -> "扣费/银行木马家族(Banker/SmsTrojan)"
            j.contains("Ransom") -> "勒索家族(Ransom)"
            j.contains("Lock") -> "锁机家族(Locker)"
            j.contains("Spy") || j.contains("ClipSpy") -> "间谍家族(Spyware)"
            j.contains("Backdoor") || j.contains("ReverseShell") -> "后门家族(Backdoor)"
            j.contains("Miner") || j.contains("HighCpu") -> "挖矿家族(Miner)"
            j.contains("PrivEsc") || j.contains("SuBinary") -> "提权家族(PrivEsc)"
            else -> "通用可疑(未定型)"
        }
    }

    /** 对全量应用并行扫描并按家族归类 */
    fun items(
        context: Context,
        results: List<TrojanScanner.ScanResult>
    ): List<TrojanAdapter.UiItem> {
        val infected = results.filter { it.isInfected }
        if (infected.isEmpty()) {
            return listOf(
                TrojanAdapter.UiItem("Family.Clean", "未发现可归类家族的感染应用", "", ThreatLevel.LOW, null, null)
            )
        }
        val byFamily = LinkedHashMap<String, MutableList<TrojanScanner.ScanResult>>()
        for (r in infected) {
            val family = classify(r.detections.map { it.name })
            byFamily.getOrPut(family) { mutableListOf() }.add(r)
        }
        val items = mutableListOf<TrojanAdapter.UiItem>()
        for ((family, apps) in byFamily) {
            items.add(
                TrojanAdapter.UiItem(
                    "Family.Group · " + family,
                    apps.size.toString() + " 个应用",
                    apps.joinToString(", ") { it.appName }.take(300),
                    if (family.contains("勒索") || family.contains("后门")) ThreatLevel.CRITICAL
                    else ThreatLevel.HIGH,
                    null, null
                )
            )
        }
        return items
    }
}
