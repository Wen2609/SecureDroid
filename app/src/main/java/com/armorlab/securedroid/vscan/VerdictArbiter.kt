package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.scan.ThreatLevel

/**
 * 多引擎结论仲裁:只有 >=2 个独立证据源给出高危结论才判"恶意",
 * 单引擎高危降级为"待观察",显著降低误报。
 */
object VerdictArbiter {

    enum class Verdict { MALICIOUS, SUSPICIOUS, CLEAN }

    fun arbitrate(
        engineHitsHigh: Int,
        permScore: Int
    ): Verdict {
        return if (engineHitsHigh >= 2) Verdict.MALICIOUS
        else if (engineHitsHigh == 1 || permScore >= 40) Verdict.SUSPICIOUS
        else Verdict.CLEAN
    }

    fun levelFor(verdict: Verdict, raw: ThreatLevel): ThreatLevel = when (verdict) {
        Verdict.MALICIOUS -> raw
        Verdict.SUSPICIOUS -> ThreatLevel.MEDIUM
        Verdict.CLEAN -> ThreatLevel.LOW
    }
}
