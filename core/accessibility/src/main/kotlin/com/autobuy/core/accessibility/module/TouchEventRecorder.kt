package com.autobuy.core.accessibility.module

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.data.model.InputType
import com.autobuy.core.data.model.NodeSelector
import com.autobuy.core.data.model.RecipeStep
import com.autobuy.core.data.model.SelectorType
import com.autobuy.core.data.model.ShopRecipe
import com.autobuy.core.data.model.StepAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 2: Smart Recorder (1회 학습 모드) 코어 엔진.
 *
 * 사용자가 신규 쇼핑몰/사이트에서 구매 과정을 1회 시뮬레이션할 때
 * 클릭 및 텍스트 입력 이벤트를 실시간 포착하여 UI 노드 정보(resourceId, text, bounds 등)를 수집하고
 * 자동으로 실행 가능한 ShopRecipe JSON으로 전환합니다.
 */
@Singleton
class TouchEventRecorder @Inject constructor() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordedSteps = MutableStateFlow<List<RecipeStep>>(emptyList())
    val recordedSteps: StateFlow<List<RecipeStep>> = _recordedSteps

    private var shopId: String = "recorded_shop"
    private var shopName: String = "신규 쇼핑몰"
    private var targetPackage: String = "browser"

    fun startRecording(shopId: String, shopName: String, packageName: String) {
        this.shopId = shopId
        this.shopName = shopName
        this.targetPackage = packageName
        _recordedSteps.value = emptyList()
        _isRecording.value = true
    }

    fun stopRecording(): ShopRecipe {
        _isRecording.value = false
        return generateRecipe()
    }

    /**
     * AccessibilityService로부터 수신된 터치/입력 이벤트를 가공하여 스텝으로 기록합니다.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        if (!_isRecording.value) return

        val source = event.source ?: return
        val eventType = event.eventType

        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> recordClickStep(source)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> recordTextStep(source)
        }
    }

    private fun recordClickStep(node: AccessibilityNodeInfo) {
        val selectors = generateSelectors(node)
        if (selectors.isEmpty()) return

        val stepId = "step_click_${_recordedSteps.value.size + 1}"
        val description = "${node.text ?: node.contentDescription ?: node.viewIdResourceName ?: "노드"} 클릭"

        val step = RecipeStep(
            stepId = stepId,
            description = description,
            action = StepAction.CLICK,
            selectors = selectors,
            waitAfterMs = 500,
            retryCount = 3
        )

        _recordedSteps.value = _recordedSteps.value + step
    }

    private fun recordTextStep(node: AccessibilityNodeInfo) {
        val selectors = generateSelectors(node)
        if (selectors.isEmpty()) return

        val text = node.text?.toString() ?: ""
        val stepId = "step_input_${_recordedSteps.value.size + 1}"

        // 입력 텍스트 유형 추정
        val inputType = inferInputType(text, node)

        val step = RecipeStep(
            stepId = stepId,
            description = "텍스트 입력 ($text)",
            action = StepAction.SET_TEXT,
            selectors = selectors,
            inputType = inputType,
            staticValue = if (inputType == null) text else null,
            waitAfterMs = 300
        )

        _recordedSteps.value = _recordedSteps.value + step
    }

    /**
     * 노드로부터 다중 Selector 생성 (Fallback 순서대로)
     */
    private fun generateSelectors(node: AccessibilityNodeInfo): List<NodeSelector> {
        val selectors = mutableListOf<NodeSelector>()

        // 1. Resource ID
        node.viewIdResourceName?.let { resId ->
            selectors.add(NodeSelector(SelectorType.RESOURCE_ID, resId))
        }

        // 2. Exact Text
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { text ->
            selectors.add(NodeSelector(SelectorType.TEXT, text))
        }

        // 3. Content Description
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { desc ->
            selectors.add(NodeSelector(SelectorType.CONTENT_DESC, desc))
        }

        // 4. Class Name Fallback
        node.className?.toString()?.let { cls ->
            selectors.add(NodeSelector(SelectorType.CLASS_NAME, cls))
        }

        return selectors
    }

    private fun inferInputType(text: String, node: AccessibilityNodeInfo): InputType? {
        val resId = node.viewIdResourceName?.lowercase() ?: ""
        return when {
            resId.contains("name") || text.contains("수령인") -> InputType.RECIPIENT_NAME
            resId.contains("phone") || resId.contains("tel") -> InputType.PHONE_NUMBER
            resId.contains("address") || text.contains("주소") -> InputType.ADDRESS
            resId.contains("card") || text.contains("카드") -> InputType.CARD_NUMBER
            else -> null
        }
    }

    /**
     * 기록된 스텝들을 기반으로 완벽한 ShopRecipe 객체를 생성합니다.
     */
    private fun generateRecipe(): ShopRecipe {
        val steps = _recordedSteps.value.ifEmpty {
            listOf(
                RecipeStep(
                    stepId = "default_step",
                    description = "기본 클릭 스텝",
                    action = StepAction.CLICK,
                    selectors = listOf(NodeSelector(SelectorType.TEXT_CONTAINS, "구매"))
                )
            )
        }

        val lastStep = steps.lastOrNull()
        val finalTrigger = lastStep?.selectors?.firstOrNull()
            ?: NodeSelector(SelectorType.TEXT_CONTAINS, "결제하기")

        return ShopRecipe(
            shopId = shopId,
            shopName = shopName,
            packageName = targetPackage,
            version = "1.0",
            steps = steps,
            paymentFinalTrigger = finalTrigger
        )
    }
}
