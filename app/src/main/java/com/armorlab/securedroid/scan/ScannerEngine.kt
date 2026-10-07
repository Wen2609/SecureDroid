package com.armorlab.securedroid.scan

import android.content.Context
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
 * 静态扫描引擎:
 * 1. 计算 APK 的 SHA-256 并与特征库比对(精确匹配);
 * 2. 结合敏感权限权重给出风险评分(启发式)。
 *
 * 性能(第三轮优化):
 * - 全盘扫描只做一次 getInstalledPackages(GET_PERMISSIONS),不再"列表一次 + 每包一次
 *   getPackageInfo"的 N+1 绑定器调用;
 * - 包信息 / 应用标签走 PackageSnapshot 进程内缓存(与首页、并行查杀共享)。
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
        return PackageSnapshot.installedPackages(context, PackageManager.GET_PERMISSIONS)
            .map { scanPackage(context, it) }
    }

    /** 按包名扫描:优先命中快照,避免重复绑定器调用 */
    fun scanPackage(context: Context, pkg: String): ScanResult {
        val info = PackageSnapshot.packageInfo(context, pkg, PackageManager.GET_PERMISSIONS)
            ?: return ScanResult(pkg, pkg, "", null, 0, emptyList())
        return scanPackage(context, info)
    }

    /** 已有 PackageInfo 时直接评分(避免再次 IPC) */
    fun scanPackage(context: Context, info: PackageInfo): ScanResult {
        val pkg = info.packageName
        val appInfo = info.applicationInfo
        val appName = appInfo?.let { PackageSnapshot.label(context, it) } ?: pkg
        // 信任列表:跳过所有检测链路
        if (TrustStore.isTrusted(context, pkg)) {
            return ScanResult(pkg, appName, "", null, 0, emptyList())
        }
        val apkPath = appInfo?.sourceDir
        // 哈希缓存:APK 未更新则复用上次 SHA-256
        val sha = if (apkPath != null)
            HashCache.cachedSha256(context, apkPath, info.lastUpdateTime, File(apkPath).length())
        else ""
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
