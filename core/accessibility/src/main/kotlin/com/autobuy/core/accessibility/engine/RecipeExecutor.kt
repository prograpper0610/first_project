package com.autobuy.core.accessibility.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.accessibility.engine.AutoBuyConfig
import com.autobuy.core.data.model.InputType
import com.autobuy.core.data.model.RecipeStep
import com.autobuy.core.data.model.StepAction
import com.autobuy.core.security.SecureVault
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recipe 스텝을 실제로 실행하는 실행기.
 *
 * SecureVault에서 복호화된 데이터를 주입하여 폼을 자동으로 채웁니다.
 * 동적 커스텀 필드(InputType.CUSTOM_FIELD) 지원으로 미정 사이트/앱 완벽 대처.
 */
@Singleton
class RecipeExecutor @Inject constructor(
    private val nodeScanner: NodeScanner,
    private val actionExecutor: ActionExecutor,
    private val secureVault: SecureVault
) {
    private var decryptedDelivery: com.autobuy.core.security.DeliveryInfo? = null
    private var decryptedPayment: com.autobuy.core.security.PaymentInfoDecrypted? = null
    private var dynamicCustomFields: Map<String, String> = emptyMap()

    fun loadSecureData(
        deliveryEncrypted: com.autobuy.core.security.EncryptedDeliveryInfo?,
        paymentEncrypted: com.autobuy.core.security.EncryptedPaymentInfo?,
        extraCustomFields: Map<String, String> = emptyMap()
    ) {
        deliveryEncrypted?.let {
            val d = secureVault.decryptDeliveryInfo(it)
            decryptedDelivery = d
        }
        paymentEncrypted?.let {
            val p = secureVault.decryptPaymentInfo(it)
            decryptedPayment = p
        }
        dynamicCustomFields = extraCustomFields +
                (decryptedDelivery?.customFields ?: emptyMap()) +
                (decryptedPayment?.customFields ?: emptyMap())
    }

    /**
     * 단일 Recipe 스텝을 실행합니다.
     */
    suspend fun executeStep(
        root: AccessibilityNodeInfo,
        step: RecipeStep,
        config: AutoBuyConfig
    ): Boolean {
        var success = false
        var retryCount = 0

        while (!success && retryCount <= step.retryCount) {
            val node = nodeScanner.findNode(root, step.selectors)

            if (node == null) {
                if (step.scrollToView) {
                    root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    delay(300)
                }
                retryCount++
                delay(step.retryIntervalMs)
                continue
            }

            success = when (step.action) {
                StepAction.CLICK -> actionExecutor.click(node)
                StepAction.LONG_CLICK -> node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                StepAction.SET_TEXT -> {
                    val value = resolveInputValue(step) ?: ""
                    actionExecutor.setText(node, value)
                }
                StepAction.CLEAR_TEXT -> {
                    actionExecutor.setText(node, "")
                }
                StepAction.SCROLL_DOWN -> actionExecutor.scrollDown(node)
                StepAction.SCROLL_UP -> actionExecutor.scrollUp(node)
                StepAction.WAIT_FOR_ELEMENT -> true
                StepAction.WAIT_FOR_DISAPPEAR -> {
                    nodeScanner.waitForNodeDisappear(
                        rootProvider = { root },
                        selectors = step.selectors
                    )
                }
                StepAction.SELECT_OPTION -> {
                    actionExecutor.click(node)
                    true
                }
                StepAction.SWIPE -> {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    actionExecutor.swipe(
                        startX = bounds.left.toFloat(),
                        startY = bounds.centerY().toFloat(),
                        endX = bounds.right.toFloat(),
                        endY = bounds.centerY().toFloat()
                    )
                }
                StepAction.JS_INJECT -> {
                    step.staticValue?.let { script ->
                        actionExecutor.injectJavaScript(node, script)
                    } ?: false
                }
            }

            if (!success) {
                retryCount++
                delay(step.retryIntervalMs)
            }
        }

        if (success) delay(step.waitAfterMs)
        return success
    }

    /**
     * InputType 및 customKey에 따라 입력 값을 결정을 지원합니다.
     */
    private fun resolveInputValue(step: RecipeStep): String? {
        if (step.staticValue != null) return step.staticValue

        return when (step.inputType) {
            InputType.RECIPIENT_NAME -> decryptedDelivery?.recipientName
            InputType.PHONE_NUMBER -> decryptedDelivery?.phoneNumber
            InputType.ADDRESS -> decryptedDelivery?.address
            InputType.ADDRESS_DETAIL -> decryptedDelivery?.addressDetail
            InputType.POSTAL_CODE -> decryptedDelivery?.postalCode
            InputType.CARD_NUMBER -> decryptedPayment?.cardNumber?.let { String(it) }
            InputType.CARD_EXPIRY -> decryptedPayment?.expiry
            InputType.CARD_CVV -> decryptedPayment?.cvv?.let { String(it) }
            InputType.CARD_HOLDER -> decryptedPayment?.cardHolder
            InputType.QUANTITY -> "1"
            InputType.CAPTCHA_TEXT -> null
            InputType.CUSTOM_FIELD -> {
                step.customKey?.let { key -> dynamicCustomFields[key] }
            }
            null -> {
                // customKey가 존재하면 동적 커스텀 필드에서 조회 시도
                step.customKey?.let { key -> dynamicCustomFields[key] }
            }
        }
    }

    fun clearSensitiveData() {
        decryptedPayment?.clear()
        decryptedPayment = null
        decryptedDelivery = null
        dynamicCustomFields = emptyMap()
    }
}
