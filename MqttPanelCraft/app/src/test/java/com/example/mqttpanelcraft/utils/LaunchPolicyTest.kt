package com.example.mqttpanelcraft.utils

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class LaunchPolicyTest {
    @Test fun adsRequireEveryGate() {
        for (age in listOf(false, true)) for (consent in listOf(false, true))
            for (premium in listOf(false, true)) for (tutorial in listOf(false, true))
                assertEquals(age && consent && !premium && !tutorial,
                    AdvertisingPolicy.canRequest(age, consent, premium, tutorial))
    }
    @Test fun cooldownIsThreeFiveThenTenMinutes() {
        val clock = InterstitialCooldown(0)
        assertFalse(clock.eligible(179999))
        assertTrue(clock.eligible(180000))
        clock.shown(180000)
        assertFalse(clock.eligible(479999))
        assertTrue(clock.eligible(480000))
        clock.shown(480000)
        assertFalse(clock.eligible(1079999))
        assertTrue(clock.eligible(1080000))
    }
    @Test fun onlyAuthenticUnchangedReceiptsVerify() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val json = "{\"productId\":\"premium_unlock\"}"
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(pair.private); update(json.toByteArray(Charsets.UTF_8)); sign()
        }
        assertTrue(PurchaseSignatureVerifier.verify(pair.public.encoded, json, signature))
        assertFalse(PurchaseSignatureVerifier.verify(pair.public.encoded, json + " ", signature))
        assertFalse(PurchaseSignatureVerifier.verify(byteArrayOf(), json, signature))
        assertFalse(PurchaseSignatureVerifier.verify(pair.public.encoded, json, byteArrayOf()))
    }
}
