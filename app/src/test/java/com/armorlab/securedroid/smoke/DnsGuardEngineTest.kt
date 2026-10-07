package com.armorlab.securedroid.smoke

import com.armorlab.securedroid.feature.DnsGuardEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DNS 防护纯逻辑测试:解析/拦截/NXDOMAIN 构造是拦截语义的核心 ——
 * 解析错一个字节就会把正常域名判成恶意(误杀)或漏掉恶意域名。
 */
class DnsGuardEngineTest {

    /** 构造标准 DNS 查询:example.com A IN */
    private fun exampleQuery(): ByteArray {
        val qname = byteArrayOf(
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0
        )
        val packet = ByteArray(12 + qname.size + 4)
        packet[0] = 0x12; packet[1] = 0x34            // 事务 ID
        packet[2] = 0x01; packet[3] = 0x00            // RD=1, QR=0
        packet[4] = 0; packet[5] = 1                  // QDCOUNT=1
        qname.copyInto(packet, 12)
        val tail = 12 + qname.size
        packet[tail] = 0; packet[tail + 1] = 1        // QTYPE=A
        packet[tail + 2] = 0; packet[tail + 3] = 1    // QCLASS=IN
        return packet
    }

    @Test
    fun parsesQueryDomain() {
        assertEquals("example.com", DnsGuardEngine.queryDomain(exampleQuery()))
    }

    @Test
    fun rejectsResponseAndMalformedPackets() {
        // QR=1 是应答,不是查询
        val resp = exampleQuery()
        resp[2] = 0x81.toByte()
        assertNull(DnsGuardEngine.queryDomain(resp))
        // 标签长度越界
        assertNull(DnsGuardEngine.queryDomain(ByteArray(12)))
        assertNull(DnsGuardEngine.queryDomain(ByteArray(0)))
        val bad = exampleQuery()
        bad[12] = 0x40 // 标签长度 64 > 63
        assertNull(DnsGuardEngine.queryDomain(bad))
        // QDCOUNT=0
        val noQd = exampleQuery()
        noQd[5] = 0
        assertNull(DnsGuardEngine.queryDomain(noQd))
    }

    @Test
    fun blockedMatchesSubstringCaseInsensitive() {
        val patterns = listOf("duckdns.org", ".onion", "evil")
        assertTrue(DnsGuardEngine.isBlocked("my.evil.duckdns.org", patterns))
        assertTrue(DnsGuardEngine.isBlocked("tunnel.DUCKDNS.ORG", patterns))
        assertTrue(DnsGuardEngine.isBlocked("x.onion", patterns))
        assertFalse(DnsGuardEngine.isBlocked("example.com", patterns))
        assertFalse(DnsGuardEngine.isBlocked("not-evil-but-clean.cn", listOf(" ")))
    }

    @Test
    fun nxDomainResponseKeepsTransactionAndQuestion() {
        val query = exampleQuery()
        val resp = DnsGuardEngine.buildNxDomainResponse(query)!!
        assertEquals("事务 ID 必须一致", query[0], resp[0])
        assertEquals(query[1], resp[1])
        val flags = ((resp[2].toInt() and 0xFF) shl 8) or (resp[3].toInt() and 0xFF)
        assertTrue("QR 必须置位", (flags shr 15) and 1 == 1)
        assertTrue("RA 必须置位", (flags shr 7) and 1 == 1)
        assertEquals("RCODE=3(NXDOMAIN)", 3, flags and 0xF)
        assertEquals("ANCOUNT 必须为 0", 0, resp[6].toInt() and 0xFF)
        assertEquals("NSCOUNT 必须为 0", 0, resp[7].toInt() and 0xFF)
        assertEquals("ARCOUNT 必须为 0", 0, resp[8].toInt() and 0xFF)
        assertEquals("应答长度 = 头 + 问题区", 12 + 13 + 4, resp.size)
        assertArrayEquals(
            "问题区必须逐字节一致",
            query.copyOfRange(12, query.size),
            resp.copyOfRange(12, resp.size)
        )
    }

    @Test
    fun udpPacketHasValidIpChecksum() {
        val src = DnsGuardEngine.ipv4Bytes("10.111.222.2")
        val dst = DnsGuardEngine.ipv4Bytes("192.168.1.5")
        val payload = ByteArray(20) { it.toByte() }
        val packet = DnsGuardEngine.buildUdp4Packet(src, 53, dst, 51332, payload)
        assertEquals(20 + 8 + payload.size, ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF))
        // 校验和自洽:清零校验和字段后重算,必须与存储值一致
        val stored = ((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF)
        packet[10] = 0
        packet[11] = 0
        val recomputed = DnsGuardEngine.ipChecksum(packet, 20)
        assertEquals("IPv4 头校验和必须自洽", recomputed, stored)
        // 源/目的 IP 填装正确
        assertArrayEquals(src, packet.copyOfRange(12, 16))
        assertArrayEquals(dst, packet.copyOfRange(16, 20))
    }

    @Test
    fun parsesIpv4Header() {
        val dst = DnsGuardEngine.ipv4Bytes("10.111.222.2")
        val src = DnsGuardEngine.ipv4Bytes("192.168.1.5")
        val packet = DnsGuardEngine.buildUdp4Packet(src, 51332, dst, 53, ByteArray(12))
        val info = DnsGuardEngine.parseIpv4(packet, packet.size)!!
        assertEquals(20, info.ihl)
        assertEquals(17, info.protocol)
        assertArrayEquals(src, info.srcIp)
        assertArrayEquals(dst, info.dstIp)
        assertNull(DnsGuardEngine.parseIpv4(ByteArray(10), 10))
    }
}
