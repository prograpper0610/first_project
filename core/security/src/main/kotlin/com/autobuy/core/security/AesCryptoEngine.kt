package com.autobuy.core.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM 암호화/복호화 유틸리티.
 *
 * - GCM 모드: 인증 태그(Authentication Tag)를 통해 무결성 검증.
 * - IV(Nonce)는 암호화마다 새로 생성하고 암호문과 함께 저장.
 * - 저장 형식: Base64(IV(12 bytes) + Ciphertext + AuthTag(16 bytes))
 */
@Singleton
class AesCryptoEngine @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12   // 96-bit IV (GCM 권장값)
        private const val GCM_TAG_LENGTH = 128  // 128-bit Auth Tag
    }

    /**
     * 평문 문자열을 AES-256-GCM으로 암호화합니다.
     * @return Base64 인코딩된 암호문 (IV + Ciphertext + AuthTag)
     */
    fun encrypt(plaintext: String): String {
        val secretKey = keystoreManager.getOrCreateMasterKey()
        return encrypt(plaintext.toByteArray(Charsets.UTF_8), secretKey)
    }

    fun encrypt(plaintext: ByteArray, key: SecretKey = keystoreManager.getOrCreateMasterKey()): String {
        val iv = generateIv()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)

        // IV + Ciphertext 결합
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Base64 암호문을 복호화하여 평문 문자열로 반환합니다.
     */
    fun decrypt(encryptedBase64: String): String {
        val secretKey = keystoreManager.getOrCreateMasterKey()
        return String(decrypt(encryptedBase64, secretKey), Charsets.UTF_8)
    }

    fun decrypt(encryptedBase64: String, key: SecretKey = keystoreManager.getOrCreateMasterKey()): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        // IV 추출 (앞 12 bytes)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        // Ciphertext + AuthTag 추출
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * 민감 정보(카드번호 등)를 암호화하고 즉시 원본 ByteArray를 제로 초기화합니다.
     * String을 사용하면 GC 전 메모리에 잔존하므로 CharArray로 처리합니다.
     */
    fun encryptSensitive(chars: CharArray): String {
        val bytes = chars.map { it.code.toByte() }.toByteArray()
        return try {
            encrypt(bytes)
        } finally {
            // 보안: 즉시 메모리 제로화
            bytes.fill(0)
            chars.fill('\u0000')
        }
    }

    /**
     * 복호화된 민감 정보를 사용 후 즉시 메모리에서 제거하기 위해 CharArray로 반환합니다.
     * 사용자는 use 블록 후 반드시 fill('\u0000') 처리를 해야 합니다.
     */
    fun decryptSensitive(encryptedBase64: String): CharArray {
        val bytes = decrypt(encryptedBase64)
        return try {
            CharArray(bytes.size) { bytes[it].toInt().toChar() }
        } finally {
            bytes.fill(0)
        }
    }

    private fun generateIv(): ByteArray {
        return ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
    }
}
