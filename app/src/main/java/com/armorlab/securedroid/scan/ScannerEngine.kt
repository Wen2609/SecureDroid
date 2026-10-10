package com.armorlab.securedroid.scan

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.armorlab.securedroid.core.PackageSnapshot
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.vscan.HashCache
import com.armorlab.securedroid.vscan.TrustStore
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 静态扫描引擎:计算 APK 的 SHA-256 并与特征库比对(精确匹配)。
 *
 * 性能:
 * - 包信息 / 应用标签走 PackageSnapshot 进程内缓存(与首页、并行查杀共享);
 * - APK 哈希走 HashCache(未更新即复用)。
 */
object ScannerEngine {

    data class ScanResult(
        val packageName: String,
        val appName: String,
        val sha256: String,
        val threat: ThreatInfo?,
        val permissionRiskScore: Int
    ) {
        val isMalicious: Boolean get() = threat != null
    }

    /** 按包名扫描:优先命中快照,避免重复绑定器调用 */
    fun scanPackage(context: android.content.Context, pkg: String): ScanResult {
        val info = PackageSnapshot.packageInfo(context, pkg, PackageManager.GET_PERMISSIONS)
            ?: return ScanResult(pkg, pkg, "", null, 0)
        return scanPackage(context, info)
    }

    /** 已有 PackageInfo 时直接评分(避免再次 IPC) */
    fun scanPackage(context: android.content.Context, info: PackageInfo): ScanResult {
        val pkg = info.packageName
        val appInfo = info.applicationInfo
        val appName = appInfo?.let { PackageSnapshot.label(context, it) } ?: pkg
        // 信任列表:跳过所有检测链路
        if (TrustStore.isTrusted(context, pkg)) {
            return ScanResult(pkg, appName, "", null, 0)
        }
        val apkPath = appInfo?.sourceDir
        // 哈希缓存:APK 未更新则复用上次 SHA-256
        val sha = if (apkPath != null)
            HashCache.cachedSha256(context, apkPath, info.lastUpdateTime, File(apkPath).length())
        else ""
        val threat = SignatureDatabase.lookup(sha)
        val riskScore = PermissionAuditor.scoreFor(info).first
        return ScanResult(pkg, appName, sha, threat, riskScore)
    }

    /** 一次读盘同时计算 SHA-256 与 MD5(ClamAV .hsb/.hdb 双格式匹配,免两次 IO) */
    fun hashFileDigests(path: String): Pair<String, String> {
        val sha = MessageDigest.getInstance("SHA-256")
        val md5 = MessageDigest.getInstance("MD5")
        FileInputStream(File(path)).use { input ->
            val buf = ByteArray(65536)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                sha.update(buf, 0, n)
                md5.update(buf, 0, n)
            }
        }
        return Pair(toHex(sha.digest()), toHex(md5.digest()))
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
