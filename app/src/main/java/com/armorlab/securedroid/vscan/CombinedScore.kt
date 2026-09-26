package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.pm.PackageManager
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.TrojanScanner

/**
 * 综合威胁评分引擎:权限权重 + 多引擎行为检测 + APK 结构元数据
 * 三路证据加权合成单一威胁分(0-100)。
 */
object CombinedScore {

    data class Result(
        val score: Int,
        val level: ThreatLevel,
        val parts: List<String>,
        val highEngineHits: Int = 0
    )

    fun evaluate(context: Context, pkg: String): Result {
        var score = 0
        var highEngineHits = 0
        val parts = mutableListOf<String>()
        val pm = context.packageManager
        val info = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) { return Result(0, ThreatLevel.LOW, emptyList()) }

        // 权限证据(权重上限 30)
        val perm = PermissionAuditor.scoreFor(info)
        if (perm.first > 0) {
            val add = (perm.first / 2).coerceAtMost(30)
            score += add
            parts.add("权限 " + perm.first + " 分(计 " + add + ")")
        }

        // 行为引擎证据(上限 50)
        val trojan = TrojanScanner.scanPackage(context, pkg)
        for (d in trojan.detections) {
            val add = when (d.level) {
                ThreatLevel.CRITICAL -> 30
                ThreatLevel.HIGH -> 20
                ThreatLevel.MEDIUM -> 10
                ThreatLevel.LOW -> 3
            }
            score += add
            if (d.level == ThreatLevel.CRITICAL || d.level == ThreatLevel.HIGH) highEngineHits++
            parts.add(d.engine + ":" + d.name + "(+" + add + ")")
        }

        // 元数据证据(上限 40)
        for (f in ApkInsights.analyze(context, pkg)) {
            val add = when (f.level) {
                ThreatLevel.CRITICAL -> 30
                ThreatLevel.HIGH -> 20
                ThreatLevel.MEDIUM -> 8
                ThreatLevel.LOW -> 0
            }
            if (add > 0) {
                score += add
                if (f.level == ThreatLevel.CRITICAL || f.level == ThreatLevel.HIGH) highEngineHits++
                parts.add(f.name + "(+" + add + ")")
            }
        }

        if (score > 100) score = 100
        val level = when {
            score >= 60 -> ThreatLevel.CRITICAL
            score >= 35 -> ThreatLevel.HIGH
            score >= 15 -> ThreatLevel.MEDIUM
            else -> ThreatLevel.LOW
        }
        return Result(score, level, parts, highEngineHits)
    }
}
