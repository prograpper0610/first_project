package com.autobuy.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 쇼핑몰 자동화 프로필 (Recipe) 엔티티.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,                        // UUID
    @ColumnInfo(name = "shop_id") val shopId: String,  // 쇼핑몰 식별자 (e.g., "coupang")
    @ColumnInfo(name = "shop_name") val shopName: String,
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "recipe_json") val recipeJson: String,  // Recipe 전체 JSON
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean,   // 내장 프로필 여부
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Date = Date(),
    @ColumnInfo(name = "updated_at") val updatedAt: Date = Date()
)

/**
 * 암호화된 민감 데이터 레코드 엔티티.
 * 모든 필드는 AesCryptoEngine으로 암호화된 Base64 문자열로 저장됩니다.
 *
 * 동적 확장 지원:
 * - enc_custom_fields_json: 추후 추가될 수 있는 임의의 필드들을 JSON 형태(AES-256 암호화)로 확장 저장.
 */
@Entity(tableName = "secure_records")
data class SecureRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "record_type") val recordType: String,  // "DELIVERY" | "PAYMENT" | "CUSTOM"
    @ColumnInfo(name = "display_name") val displayName: String, // 사용자 표시명 (마스킹됨)

    // 배송지 정보 (암호화)
    @ColumnInfo(name = "enc_recipient_name") val encRecipientName: String? = null,
    @ColumnInfo(name = "enc_phone") val encPhone: String? = null,
    @ColumnInfo(name = "enc_address") val encAddress: String? = null,
    @ColumnInfo(name = "enc_address_detail") val encAddressDetail: String? = null,
    @ColumnInfo(name = "enc_postal_code") val encPostalCode: String? = null,

    // 카드 정보 (암호화)
    @ColumnInfo(name = "enc_card_number") val encCardNumber: String? = null,
    @ColumnInfo(name = "enc_expiry") val encExpiry: String? = null,
    @ColumnInfo(name = "enc_cvv") val encCvv: String? = null,
    @ColumnInfo(name = "enc_card_holder") val encCardHolder: String? = null,
    @ColumnInfo(name = "masked_card_number") val maskedCardNumber: String? = null,

    // 동적 확장 필드 (AES-256 암호화된 JSON Map<String, String>)
    @ColumnInfo(name = "enc_custom_fields_json") val encCustomFieldsJson: String? = null,

    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Date = Date()
)

/**
 * 자동구매 실행 로그 엔티티.
 */
@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "product_url") val productUrl: String,
    @ColumnInfo(name = "product_name") val productName: String? = null,
    @ColumnInfo(name = "status") val status: String,        // "SUCCESS" | "FAILED" | "HANDOVER" | "CANCELLED"
    @ColumnInfo(name = "fail_reason") val failReason: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Date,
    @ColumnInfo(name = "ended_at") val endedAt: Date? = null,
    @ColumnInfo(name = "step_log") val stepLog: String = "[]",  // JSON 배열 형태 단계별 로그
    @ColumnInfo(name = "queue_wait_ms") val queueWaitMs: Long = 0,
    @ColumnInfo(name = "total_elapsed_ms") val totalElapsedMs: Long = 0
)

/**
 * 앱 설정 엔티티 (Key-Value 저장소).
 */
@Entity(tableName = "configurations")
data class ConfigurationEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Date = Date()
)
