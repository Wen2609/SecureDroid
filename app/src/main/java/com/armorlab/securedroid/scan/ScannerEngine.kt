package com.armorlab.securedroid.scan

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.armorlab.securedroid.permissions.PermissionAuditor
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 静态扫描引擎:
 * 1. 计算 APK 的 SHA-256 并与特征库比对(精确匹配);
 * 2. 结合敏感权限权重给出风险评分(启发式)。
 */
object ScannerEngine {

    data class ScanResult(
        val packageName: String,
        val appName: String,
        val sha256: String,
        val threat: ThreatInfo?,
        val permissionRiskScore: Int,
        val riskyPermissions: List<String>
    ) {
        val isMalicious: Boolean get() = threat != null
        val isRisky: Boolean get() = !isMalicious && permissionRiskScore >= 40
    }

    fun scanAll(context: Context): List<ScanResult> {
        SignatureDatabase.loadLocalUpdate(context)
        val pm = context.packageManager
        return pm.getInstalledPackages(0).map { scanPackage(context, it.packageName) }
    }

    fun scanPackage(context: Context, pkg: String): ScanResult {
        val pm = context.packageManager
        val info: PackageInfo = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            return ScanResult(pkg, pkg, "", null, 0, emptyList())
        }
        val appInfo = info.applicationInfo
        val appName = appInfo?.loadLabel(pm)?.toString() ?: pkg
        val apkPath = appInfo?.sourceDir
        val sha = if (apkPath != null) hashFile(apkPath) else ""
        val threat = SignatureDatabase.lookup(sha)
        val audit = PermissionAuditor.scoreFor(info)
        return ScanResult(pkg, appName, sha, threat, audit.first, audit.second)
    }

    fun hashFile(path: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(File(path)).use { input ->
            val buf = ByteArray(65536)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return toHex(md.digest())
    }

    fun toHex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(digits[v ushr 4])
            sb.append(digits[v and 0x0F])
        }
        return sb.toString()
    }
}
