package com.autobuy.core.security

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 PII(개인정보) 및 동적 커스텀 필드를 암호화하여 저장하는 보안 저장소.
 *
 * 확장성:
 * - DeliveryInfo, PaymentInfo 외에 사용자 임의 정의 Key-Value Map(customFields)도
 *   AES-256-GCM으로 암호화하여 저장할 수 있습니다.
 */
@Singleton
class SecureVault @Inject constructor(
    private val cryptoEngine: AesCryptoEngine
) {
    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    /**
     * 배송지 정보를 암호화합니다.
     */
    fun encryptDeliveryInfo(info: DeliveryInfo): EncryptedDeliveryInfo {
        val customFieldsJson = if (info.customFields.isNotEmpty()) {
            mapAdapter.toJson(info.customFields)
        } else null

        return EncryptedDeliveryInfo(
            encryptedName = cryptoEngine.encrypt(info.recipientName),
            encryptedPhone = cryptoEngine.encrypt(info.phoneNumber),
            encryptedAddress = cryptoEngine.encrypt(info.address),
            encryptedAddressDetail = cryptoEngine.encrypt(info.addressDetail),
            encryptedPostalCode = cryptoEngine.encrypt(info.postalCode),
            encryptedCustomFieldsJson = customFieldsJson?.let { cryptoEngine.encrypt(it) }
        )
    }

    /**
     * 암호화된 배송지 정보를 복호화합니다.
     */
    fun decryptDeliveryInfo(encrypted: EncryptedDeliveryInfo): DeliveryInfo {
        val customMap = encrypted.encryptedCustomFieldsJson?.let { encJson ->
            val json = cryptoEngine.decrypt(encJson)
            mapAdapter.fromJson(json)
        } ?: emptyMap()

        return DeliveryInfo(
            recipientName = cryptoEngine.decrypt(encrypted.encryptedName),
            phoneNumber = cryptoEngine.decrypt(encrypted.encryptedPhone),
            address = cryptoEngine.decrypt(encrypted.encryptedAddress),
            addressDetail = cryptoEngine.decrypt(encrypted.encryptedAddressDetail),
            postalCode = cryptoEngine.decrypt(encrypted.encryptedPostalCode),
            customFields = customMap
        )
    }

    /**
     * 카드 정보를 암호화합니다. (CharArray 입력으로 메모리 보안 강화)
     */
    fun encryptPaymentInfo(info: PaymentInfoRaw): EncryptedPaymentInfo {
        val customFieldsJson = if (info.customFields.isNotEmpty()) {
            mapAdapter.toJson(info.customFields)
        } else null

        return EncryptedPaymentInfo(
            encryptedCardNumber = cryptoEngine.encryptSensitive(info.cardNumber.toCharArray()),
            encryptedExpiry = cryptoEngine.encrypt(info.expiry),
            encryptedCvv = cryptoEngine.encryptSensitive(info.cvv.toCharArray()),
            encryptedCardHolder = cryptoEngine.encrypt(info.cardHolder),
            maskedCardNumber = maskCardNumber(info.cardNumber),
            encryptedCustomFieldsJson = customFieldsJson?.let { cryptoEngine.encrypt(it) }
        ).also {
            // 원본 데이터 즉시 메모리 제거
            info.cardNumber.fill('0')
            info.cvv.fill('0')
        }
    }

    /**
     * 암호화된 카드 정보를 복호화합니다.
     */
    fun decryptPaymentInfo(encrypted: EncryptedPaymentInfo): PaymentInfoDecrypted {
        val customMap = encrypted.encryptedCustomFieldsJson?.let { encJson ->
            val json = cryptoEngine.decrypt(encJson)
            mapAdapter.fromJson(json)
        } ?: emptyMap()

        return PaymentInfoDecrypted(
            cardNumber = cryptoEngine.decryptSensitive(encrypted.encryptedCardNumber),
            expiry = cryptoEngine.decrypt(encrypted.encryptedExpiry),
            cvv = cryptoEngine.decryptSensitive(encrypted.encryptedCvv),
            cardHolder = cryptoEngine.decrypt(encrypted.encryptedCardHolder),
            customFields = customMap
        )
    }

    private fun maskCardNumber(cardNumber: CharArray): String {
        val s = String(cardNumber)
        return if (s.length >= 8) "**** **** **** ${s.takeLast(4)}" else "****"
    }
}

// ==================== Data Classes ====================

data class DeliveryInfo(
    val recipientName: String,
    val phoneNumber: String,
    val address: String,
    val addressDetail: String,
    val postalCode: String,
    val customFields: Map<String, String> = emptyMap() // 확장 커스텀 필드
)

data class EncryptedDeliveryInfo(
    val encryptedName: String,
    val encryptedPhone: String,
    val encryptedAddress: String,
    val encryptedAddressDetail: String,
    val encryptedPostalCode: String,
    val encryptedCustomFieldsJson: String? = null
)

data class PaymentInfoRaw(
    val cardNumber: CharArray,
    val expiry: String,
    val cvv: CharArray,
    val cardHolder: String,
    val customFields: Map<String, String> = emptyMap() // 확장 커스텀 필드
) {
    override fun equals(other: Any?) = false
    override fun hashCode() = System.identityHashCode(this)
}

data class EncryptedPaymentInfo(
    val encryptedCardNumber: String,
    val encryptedExpiry: String,
    val encryptedCvv: String,
    val encryptedCardHolder: String,
    val maskedCardNumber: String,
    val encryptedCustomFieldsJson: String? = null
)

data class PaymentInfoDecrypted(
    val cardNumber: CharArray,
    val expiry: String,
    val cvv: CharArray,
    val cardHolder: String,
    val customFields: Map<String, String> = emptyMap()
) {
    fun clear() {
        cardNumber.fill('\u0000')
        cvv.fill('\u0000')
    }

    override fun equals(other: Any?) = false
    override fun hashCode() = System.identityHashCode(this)
}
