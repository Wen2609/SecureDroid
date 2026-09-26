package com.armorlab.securedroid.feature

import android.content.Context
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.root.ShellBridge
import java.io.File
import java.net.InetAddress

/** 网络连接审计:解析 /proc/net/tcp,把连接 UID 归属到应用(Root 下全局可见) */
object NetAudit {

    data class Conn(val appLabel: String, val pkg: String, val remote: String)

    fun established(context: Context, limit: Int = 120): List<Conn> {
        var raw = try { File("/proc/net/tcp").readText() } catch (_: Exception) { "" }
        // Root 模式下用 su 读取全局连接表(免 root 只能看到本应用连接)
        if (RootGuard.isRootMode(context)) {
            raw = ShellBridge.runSu("cat /proc/net/tcp") ?: raw
        }
        val pm = context.packageManager
        val out = mutableListOf<Conn>()
        val seen = HashSet<String>()
        for (line0 in raw.lines()) {
            val line = line0.trim()
            if (line.isEmpty() || !line[0].isDigit()) continue
            val f = line.split(Regex("\\s+"))
            if (f.size < 8) continue
            if (f[3] != "01") continue // 仅 ESTABLISHED
            val uid = f[7].toIntOrNull() ?: continue
            val remote = try { hexIpPort(f[2]) } catch (_: Exception) { continue }
            if (remote.startsWith("0.0.0.0") || remote.startsWith("127.0.0.1")) continue
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: ("uid:" + uid)
            val label = try {
                pm.getApplicationInfo(pkg, 0)?.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) { pkg }
            val key = pkg + "|" + remote
            if (!seen.add(key)) continue
            out.add(Conn(label, pkg, remote))
            if (out.size >= limit) break
        }
        return out
    }

    /** "0100007F:1F90" → "127.0.0.1:8080"(小端序) */
    private fun hexIpPort(field: String): String {
        val parts = field.split(':')
        val hex = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull(16) ?: 0
        val b = ByteArray(4)
        for (i in 0..3) b[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return InetAddress.getByAddress(b).hostAddress + ":" + port
    }
}
