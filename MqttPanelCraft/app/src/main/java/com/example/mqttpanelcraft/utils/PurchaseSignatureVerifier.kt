package com.example.mqttpanelcraft.utils

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PurchaseSignatureVerifier {
    fun verify(publicKey: ByteArray, signedData: String, signature: ByteArray): Boolean = try {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKey))
        Signature.getInstance("SHA1withRSA").run {
            initVerify(key)
            update(signedData.toByteArray(Charsets.UTF_8))
            verify(signature)
        }
    } catch (_: Exception) { false }
}
