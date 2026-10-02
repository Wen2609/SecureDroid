package com.armorlab.securedroid.feature

import com.armorlab.securedroid.core.Re
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * DNS 劫持检测:将系统解析结果与 DoH(AliDNS 223.5.5.5 加密解析)对比,
 * 不一致则怀疑被劫持/污染。
 */
object DnsGuard {

    private val probes = listOf("www.baidu.com", "www.qq.com", "www.taobao.com")

    data class Result(val host: String, val systemIps: Set<String>, val dohIps: Set<String>) {
        val verified: Boolean get() = dohIps.isNotEmpty()
        val hijacked: Boolean get() = verified && (systemIps - dohIps).isNotEmpty()
    }

    fun probe(): List<Result> = probes.map { probeOne(it) }

    private fun probeOne(host: String): Result {
        val systemIps = try {
            InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.toSet()
        } catch (_: Exception) { emptySet<String>() }
        val dohIps = try { dohQuery(host) } catch (_: Exception) { emptySet<String>() }
        return Result(host, systemIps, dohIps)
    }

    /** DoH JSON-API 查询(轻量解析,不引 JSON 库) */
    private fun dohQuery(host: String): Set<String> {
        val conn = URL("https://223.5.5.5/resolve?name=" + host + "&type=A")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("accept", "application/dns-json")
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val result = HashSet<String>()
        val rex = Re.DNS_A_RECORD
        for (m in rex.findAll(body)) result.add(m.groupValues[1])
        return result
    }
}
