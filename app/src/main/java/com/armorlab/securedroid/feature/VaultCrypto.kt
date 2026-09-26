package com.armorlab.securedroid.feature

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 文件保险箱:PBKDF2WithHmacSHA256(60 万次迭代)派生密钥,
 * AES-256-GCM 认证加密。文件格式:SDV1 + salt(16) + iv(12) + 密文。
 */
object VaultCrypto {

    private const val MAGIC = "SDV1"
    private const val ITERATIONS = 600_000

    fun encrypt(password: String, plain: ByteArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        return MAGIC.toByteArray(Charsets.UTF_8) + salt + iv + ct
    }

    fun decrypt(password: String, blob: ByteArray): ByteArray {
        if (blob.size < 32 || !blob.copyOfRange(0, 4).toString(Charsets.UTF_8).startsWith(MAGIC)) {
            throw IllegalArgumentException("不是本保险箱加密的文件")
        }
        val salt = blob.copyOfRange(4, 20)
        val iv = blob.copyOfRange(20, 32)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(blob.copyOfRange(32, blob.size))
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
