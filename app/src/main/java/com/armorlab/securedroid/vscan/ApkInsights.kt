package com.armorlab.securedroid.vscan

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.armorlab.securedroid.scan.ThreatLevel
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * APK 结构与元数据检测引擎:
 * 1. 证书:未签名 / 调试标记 / 签名指纹展示;
 * 2. targetSdk 低于 23(可规避运行时权限模型);
 * 3. 危险权限组合(请求安装 + 短信读写);
 * 4. 嵌入式压缩包:PK 头数量远超 zip 条目数 → 藏匿载荷;
 * 5. 隐藏 DEX:原始 dex magic 出现次数超过 dex 条目数 → 加密/拼接隐藏代码;
 * 6. assets 中的原生库(绕过 lib/ 标准布局)。
 */
object ApkInsights {

    private val PK = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val DEX = byteArrayOf(0x64, 0x65, 0x78, 0x0A)

    data class Finding(val name: String, val level: ThreatLevel, val detail: String)

    fun analyze(context: Context, pkg: String): List<Finding> {
        val findings = mutableListOf<Finding>()
        val pm = context.packageManager
        val info = try {
            pm.getPackageInfo(pkg,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES)
        } catch (_: Exception) { return findings }
        val app = info.applicationInfo ?: return findings

        // 1) 证书
        val sigs = info.signatures
        if (sigs == null || sigs.isEmpty()) {
            findings.add(Finding("Virus.Unsigned", ThreatLevel.HIGH, "APK 未携带签名信息,来源异常"))
        } else {
            val fp = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray())
                .joinToString("") { String.format("%02x", it) }
            findings.add(Finding("Virus.CertInfo", ThreatLevel.LOW,
                "签名指纹: " + fp.take(24) + "…"))
        }
        if ((app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            findings.add(Finding("Virus.Debuggable", ThreatLevel.MEDIUM,
                "应用标记为可调试(debuggable),可被 attach 注入"))
        }

        // 2) targetSdk
        val target = app.targetSdkVersion
        if (target in 1..22) {
            findings.add(Finding("Virus.LegacySdk", ThreatLevel.MEDIUM,
                "targetSdk=" + target + " 低于 23,可规避运行时权限模型"))
        }

        // 3) 危险权限组合
        val perms = info.requestedPermissions?.toSet() ?: emptySet()
        val install = perms.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        val sms = perms.contains(android.Manifest.permission.READ_SMS) ||
            perms.contains(android.Manifest.permission.SEND_SMS)
        if (install && sms) {
            findings.add(Finding("Virus.InstallSmsCombo", ThreatLevel.HIGH,
                "同时请求\"安装应用\"与\"短信\"权限,典型扣费/木马组合"))
        }

        // 4/5/6) 结构检测(读 APK 字节,上限 64MB)
        val apkPath = app.sourceDir ?: return findings
        val bytes = try {
            val f = java.io.File(apkPath)
            if (f.length() <= 64L * 1024 * 1024) f.readBytes() else return findings
        } catch (_: Exception) { return findings }

        var entryCount = 0
        var dexEntries = 0
        var assetsSo = mutableListOf<String>()
        try {
            java.util.zip.ZipFile(apkPath).use { zip ->
                val e = zip.entries()
                while (e.hasMoreElements()) {
                    val n = e.nextElement().name
                    entryCount++
                    if (n.endsWith(".dex")) dexEntries++
                    if (n.startsWith("assets/") && n.endsWith(".so") && assetsSo.size < 5) {
                        assetsSo.add(n)
                    }
                }
            }
        } catch (_: Exception) { }

        val pkCount = countOf(bytes, PK)
        if (entryCount > 0 && pkCount > entryCount + 5) {
            findings.add(Finding("Virus.EmbeddedZip", ThreatLevel.HIGH,
                "PK 头 " + pkCount + " 远多于 zip 条目 " + entryCount + ",疑有嵌入式压缩载荷"))
        }
        val dexCount = countOf(bytes, DEX)
        if (dexEntries > 0 && dexCount > dexEntries) {
            findings.add(Finding("Virus.HiddenDex", ThreatLevel.HIGH,
                "原始 dex magic 出现 " + dexCount + " 次但仅 " + dexEntries +
                    " 个 dex 条目,疑有隐藏/拼接 DEX"))
        }
        if (assetsSo.isNotEmpty()) {
            findings.add(Finding("Virus.AssetsSo", ThreatLevel.MEDIUM,
                "assets 中携带原生库(绕过 lib/ 标准布局): " + assetsSo.joinToString(", ")))
        }
        return findings
    }

    private fun countOf(data: ByteArray, pat: ByteArray): Int {
        var count = 0
        var i = 0
        outer@ while (i <= data.size - pat.size) {
            for (j in pat.indices) {
                if (data[i + j] != pat[j]) { i++; continue@outer }
            }
            count++
            i += pat.size
        }
        return count
    }
}
