package com.autobuy.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore를 사용한 AES-256 마스터키 관리자.
 *
 * - 키는 Keystore Hardware-backed TEE/SE 내부에 저장됩니다.
 * - 앱 외부에서는 절대로 키 원본에 접근할 수 없습니다.
 * - 마스터키는 최초 1회만 생성하고 이후에는 기존 키를 재사용합니다.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "autobuy_master_key_v1"
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val KEY_SIZE = 256
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    }

    /**
     * 마스터키를 가져옵니다. 존재하지 않으면 새로 생성합니다.
     */
    fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            generateMasterKey()
        }
    }

    private fun generateMasterKey(): SecretKey {
        val keyGenParams = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setUserAuthenticationRequired(false)  // 앱 자체 인증으로 관리
            .setRandomizedEncryptionRequired(true)
            .build()

        return KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE).apply {
            init(keyGenParams)
        }.generateKey()
    }

    /**
     * 키가 Keystore에 존재하는지 확인합니다.
     */
    fun hasMasterKey(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.containsAlias(MASTER_KEY_ALIAS)
    }

    /**
     * 마스터키를 삭제합니다. (앱 데이터 초기화 시 사용)
     */
    fun deleteMasterKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        }
    }
}
