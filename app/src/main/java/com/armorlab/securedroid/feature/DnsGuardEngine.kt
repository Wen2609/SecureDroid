package com.armorlab.securedroid.feature

/**
 * DNS 防护纯逻辑:DNS 报文解析、拦截判定、NXDOMAIN 应答构造。
 * 全部为无副作用的字节级操作,与 tun 读写解耦 —— 便于单测与复用。
 *
 * DNS 报文(UDP 载荷)头部 12 字节:
 *   ID(2) Flags(2) QDCOUNT(2) ANCOUNT(2) NSCOUNT(2) ARCOUNT(2)
 * Flags 位:QR(15) Opcode(14-11) AA(10) TC(9) RD(8) | RA(7) Z(6-4) RCODE(3-0)
 */
object DnsGuardEngine {

    /** 隧道内本端地址(仅占位,不承载业务流量) */
    const val TUN_ADDR = "10.111.222.1"

    /** 伪造 DNS 服务器地址:唯一被路由进隧道的 /32,系统 DNS 查询全部被捕获到这里 */
    const val FAKE_DNS = "10.111.222.2"

    const val DNS_PORT = 53

    /** 解析 DNS 查询(QR=0)的 Question 域名;非查询或格式非法返回 null */
    fun queryDomain(dnsPayload: ByteArray): String? {
        if (dnsPayload.size < 12) return null
        val flags = ((dnsPayload[2].toInt() and 0xFF) shl 8) or (dnsPayload[3].toInt() and 0xFF)
        if ((flags shr 15) and 1 == 1) return null
        val qdcount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        if (qdcount == 0) return null
        var pos = 12
        val sb = StringBuilder()
        while (pos < dnsPayload.size) {
            val len = dnsPayload[pos].toInt() and 0xFF
            if (len == 0) break
            if (len > 63 || pos + 1 + len > dnsPayload.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) {
                val c = dnsPayload[pos + i].toInt() and 0xFF
                if (c !in 0x21..0x7E) return null
                sb.append(c.toChar())
            }
            pos += 1 + len
        }
        if (sb.isEmpty()) return null
        return sb.toString().lowercase()
    }

    /** 域名是否命中拦截模式(子串语义,与 BlocklistEngine 一致;域名与模式都按不区分大小写比对) */
    fun isBlocked(domain: String, patterns: List<String>): Boolean {
        val d = domain.lowercase()
        return patterns.any { it.isNotBlank() && d.contains(it.trim().lowercase()) }
    }

    /** 点分十进制 → 4 字节(解析失败按 0 处理,仅用于回包地址填装) */
    fun ipv4Bytes(dotted: String): ByteArray {
        val parts = dotted.split('.')
        return ByteArray(4) { (parts[it].toIntOrNull() ?: 0).toByte() }
    }

    /**
     * 构造 NXDOMAIN 应答:复制事务 ID 与 Question 区,置 QR=1/RA=1/RCODE=3,
     * AN/NS/AR 计数清零( EDNS OPT 附加区一并丢弃,避免计数与内容不一致)。
     * 无法解析出完整 Question 区时返回 null(调用方丢弃该查询)。
     */
    fun buildNxDomainResponse(query: ByteArray): ByteArray? {
        if (query.size < 17) return null
        var pos = 12
        var ended = false
        while (pos < query.size) {
            val len = query[pos].toInt() and 0xFF
            if (len == 0) {
                pos += 1
                ended = true
                break
            }
            if (len > 63 || pos + 1 + len >= query.size) return null
            pos += 1 + len
        }
        if (!ended) return null
        pos += 4 // QTYPE + QCLASS
        if (pos > query.size) return null
        val out = query.copyOf(pos)
        val flags = ((query[2].toInt() and 0xFF) shl 8) or (query[3].toInt() and 0xFF)
        val newFlags = ((flags or 0x8080) and 0xFFF0) or 0x0003
        out[2] = ((newFlags shr 8) and 0xFF).toByte()
        out[3] = (newFlags and 0xFF).toByte()
        for (i in 6..11) out[i] = 0
        return out
    }

    /** 组装 IPv4 + UDP 回包(校验和:IPv4 头必算,UDP 置 0 —— IPv4 下合法) */
    fun buildUdp4Packet(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val out = ByteArray(total)
        out[0] = 0x45
        out[1] = 0
        out[2] = ((total shr 8) and 0xFF).toByte()
        out[3] = (total and 0xFF).toByte()
        out[4] = 0; out[5] = 0
        out[6] = 0x40; out[7] = 0
        out[8] = 64
        out[9] = 17
        out[10] = 0; out[11] = 0 // checksum 后填
        srcIp.copyInto(out, 12)
        dstIp.copyInto(out, 16)
        out[20] = ((srcPort shr 8) and 0xFF).toByte()
        out[21] = (srcPort and 0xFF).toByte()
        out[22] = ((dstPort shr 8) and 0xFF).toByte()
        out[23] = (dstPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        out[24] = ((udpLen shr 8) and 0xFF).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        out[26] = 0; out[27] = 0
        payload.copyInto(out, 28)
        val sum = ipChecksum(out, 20)
        out[10] = ((sum shr 8) and 0xFF).toByte()
        out[11] = (sum and 0xFF).toByte()
        return out
    }

    /** IPv4 头校验和(16 位反码求和) */
    fun ipChecksum(header: ByteArray, headerLength: Int): Int {
        var sum = 0L
        var i = 0
        while (i < headerLength) {
            sum += ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return ((sum.toInt()).inv()) and 0xFFFF
    }

    /** 解析 IPv4 包:返回 (IHL, 协议, 源IP, 目的IP, 总长);非 IPv4 返回 null */
    fun parseIpv4(packet: ByteArray, length: Int): Ipv4Info? {
        if (length < 20) return null
        if (((packet[0].toInt() and 0xF0) shr 4) != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ihl > length) return null
        val total = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val proto = packet[9].toInt() and 0xFF
        val src = packet.copyOfRange(12, 16)
        val dst = packet.copyOfRange(16, 20)
        return Ipv4Info(ihl, proto, src, dst, total.coerceAtMost(length))
    }

    class Ipv4Info(
        val ihl: Int,
        val protocol: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val totalLength: Int
    )
}
