package com.autobuy.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.accessibility.engine.AutoBuyOrchestrator
import com.autobuy.core.accessibility.engine.NodeScanner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AutoBuy 핵심 접근성 서비스.
 *
 * - 모든 앱의 화면 이벤트를 수신하여 현재 상태를 판별합니다.
 * - AutoBuyOrchestrator와 통신하여 자동화 액션을 실행합니다.
 * - Companion Object의 SharedFlow를 통해 현재 루트 노드를 노출합니다.
 */
@AndroidEntryPoint
class AutoBuyAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var orchestrator: AutoBuyOrchestrator

    @Inject
    lateinit var nodeScanner: NodeScanner

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        // 현재 화면 루트 노드 스트림 (전역 접근)
        private val _rootNodeFlow = MutableSharedFlow<AccessibilityNodeInfo?>(replay = 1)
        val rootNodeFlow: SharedFlow<AccessibilityNodeInfo?> = _rootNodeFlow

        // 서비스 실행 상태
        @Volatile
        var isRunning = false
            private set

        // 현재 포어그라운드 패키지명
        @Volatile
        var currentPackage: String = ""
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        serviceInfo = AccessibilityServiceInfo().apply {
            // 모든 패키지의 이벤트 감지
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100L
        }

        serviceScope.launch {
            orchestrator.initialize(this@AutoBuyAccessibilityService)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val rootNode = rootInActiveWindow ?: return
        currentPackage = event.packageName?.toString() ?: ""

        serviceScope.launch {
            _rootNodeFlow.emit(rootNode)
            orchestrator.onScreenEvent(
                event = event,
                rootNode = rootNode,
                packageName = currentPackage
            )
        }
    }

    override fun onInterrupt() {
        // 서비스 일시 중단 — 대부분의 경우 재연결됨
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        serviceScope.cancel()
        orchestrator.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
