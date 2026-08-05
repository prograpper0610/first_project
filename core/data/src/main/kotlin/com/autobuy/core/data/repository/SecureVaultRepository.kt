package com.autobuy.core.data.repository

import com.autobuy.core.data.db.dao.SecureRecordDao
import com.autobuy.core.data.db.entity.SecureRecordEntity
import com.autobuy.core.security.DeliveryInfo
import com.autobuy.core.security.EncryptedDeliveryInfo
import com.autobuy.core.security.EncryptedPaymentInfo
import com.autobuy.core.security.PaymentInfoDecrypted
import com.autobuy.core.security.PaymentInfoRaw
import com.autobuy.core.security.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 암호화 데이터 리포지토리.
 *
 * PII 및 동적 커스텀 필드를 SecureVault로 AES-256 암호화하여 Room DB에 영속화합니다.
 */
@Singleton
class SecureVaultRepository @Inject constructor(
    private val secureRecordDao: SecureRecordDao,
    private val secureVault: SecureVault
) {
    /**
     * 배송지 목록 관찰.
     */
    fun observeDeliveryRecords(): Flow<List<SecureRecordSummary>> {
        return secureRecordDao.observeByType("DELIVERY").map { entities ->
            entities.map { SecureRecordSummary(it.id, it.displayName, it.isDefault) }
        }
    }

    /**
     * 카드 목록 관찰.
     */
    fun observePaymentRecords(): Flow<List<SecureRecordSummary>> {
        return secureRecordDao.observeByType("PAYMENT").map { entities ->
            entities.map { SecureRecordSummary(it.id, it.displayName, it.isDefault) }
        }
    }

    /**
     * 배송지 정보 암호화 저장.
     */
    suspend fun saveDeliveryInfo(
        displayName: String,
        info: DeliveryInfo,
        isDefault: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val enc = secureVault.encryptDeliveryInfo(info)

        val entity = SecureRecordEntity(
            id = id,
            recordType = "DELIVERY",
            displayName = displayName,
            encRecipientName = enc.encryptedName,
            encPhone = enc.encryptedPhone,
            encAddress = enc.encryptedAddress,
            encAddressDetail = enc.encryptedAddressDetail,
            encPostalCode = enc.encryptedPostalCode,
            encCustomFieldsJson = enc.encryptedCustomFieldsJson,
            isDefault = isDefault,
            createdAt = Date()
        )

        if (isDefault) secureRecordDao.clearDefaults("DELIVERY")
        secureRecordDao.upsert(entity)
        id
    }

    /**
     * 카드 정보 암호화 저장.
     */
    suspend fun savePaymentInfo(
        displayName: String,
        info: PaymentInfoRaw,
        isDefault: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val enc = secureVault.encryptPaymentInfo(info)

        val entity = SecureRecordEntity(
            id = id,
            recordType = "PAYMENT",
            displayName = displayName,
            encCardNumber = enc.encryptedCardNumber,
            encExpiry = enc.encryptedExpiry,
            encCvv = enc.encryptedCvv,
            encCardHolder = enc.encryptedCardHolder,
            maskedCardNumber = enc.maskedCardNumber,
            encCustomFieldsJson = enc.encryptedCustomFieldsJson,
            isDefault = isDefault,
            createdAt = Date()
        )

        if (isDefault) secureRecordDao.clearDefaults("PAYMENT")
        secureRecordDao.upsert(entity)
        id
    }

    /**
     * 암호화된 배송지 데이터 로드.
     */
    suspend fun getDeliveryInfo(id: String): DeliveryInfo? = withContext(Dispatchers.IO) {
        val entity = secureRecordDao.getById(id) ?: return@withContext null
        val enc = EncryptedDeliveryInfo(
            encryptedName = entity.encRecipientName ?: return@withContext null,
            encryptedPhone = entity.encPhone ?: "",
            encryptedAddress = entity.encAddress ?: "",
            encryptedAddressDetail = entity.encAddressDetail ?: "",
            encryptedPostalCode = entity.encPostalCode ?: "",
            encryptedCustomFieldsJson = entity.encCustomFieldsJson
        )
        secureVault.decryptDeliveryInfo(enc)
    }

    /**
     * 암호화된 카드 데이터 로드.
     */
    suspend fun getPaymentInfo(id: String): PaymentInfoDecrypted? = withContext(Dispatchers.IO) {
        val entity = secureRecordDao.getById(id) ?: return@withContext null
        val enc = EncryptedPaymentInfo(
            encryptedCardNumber = entity.encCardNumber ?: return@withContext null,
            encryptedExpiry = entity.encExpiry ?: "",
            encryptedCvv = entity.encCvv ?: "",
            encryptedCardHolder = entity.encCardHolder ?: "",
            maskedCardNumber = entity.maskedCardNumber ?: "****",
            encryptedCustomFieldsJson = entity.encCustomFieldsJson
        )
        secureVault.decryptPaymentInfo(enc)
    }

    suspend fun deleteRecord(id: String) = withContext(Dispatchers.IO) {
        val entity = secureRecordDao.getById(id)
        entity?.let { secureRecordDao.delete(it) }
    }
}

data class SecureRecordSummary(
    val id: String,
    val displayName: String,
    val isDefault: Boolean
)
