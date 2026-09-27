package com.armorlab.securedroid.feature

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 文件粉碎器:对所选文件整体覆写随机数据后删除(尽力而为;
 * 受 FBE 加密与闪存磨损均衡影响,不承诺物理级不可恢复)。
 */
object ShredTool {

    fun fileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst() && idx >= 0) return c.getLong(idx)
        }
        return -1L
    }

    fun shred(context: Context, uri: Uri): Boolean {
        return try {
            val size = fileSize(context, uri)
            if (size < 0) return false
            context.contentResolver.openOutputStream(uri, "rw")?.use { os ->
                val rnd = java.security.SecureRandom()
                val buf = ByteArray(65536)
                var left = size
                while (left > 0) {
                    rnd.nextBytes(buf)
                    val n = minOf(buf.size.toLong(), left).toInt()
                    os.write(buf, 0, n)
                    left -= n
                }
                os.flush()
                (os as? java.io.FileOutputStream)?.fd?.sync()
            } ?: return false
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }
}
