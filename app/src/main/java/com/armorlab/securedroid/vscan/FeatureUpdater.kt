package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.root.ShellBridge
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.trojan.ClamAvSignatures
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 特征库在线更新:从用户配置的 URL 拉取 .hsb/.ndb 文件,
 * 可选 SHA-256 校验,写入 files/clamav 后热重载生效。
 */
object FeatureUpdater {

    data class UpdateResult(val ok: Boolean, val message: String)

    fun update(context: Context, urlStr: String, expectedSha256: String?): UpdateResult {
        val name = urlStr.substringAfterLast('/').substringBefore('?').lowercase()
        if (!(name.endsWith(".hsb") || name.endsWith(".ndb"))) {
            return UpdateResult(false, "URL 必须指向 .hsb 或 .ndb 文件")
        }
        val bytes = try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            val data = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (data.size > 20L * 1024 * 1024) return UpdateResult(false, "文件超过 20MB 上限")
            data
        } catch (e: Exception) {
            return UpdateResult(false, "下载失败: " + e.message)
        }

        val sha = ScannerEngine.toHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        )
        if (!expectedSha256.isNullOrBlank() &&
            !sha.equals(expectedSha256.trim(), ignoreCase = true)) {
            return UpdateResult(false, "SHA-256 校验失败(实际 " + sha.take(16) + "…),已拒绝写入")
        }

        try {
            val out = File(context.filesDir, "clamav/" + name)
            out.parentFile?.mkdirs()
            out.writeBytes(bytes)
            ClamAvSignatures.reload(context)
            return UpdateResult(
                true,
                "更新成功:" + name + " (" + bytes.size / 1024 + "KB),当前 ClamAV 签名 " +
                    ClamAvSignatures.hashCount() + " 哈希 / " + ClamAvSignatures.byteCount() + " 字节"
            )
        } catch (e: Exception) {
            return UpdateResult(false, "写入失败: " + e.message)
        }
    }
}
