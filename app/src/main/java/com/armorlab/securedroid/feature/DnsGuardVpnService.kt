package com.armorlab.securedroid.feature

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.armorlab.securedroid.MainActivity
import com.armorlab.securedroid.R
import com.armorlab.securedroid.realtime.SecureGuardAppRefs
import com.armorlab.securedroid.vscan.BlocklistEngine
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * DNS 防护 VPN(实验):只把伪造 DNS 地址(10.111.222.2/32)路由进 tun,
 * 其余流量零感知;系统 DNS 查询被捕获后做黑名单判定 —— 命中直接回 NXDOMAIN,
 * 未命中经受保护的 socket 转发真实上游并回包。
 *
 * 约束:
 * - 不读取、不记录、不上传任何用户流量;只对 Question 区域名做子串匹配;
 * - 上游 DNS 取系统当前网络的 LinkProperties(建立隧道前),取不到回退 223.5.5.5;
 * - establish() 失败(授权被吊销等)自动停服,绝不重试循环。
 */
class DnsGuardVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var running = false

    private val executor = Executors.newCachedThreadPool()
    private var blockPatterns: List<String> = emptyList()
    private var upstreamDns = FALLBACK_DNS

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        blockPatterns = BlocklistEngine.patterns(this)
        upstreamDns = resolveUpstreamDns()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            Thread({ tunLoop() }, "dns-guard-tun").start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun establish(): ParcelFileDescriptor? = try {
        Builder()
            .setSession(getString(R.string.dns_guard_session))
            .addAddress(DnsGuardEngine.TUN_ADDR, 32)
            .addDnsServer(DnsGuardEngine.FAKE_DNS)
            .addRoute(DnsGuardEngine.FAKE_DNS, 32)
            .setMtu(1500)
            .establish()
    } catch (_: Exception) {
        null
    }

    private fun resolveUpstreamDns(): String = try {
        val cm = getSystemService(ConnectivityManager::class.java)
        val lp = cm?.getLinkProperties(cm.activeNetwork)
        lp?.dnsServers?.firstOrNull()?.hostAddress?.takeIf { it.isNotBlank() }
            ?: FALLBACK_DNS
    } catch (_: Exception) {
        FALLBACK_DNS
    }

    private fun tunLoop() {
        val fd = establish()
        if (fd == null) {
            stopSelf()
            return
        }
        tun = fd
        try {
            FileInputStream(fd.fileDescriptor).use { input ->
                FileOutputStream(fd.fileDescriptor).use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (running) {
                        val length = try {
                            input.read(buf)
                        } catch (_: Exception) {
                            break
                        }
                        if (length <= 0) break
                        handlePacket(buf, length, output)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (running) stopSelf()
        }
    }

    /** 单包处理:只关心 UDP/53 发往伪造 DNS 的查询;解析失败一律放行转发(不误杀) */
    private fun handlePacket(buf: ByteArray, length: Int, output: FileOutputStream) {
        val ip = DnsGuardEngine.parseIpv4(buf, length) ?: return
        if (ip.protocol != PROTO_UDP) return
        val ihl = ip.ihl
        if (length < ihl + 8) return
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        if (dstPort != DnsGuardEngine.DNS_PORT) return
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val udpLen = ((buf[ihl + 4].toInt() and 0xFF) shl 8) or (buf[ihl + 5].toInt() and 0xFF)
        if (udpLen < 8 || ihl + udpLen > length) return
        val payload = buf.copyOfRange(ihl + 8, ihl + udpLen)

        executor.submit {
            try {
                val domain = DnsGuardEngine.queryDomain(payload)
                val answer: ByteArray? = if (domain != null &&
                    DnsGuardEngine.isBlocked(domain, blockPatterns)
                ) {
                    DnsGuardEngine.buildNxDomainResponse(payload)
                } else {
                    forward(payload)
                }
                if (answer != null) {
                    val packet = DnsGuardEngine.buildUdp4Packet(
                        DnsGuardEngine.ipv4Bytes(DnsGuardEngine.FAKE_DNS), DnsGuardEngine.DNS_PORT,
                        ip.srcIp, srcPort,
                        answer
                    )
                    synchronized(output) { output.write(packet) }
                }
            } catch (_: Exception) {
            }
        }
    }

    /** 经受保护 socket 转发到真实上游,5 秒超时;失败返回 null(客户端自行重试) */
    private fun forward(query: ByteArray): ByteArray? {
        val socket = DatagramSocket().apply { soTimeout = 5_000 }
        try {
            protect(socket)
            socket.send(
                DatagramPacket(query, query.size, InetAddress.getByName(upstreamDns), DnsGuardEngine.DNS_PORT)
            )
            val buf = ByteArray(4096)
            val resp = DatagramPacket(buf, buf.size)
            socket.receive(resp)
            return resp.data.copyOfRange(0, resp.length)
        } catch (_: Exception) {
            return null
        } finally {
            socket.close()
        }
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, SecureGuardAppRefs.CHANNEL_REALTIME)
            .setSmallIcon(R.drawable.ic_sd_shield_check)
            .setContentTitle(getString(R.string.dns_guard_notif_title))
            .setContentText(getString(R.string.dns_guard_notif_text))
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()
        val type = if (android.os.Build.VERSION.SDK_INT >= 34) {
            // specialUse(systemExempted 还要求精确闹钟权限,不合理);
            // VPN 身份由 BIND_VPN_SERVICE 系统绑定决定,FGS 类型只影响策略归类
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    companion object {
        private const val PROTO_UDP = 17
        private const val FALLBACK_DNS = "223.5.5.5"
        private const val NOTIF_ID = 3001
        const val ACTION_STOP = "com.armorlab.securedroid.dnsguard.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, DnsGuardVpnService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DnsGuardVpnService::class.java).setAction(ACTION_STOP)
            )
        }

        /** 已授权且无 prepare 弹窗时返回 null;需要授权时返回待启动的 Intent */
        fun prepare(activity: android.app.Activity): Intent? = VpnService.prepare(activity)
    }
}
