package com.armorlab.securedroid.vscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 木马家族分类:纯逻辑,确保关键词命中映射稳定(误分类会直接误导用户处置) */
class FamilyClassifierTest {

    @Test
    fun classifiesSmsAndBankerFamily() {
        assertTrue(FamilyClassifier.classify(listOf("SmsSend")).contains("扣费"))
        assertTrue(FamilyClassifier.classify(listOf("PremiumDial")).contains("扣费"))
    }

    @Test
    fun classifiesRansomFamily() {
        assertTrue(FamilyClassifier.classify(listOf("RansomNote")).contains("勒索"))
    }

    @Test
    fun classifiesLockerFamily() {
        assertTrue(FamilyClassifier.classify(listOf("Locker.LockNow")).contains("锁机"))
    }

    @Test
    fun classifiesSpyFamily() {
        assertTrue(FamilyClassifier.classify(listOf("SpyClip")).contains("间谍"))
        assertTrue(FamilyClassifier.classify(listOf("ClipSpy")).contains("间谍"))
    }

    @Test
    fun classifiesBackdoorFamily() {
        assertTrue(FamilyClassifier.classify(listOf("ReverseShell")).contains("后门"))
        assertTrue(FamilyClassifier.classify(listOf("Backdoor.Connect")).contains("后门"))
    }

    @Test
    fun classifiesMinerFamily() {
        assertTrue(FamilyClassifier.classify(listOf("Miner.XMR")).contains("挖矿"))
        assertTrue(FamilyClassifier.classify(listOf("HighCpu")).contains("挖矿"))
    }

    @Test
    fun classifiesPrivilegeEscalationFamily() {
        assertTrue(FamilyClassifier.classify(listOf("PrivEsc")).contains("提权"))
        assertTrue(FamilyClassifier.classify(listOf("SuBinary")).contains("提权"))
    }

    @Test
    fun fallsBackToGenericForUnknownSignals() {
        assertEquals("通用可疑(未定型)", FamilyClassifier.classify(emptyList()))
        assertEquals("通用可疑(未定型)", FamilyClassifier.classify(listOf("Unknown.Thing")))
    }

    @Test
    fun familyPriorityIsDeterministicForMixedSignals() {
        // 混合信号按规则表顺序取首个命中家族(Sms 优先于 Ransom),
        // 且与检测项顺序无关 —— 同一应用不同扫描轮次必须归入同一家族
        val mixed = listOf("SmsSend", "RansomNote")
        assertEquals("扣费/银行木马家族(Banker/SmsTrojan)", FamilyClassifier.classify(mixed))
        assertEquals(FamilyClassifier.classify(mixed), FamilyClassifier.classify(mixed.reversed()))
    }
}
