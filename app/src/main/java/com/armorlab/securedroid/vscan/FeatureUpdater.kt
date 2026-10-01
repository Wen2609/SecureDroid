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

    /**
     * 特征库在线更新。
     *
     * 安全要求(参考 OWASP MASVS-NETWORK:安全通道 + 完整性校验):
     * - 只接受 https 且**重定向后仍为 https**,拒绝明文传输(否则特征库可被中间人投毒);
     * - SHA-256 为**必填**,缺失或不匹配一律拒绝写入 —— 特征库投毒等于让查杀引擎失效;
     * - 内容需至少包含一条可解析特征行,避免写入垃圾/占位内容。
     */
    /** 粗略校验:至少一行非注释、非空且不像 HTML 错误页的内容 */
    private fun looksLikeSignatureDatabase(bytes: ByteArray): Boolean {
        val text = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return false
        }
        if (text.contains("<html", ignoreCase = true) || text.contains("<!DOCTYPE", ignoreCase = true)) {
            return false
        }
        return text.lineSequence().any { line ->
            val t = line.trim()
            t.isNotEmpty() && !t.startsWith("#") && t.length >= 16
        }
    }

    fun update(context: Context, urlStr: String, expectedSha256: String?): UpdateResult {
        val name = urlStr.substringAfterLast('/').substringBefore('?').lowercase()
        if (!(name.endsWith(".hsb") || name.endsWith(".ndb"))) {
            return UpdateResult(false, "URL 必须指向 .hsb 或 .ndb 文件")
        }
        if (!urlStr.trim().startsWith("https://", ignoreCase = true)) {
            return UpdateResult(false, "只允许 https:// 地址(明文传输可被投毒)")
        }
        if (expectedSha256.isNullOrBlank()) {
            return UpdateResult(false, "必须提供 SHA-256 校验值(缺失时无法确认特征库完整性)")
        }
        val expected = expectedSha256.trim()
        if (expected.length != 64 || expected.any { Character.digit(it, 16) < 0 }) {
            return UpdateResult(false, "SHA-256 格式不正确(应为 64 位十六进制)")
        }

        val bytes = try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            val data = conn.inputStream.use { it.readBytes() }
            val finalProtocol = conn.url.protocol
            conn.disconnect()
            if (!finalProtocol.equals("https", ignoreCase = true)) {
                return UpdateResult(false, "重定向后为非安全协议(" + finalProtocol + "),已中止")
            }
            if (data.size > 20L * 1024 * 1024) return UpdateResult(false, "文件超过 20MB 上限")
            if (data.isEmpty()) return UpdateResult(false, "下载内容为空")
            data
        } catch (e: Exception) {
            return UpdateResult(false, "下载失败: " + e.message)
        }

        val sha = ScannerEngine.toHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        )
        if (!sha.equals(expected, ignoreCase = true)) {
            return UpdateResult(false, "SHA-256 校验失败(实际 " + sha.take(16) + "…),已拒绝写入")
        }
        if (!looksLikeSignatureDatabase(bytes)) {
            return UpdateResult(false, "内容不是有效的特征库(无可用特征行),已拒绝写入")
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
