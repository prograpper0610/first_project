package com.autobuy.core.accessibility.engine

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.accessibility.module.AntiBotInterceptor
import com.autobuy.core.accessibility.module.HandoverLayer
import com.autobuy.core.accessibility.module.ProductMonitorLoop
import com.autobuy.core.accessibility.module.QueueHandler
import com.autobuy.core.accessibility.module.TouchEventRecorder
import com.autobuy.core.data.db.dao.ExecutionLogDao
import com.autobuy.core.data.db.entity.ExecutionLogEntity
import com.autobuy.core.data.model.ShopRecipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 자동구매 프로세스 전체를 조율하는 오케스트레이터 (State Machine).
 *
 * Mode A / Mode B 분기, 대기열, OCR Anti-Bot, Handover 및 실행 로그 저장 연동.
 */
@Singleton
class AutoBuyOrchestrator @Inject constructor(
    private val nodeScanner: NodeScanner,
    private val actionExecutor: ActionExecutor,
    private val recipeExecutor: RecipeExecutor,
    private val queueHandler: QueueHandler,
    private val antiBotInterceptor: AntiBotInterceptor,
    private val handoverLayer: HandoverLayer,
    private val productMonitorLoop: ProductMonitorLoop,
    private val touchEventRecorder: TouchEventRecorder,
    private val executionLogDao: ExecutionLogDao
) {
    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AutoBuyState>(AutoBuyState.Idle)
    val state: StateFlow<AutoBuyState> = _state

    private var currentRecipe: ShopRecipe? = null
    private var currentConfig: AutoBuyConfig? = null
    private var accessibilityService: AccessibilityService? = null
    private var startTimeMs: Long = 0L

    fun initialize(service: AccessibilityService) {
        accessibilityService = service
        actionExecutor.bindService(service)
        antiBotInterceptor.bindService(service)
    }

    fun onServiceDisconnected() {
        accessibilityService = null
        actionExecutor.unbindService()
        orchestratorScope.cancel()
    }

    fun startAutoBuy(config: AutoBuyConfig) {
        orchestratorScope.launch {
            startTimeMs = System.currentTimeMillis()
            currentConfig = config
            currentRecipe = config.recipe
            _state.value = AutoBuyState.Waiting(config)
        }
    }

    fun stopAutoBuy() {
        orchestratorScope.launch {
            saveLog("CANCELLED", "사용자에 의해 중단됨")
            _state.value = AutoBuyState.Idle
            currentRecipe = null
            productMonitorLoop.stopPolling()
        }
    }

    fun resume() {
        orchestratorScope.launch {
            val current = _state.value
            if (current is AutoBuyState.Paused) {
                _state.value = current.resumeState
            }
        }
    }

    suspend fun onScreenEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo,
        packageName: String
    ) {
        // Smart Recorder 모드 작동 중일 시 이벤트 수집
        if (touchEventRecorder.isRecording.value) {
            touchEventRecorder.onAccessibilityEvent(event, rootNode)
        }

        val currentState = _state.value
        val recipe = currentRecipe ?: return

        if (packageName != recipe.packageName && recipe.packageName != "browser") return

        when (currentState) {
            is AutoBuyState.Waiting -> handleWaitingState(currentState, rootNode, recipe)
            is AutoBuyState.ModeAPending -> handleModeA(currentState, rootNode, recipe)
            is AutoBuyState.ModeBPolling -> handleModeB(currentState, rootNode, recipe)
            is AutoBuyState.PurchaseStarted -> handlePurchaseStarted(currentState, rootNode, recipe)
            is AutoBuyState.QueueHandling -> handleQueue(currentState, rootNode, recipe)
            is AutoBuyState.FormFilling -> handleFormFilling(currentState, rootNode, recipe)
            is AutoBuyState.AntiBotDetected -> handleAntiBot(currentState, rootNode, recipe)
            is AutoBuyState.Handover -> { /* 사용자 처리 중 */ }
            else -> { /* IDLE, COMPLETE, ERROR */ }
        }
    }

    private suspend fun handleWaitingState(
        state: AutoBuyState.Waiting,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        val config = state.config
        _state.value = if (config.mode == PurchaseMode.MODE_A) {
            AutoBuyState.ModeAPending(config)
        } else {
            AutoBuyState.ModeBPolling(config)
        }
    }

    private suspend fun handleModeA(
        state: AutoBuyState.ModeAPending,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        val buyButtonSelectors = recipe.steps.firstOrNull { it.stepId == "click_buy" }?.selectors
            ?: return

        val buyButton = nodeScanner.findNode(root, buyButtonSelectors)
        if (buyButton != null && buyButton.isEnabled && buyButton.isClickable) {
            actionExecutor.click(buyButton)
            _state.value = AutoBuyState.PurchaseStarted(state.config, stepIndex = 1)
        }
    }

    private suspend fun handleModeB(
        state: AutoBuyState.ModeBPolling,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        // Mode B: 고속 모니터링 루프 시작
        val targetSelectors = recipe.steps.firstOrNull { it.stepId == "detect_product" }?.selectors
            ?: recipe.steps.firstOrNull()?.selectors ?: return

        val detected = productMonitorLoop.startPolling(
            rootProvider = { root },
            targetSelectors = targetSelectors,
            pollIntervalMs = 150L
        )

        if (detected) {
            _state.value = AutoBuyState.PurchaseStarted(state.config, stepIndex = 0)
        }
    }

    private suspend fun handlePurchaseStarted(
        state: AutoBuyState.PurchaseStarted,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        val isQueue = queueHandler.detectQueue(root, recipe.queueDetectors)
        if (isQueue) {
            _state.value = AutoBuyState.QueueHandling(state.config)
            return
        }
        _state.value = AutoBuyState.FormFilling(state.config, stepIndex = state.stepIndex)
    }

    private suspend fun handleQueue(
        state: AutoBuyState.QueueHandling,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        val queueCleared = queueHandler.isQueueCleared(root, recipe.queueDetectors)
        if (queueCleared) {
            _state.value = AutoBuyState.FormFilling(state.config, stepIndex = 0)
        }
    }

    private suspend fun handleFormFilling(
        state: AutoBuyState.FormFilling,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        val isCaptcha = antiBotInterceptor.detectAntiBot(root, recipe.antiBotKeywords)
        if (isCaptcha) {
            _state.value = AutoBuyState.AntiBotDetected(state)
            return
        }

        val finalTriggerNode = nodeScanner.findNode(root, listOf(recipe.paymentFinalTrigger))
        if (finalTriggerNode != null) {
            _state.value = AutoBuyState.Handover(state.config)
            handoverLayer.execute()
            saveLog("HANDOVER", "최종 결제 화면 이관 완료")
            return
        }

        val step = recipe.steps.getOrNull(state.stepIndex) ?: return
        val success = recipeExecutor.executeStep(root, step, state.config)

        if (success || step.isOptional) {
            val nextIndex = state.stepIndex + 1
            if (nextIndex >= recipe.steps.size) {
                _state.value = AutoBuyState.Complete(state.config)
                saveLog("SUCCESS", "모든 레시피 스텝 완료")
            } else {
                _state.value = state.copy(stepIndex = nextIndex)
            }
        } else {
            val errorState = AutoBuyState.Error("Step '${step.stepId}' 실행 실패", state)
            _state.value = errorState
            saveLog("FAILED", errorState.reason)
        }
    }

    private suspend fun handleAntiBot(
        state: AutoBuyState.AntiBotDetected,
        root: AccessibilityNodeInfo,
        recipe: ShopRecipe
    ) {
        val resolved = antiBotInterceptor.attemptAutoSolve(root)
        if (resolved) {
            _state.value = state.pausedState
        }
    }

    private suspend fun saveLog(status: String, failReason: String? = null) {
        val config = currentConfig ?: return
        val elapsed = System.currentTimeMillis() - startTimeMs
        val logEntity = ExecutionLogEntity(
            id = UUID.randomUUID().toString(),
            shopId = config.recipe.shopId,
            productUrl = config.targetUrl,
            status = status,
            failReason = failReason,
            startedAt = Date(startTimeMs),
            endedAt = Date(),
            queueWaitMs = queueHandler.getQueueElapsedSeconds() * 1000,
            totalElapsedMs = elapsed
        )
        executionLogDao.upsert(logEntity)
    }
}

