package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.ScannerEngine
import com.armorlab.securedroid.trojan.ClamAvSignatures
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * 特征库在线更新:从用户配置的 URL 拉取 .hsb/.ndb/.hdb/.ldb 文件,
 * 可选 SHA-256 校验,写入 files/clamav 后热重载生效。
 *
 * 可靠性(本版新增):
 * - **断点续传**:下载进度落盘到 files/clamav/.part/<名>,重试时发 Range 头续传,
 *   服务器不支持(响应 200)则从头重来;完整下载并校验通过后才落正式文件;
 * - **更新历史**:每次成功/失败记入偏好(update_history,上限 20 条),供「特征库回滚」与排障;
 * - **回滚**:写入前把同名旧文件备份到 files/clamav/backup/,「特征库回滚」工具一键还原。
 */
object FeatureUpdater {

    data class UpdateResult(val ok: Boolean, val message: String)

    private const val MAX_BYTES = 20L * 1024 * 1024
    private const val HISTORY_KEY = "update_history"
    private const val HISTORY_MAX = 20

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

    private fun clamavDir(context: Context) = File(context.filesDir, "clamav")
    private fun partDir(context: Context) = File(clamavDir(context), ".part")
    private fun backupDir(context: Context) = File(clamavDir(context), "backup")

    fun update(context: Context, urlStr: String, expectedSha256: String?): UpdateResult {
        val name = urlStr.substringAfterLast('/').substringBefore('?').lowercase()
        if (!name.isSignatureFileName()) {
            return UpdateResult(false, "URL 必须指向 .hsb/.ndb/.hdb/.ldb 文件")
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

        val (data, resumed) = try {
            downloadWithResume(context, urlStr)
        } catch (e: Exception) {
            return UpdateResult(false, "下载失败: " + e.message)
        }
        if (data.isEmpty()) return UpdateResult(false, "下载内容为空")
        if (data.size > MAX_BYTES) return UpdateResult(false, "文件超过 20MB 上限")

        val sha = ScannerEngine.toHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(data)
        )
        if (!sha.equals(expected, ignoreCase = true)) {
            clearPart(context, name)
            return UpdateResult(false, "SHA-256 校验失败(实际 " + sha.take(16) + "…),已拒绝写入")
        }
        if (!looksLikeSignatureDatabase(data)) {
            clearPart(context, name)
            return UpdateResult(false, "内容不是有效的特征库(无可用特征行),已拒绝写入")
        }

        return applyUpdate(context, name, data, resumed)
    }

    private fun String.isSignatureFileName(): Boolean {
        val n = this
        return n.endsWith(".hsb") || n.endsWith(".ndb") || n.endsWith(".hdb") || n.endsWith(".ldb") ||
            n.endsWith(".hsu") || n.endsWith(".ndu") || n.endsWith(".hdu") || n.endsWith(".ldu")
    }

    /**
     * 断点续传下载:分块读取(64KB),进度落盘 .part/<名>;
     * 重试时若 .part 存在则发 Range 头从断点续传 —— 206 续接,200 则视为服务器
     * 不支持(重下);返回 (完整字节, 是否发生续传)。
     */
    private fun downloadWithResume(context: Context, urlStr: String): Pair<ByteArray, Boolean> {
        val name = urlStr.substringAfterLast('/').substringBefore('?')
        val part = File(partDir(context), name)
        var start = 0L
        var resumed = false
        if (part.isFile && part.length() in 1 until MAX_BYTES) {
            start = part.length()
            resumed = true
        }
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (start > 0) conn.setRequestProperty("Range", "bytes=$start-")
            val code = conn.responseCode
            if (start > 0 && code != HttpURLConnection.HTTP_PARTIAL) {
                // 服务器不支持 Range:重下
                start = 0
                resumed = false
                part.delete()
            }
            if (conn.url.protocol != "https") {
                throw SecurityException("重定向后为非安全协议(" + conn.url.protocol + ")")
            }
            val input = conn.inputStream
            RandomAccessFile(part, "rw").use { out ->
                out.seek(start)
                val buf = ByteArray(64 * 1024)
                var written = start
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                    if (written > MAX_BYTES) throw SecurityException("文件超过 20MB 上限")
                }
            }
        } finally {
            conn.disconnect()
        }
        val bytes = part.readBytes()
        return Pair(bytes, resumed)
    }

    private fun clearPart(context: Context, name: String) {
        File(partDir(context), name).delete()
    }

    /** 校验通过后落盘:备份旧文件 → 写入 → 热重载 → 记历史。更新器与测试共用。 */
    fun applyUpdate(context: Context, name: String, data: ByteArray, resumed: Boolean = false): UpdateResult {
        return try {
            val out = File(clamavDir(context), name)
            out.parentFile?.mkdirs()
            if (out.isFile) {
                val bdir = backupDir(context)
                bdir.mkdirs()
                out.copyTo(File(bdir, name), overwrite = true)
            }
            out.writeBytes(data)
            clearPart(context, name)
            ClamAvSignatures.reload(context)
            val truncatedNote = if (ClamAvSignatures.isTruncated()) ";特征库过大,已截断加载" else ""
            val message = "更新成功:" + name + " (" + data.size / 1024 + "KB" +
                (if (resumed) ",断点续传" else "") + "),当前 ClamAV 签名 " +
                (ClamAvSignatures.hashCount() + ClamAvSignatures.md5Count()) + " 哈希 / " +
                ClamAvSignatures.byteCount() + " 字节 / " +
                ClamAvSignatures.logicalCount() + " 逻辑" + truncatedNote
            addHistory(context, JSONObject()
                .put("time", System.currentTimeMillis())
                .put("file", name)
                .put("size", data.size)
                .put("ok", true))
            UpdateResult(true, message)
        } catch (e: Exception) {
            UpdateResult(false, "写入失败: " + e.message)
        }
    }

    /** 回滚:把 backup/ 下的同名旧文件还原并热重载;返回结果条目文本 */
    fun rollbackUpdate(context: Context): String {
        val bdir = backupDir(context)
        val files = bdir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (files.isEmpty()) return "无可回滚的备份"
        val restored = mutableListOf<String>()
        for (b in files) {
            try {
                b.copyTo(File(clamavDir(context), b.name), overwrite = true)
                restored.add(b.name)
            } catch (_: Exception) {
            }
        }
        if (restored.isEmpty()) return "回滚失败: 备份无法复制"
        ClamAvSignatures.reload(context)
        addHistory(context, JSONObject()
            .put("time", System.currentTimeMillis())
            .put("file", restored.joinToString(","))
            .put("size", 0)
            .put("ok", true)
            .put("rollback", true))
        return "已回滚到上一版: " + restored.joinToString(", ")
    }

    private fun historyPrefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun addHistory(context: Context, entry: JSONObject) {
        try {
            val p = historyPrefs(context)
            val arr = JSONArray(p.getString(HISTORY_KEY, "[]") ?: "[]")
            val next = JSONArray()
            next.put(entry)
            for (i in 0 until arr.length()) {
                if (next.length() >= HISTORY_MAX) break
                next.put(arr.get(i))
            }
            p.edit().putString(HISTORY_KEY, next.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /** 更新历史(JSON 数组字符串,新→旧,最多 20 条) */
    fun historyJson(context: Context): String =
        try {
            historyPrefs(context).getString(HISTORY_KEY, "[]") ?: "[]"
        } catch (_: Exception) {
            "[]"
        }
}
