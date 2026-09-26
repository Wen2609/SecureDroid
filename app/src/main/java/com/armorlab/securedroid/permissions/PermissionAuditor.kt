package com.armorlab.securedroid.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.armorlab.securedroid.scan.ThreatLevel

/**
 * 权限风险审计:按敏感权限权重累加评分,权重越高越危险。
 */
object PermissionAuditor {

    private val riskMap: Map<String, Int> = mapOf(
        Manifest.permission.READ_SMS to 30,
        Manifest.permission.SEND_SMS to 35,
        Manifest.permission.RECEIVE_SMS to 25,
        Manifest.permission.RECEIVE_MMS to 25,
        Manifest.permission.READ_CONTACTS to 20,
        Manifest.permission.WRITE_CONTACTS to 20,
        Manifest.permission.READ_CALL_LOG to 30,
        Manifest.permission.WRITE_CALL_LOG to 25,
        Manifest.permission.CALL_PHONE to 25,
        Manifest.permission.RECORD_AUDIO to 25,
        Manifest.permission.CAMERA to 20,
        Manifest.permission.ACCESS_FINE_LOCATION to 20,
        Manifest.permission.ACCESS_COARSE_LOCATION to 10,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to 30,
        Manifest.permission.READ_EXTERNAL_STORAGE to 10,
        Manifest.permission.WRITE_EXTERNAL_STORAGE to 10,
        Manifest.permission.READ_MEDIA_IMAGES to 10,
        Manifest.permission.READ_MEDIA_VIDEO to 10,
        Manifest.permission.READ_PHONE_STATE to 15,
        Manifest.permission.READ_PHONE_NUMBERS to 10,
        Manifest.permission.BODY_SENSORS to 15,
        Manifest.permission.SYSTEM_ALERT_WINDOW to 20,
        Manifest.permission.GET_ACCOUNTS to 10,
        "android.permission.REQUEST_INSTALL_PACKAGES" to 40,
        "android.permission.QUERY_ALL_PACKAGES" to 15,
        "android.permission.PACKAGE_USAGE_STATS" to 20
    )

    data class AuditResult(
        val appName: String,
        val packageName: String,
        val score: Int,
        val risky: List<String>
    ) {
        val level: ThreatLevel
            get() = when {
                score >= 60 -> ThreatLevel.CRITICAL
                score >= 40 -> ThreatLevel.HIGH
                score >= 20 -> ThreatLevel.MEDIUM
                else -> ThreatLevel.LOW
            }
    }

    fun scoreFor(info: PackageInfo): Pair<Int, List<String>> {
        var score = 0
        val risky = mutableListOf<String>()
        info.requestedPermissions?.forEach { perm ->
            val weight = riskMap[perm] ?: 0
            if (weight > 0) {
                score += weight
                risky.add(perm.substringAfterLast('.'))
            }
        }
        return Pair(score, risky)
    }

    fun audit(context: Context): List<AuditResult> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val (score, risky) = scoreFor(info)
            if (score <= 0) return@mapNotNull null
            AuditResult(
                appInfo.loadLabel(pm).toString(),
                info.packageName,
                score,
                risky
            )
        }.sortedByDescending { it.score }
    }
}
