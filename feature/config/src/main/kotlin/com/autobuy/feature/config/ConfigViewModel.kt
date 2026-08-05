package com.autobuy.feature.config

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobuy.core.accessibility.AutoBuyForegroundService
import com.autobuy.core.accessibility.engine.AutoBuyConfig
import com.autobuy.core.accessibility.engine.AutoBuyOrchestrator
import com.autobuy.core.accessibility.engine.PurchaseMode
import com.autobuy.core.accessibility.engine.RecipeExecutor
import com.autobuy.core.data.repository.ProfileRepository
import com.autobuy.core.data.repository.ProfileUiModel
import com.autobuy.core.data.repository.SecureRecordSummary
import com.autobuy.core.data.repository.SecureVaultRepository
import com.autobuy.core.security.DeliveryInfo
import com.autobuy.core.security.PaymentInfoRaw
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val secureVaultRepository: SecureVaultRepository,
    private val orchestrator: AutoBuyOrchestrator,
    private val recipeExecutor: RecipeExecutor
) : ViewModel() {

    // 입력 상태
    val targetUrl = MutableStateFlow("")
    val openTimeEpochMs = MutableStateFlow(System.currentTimeMillis() + 300_000L) // 기본 5분 뒤
    val selectedMode = MutableStateFlow(PurchaseMode.MODE_A)

    // PII 배송지
    val recipientName = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val address = MutableStateFlow("")
    val addressDetail = MutableStateFlow("")
    val postalCode = MutableStateFlow("")

    // PII 카드
    val cardNumber = MutableStateFlow("")
    val expiry = MutableStateFlow("")
    val cvv = MutableStateFlow("")
    val cardHolder = MutableStateFlow("")

    // 동적 커스텀 필드 (미정 사이트/앱 완벽 대처)
    val customFields = mutableStateListOf<CustomFieldEntry>()

    // 프로필 관찰
    val profiles: StateFlow<List<ProfileUiModel>> = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProfileId = MutableStateFlow<String?>(null)

    // 단발성 이벤트 (대시보드 이동)
    private val _navigateToDashboard = MutableSharedFlow<Unit>()
    val navigateToDashboard: SharedFlow<Unit> = _navigateToDashboard

    fun addCustomField() {
        customFields.add(CustomFieldEntry(key = "", value = ""))
    }

    fun removeCustomField(index: Int) {
        if (index in customFields.indices) {
            customFields.removeAt(index)
        }
    }

    fun updateCustomField(index: Int, key: String, value: String) {
        if (index in customFields.indices) {
            customFields[index] = CustomFieldEntry(key, value)
        }
    }

    /**
     * 설정을 암호화하여 저장하고 자동구매를 실행합니다.
     */
    fun startAutoBuy(context: Context) {
        viewModelScope.launch {
            // 커스텀 필드 Map 생성
            val customMap = customFields
                .filter { it.key.isNotBlank() }
                .associate { it.key.trim() to it.value.trim() }

            // 1. 배송지 정보 암호화 저장
            val deliveryInfo = DeliveryInfo(
                recipientName = recipientName.value,
                phoneNumber = phoneNumber.value,
                address = address.value,
                addressDetail = addressDetail.value,
                postalCode = postalCode.value,
                customFields = customMap
            )
            val deliveryRecordId = secureVaultRepository.saveDeliveryInfo(
                displayName = "기본 배송지",
                info = deliveryInfo,
                isDefault = true
            )

            // 2. 카드 정보 암호화 저장 (CharArray 변환 사용)
            val paymentInfo = PaymentInfoRaw(
                cardNumber = cardNumber.value.toCharArray(),
                expiry = expiry.value,
                cvv = cvv.value.toCharArray(),
                cardHolder = cardHolder.value,
                customFields = customMap
            )
            val paymentRecordId = secureVaultRepository.savePaymentInfo(
                displayName = "기본 카드",
                info = paymentInfo,
                isDefault = true
            )

            // 3. 프로필 선택 (선택 안 되었을 시 첫 번째 또는 쿠팡)
            val profileList = profiles.value
            val targetProfile = profileList.find { it.id == selectedProfileId.value }
                ?: profileList.firstOrNull()

            if (targetProfile == null) return@launch

            // 4. RecipeExecutor에 복호화 정보 로드
            val decDelivery = secureVaultRepository.getDeliveryInfo(deliveryRecordId)
            val decPayment = secureVaultRepository.getPaymentInfo(paymentRecordId)

            recipeExecutor.loadSecureData(
                deliveryEncrypted = secureVaultRepository.getDeliveryInfo(deliveryRecordId)?.let {
                    com.autobuy.core.security.EncryptedDeliveryInfo(
                        encryptedName = "", encryptedPhone = "", encryptedAddress = "", encryptedAddressDetail = "", encryptedPostalCode = ""
                    )
                },
                paymentEncrypted = null,
                extraCustomFields = customMap
            )

            // 5. Config 구성
            val config = AutoBuyConfig(
                sessionId = UUID.randomUUID().toString(),
                recipe = targetProfile.recipe,
                mode = selectedMode.value,
                targetUrl = targetUrl.value,
                openTimeEpochMs = openTimeEpochMs.value,
                deliveryRecordId = deliveryRecordId,
                paymentRecordId = paymentRecordId
            )

            // 6. 포그라운드 서비스 및 오케스트레이터 시작
            val serviceIntent = AutoBuyForegroundService.startIntent(context, config)
            context.startForegroundService(serviceIntent)

            orchestrator.startAutoBuy(config)

            _navigateToDashboard.emit(Unit)
        }
    }
}

data class CustomFieldEntry(
    val key: String,
    val value: String
)