sealed class AutoBuyState {
    object Idle : AutoBuyState()
    data class Waiting(val config: AutoBuyConfig) : AutoBuyState()
    data class ModeAPending(val config: AutoBuyConfig) : AutoBuyState()
    data class ModeBPolling(val config: AutoBuyConfig) : AutoBuyState()
    data class PurchaseStarted(val config: AutoBuyConfig, val stepIndex: Int) : AutoBuyState()
    data class QueueHandling(val config: AutoBuyConfig) : AutoBuyState()
    data class FormFilling(val config: AutoBuyConfig, val stepIndex: Int) : AutoBuyState()
    data class AntiBotDetected(val pausedState: FormFilling) : AutoBuyState()
    data class Paused(val resumeState: AutoBuyState) : AutoBuyState()
    data class Handover(val config: AutoBuyConfig) : AutoBuyState()
    data class Complete(val config: AutoBuyConfig) : AutoBuyState()
    data class Error(val reason: String, val previousState: AutoBuyState) : AutoBuyState()
}

data class AutoBuyConfig(
    val sessionId: String,
    val recipe: ShopRecipe,
    val mode: PurchaseMode,
    val targetUrl: String,
    val openTimeEpochMs: Long,
    val deliveryRecordId: String,
    val paymentRecordId: String
)

enum class PurchaseMode { MODE_A, MODE_B }
