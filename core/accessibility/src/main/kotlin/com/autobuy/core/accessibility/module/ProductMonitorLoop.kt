package com.autobuy.core.accessibility.module

import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.accessibility.engine.ActionExecutor
import com.autobuy.core.accessibility.engine.NodeScanner
import com.autobuy.core.data.model.NodeSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mode B (Monitoring Polling) 전용 고속 상품 모니터링 루프.
 *
 * 쇼핑몰 검색/목록 화면에서 100~200ms 간격으로 고속 폴링을 수행하여
 * 설정한 키워드나 Selector와 매칭되는 상품이 노출되는 즉시 클릭하여 상세 페이지로 진입합니다.
 */
@Singleton
class ProductMonitorLoop @Inject constructor(
    private val nodeScanner: NodeScanner,
    private val actionExecutor: ActionExecutor
) {
    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling

    private val _targetDetected = MutableStateFlow(false)
    val targetDetected: StateFlow<Boolean> = _targetDetected

    /**
     * 고속 폴링 루프 실행.
     *
     * @param rootProvider 현재 최신 화면 루트 노드를 제공하는 람다
     * @param targetSelectors 탐색할 상품 노드 셀렉터 목록
     * @param keywords 탐색할 텍스트 키워드 목록
     * @param pollIntervalMs 폴링 간격 (기본 150ms)
     * @return true: 상품 발견 및 클릭 성공, false: 취소되거나 미발견
     */
    suspend fun startPolling(
        rootProvider: () -> AccessibilityNodeInfo?,
        targetSelectors: List<NodeSelector>,
        keywords: List<String> = emptyList(),
        pollIntervalMs: Long = 150L
    ): Boolean = withContext(Dispatchers.Default) {
        _isPolling.value = true
        _targetDetected.value = false

        try {
            while (isActive && _isPolling.value) {
                val root = rootProvider()
                if (root != null) {
                    // 1. Selector 기반 탐색
                    var matchedNode = nodeScanner.findNode(root, targetSelectors)

                    // 2. 키워드 기반 탐색 (Selector 미발견 시 Fallback)
                    if (matchedNode == null && keywords.isNotEmpty()) {
                        for (kw in keywords) {
                            val nodes = root.findAccessibilityNodeInfosByText(kw)
                            if (nodes.isNotEmpty()) {
                                matchedNode = nodes.first()
                                break
                            }
                        }
                    }

                    // 상품 발견 시 클릭 후 즉시 폴링 종료
                    if (matchedNode != null && (matchedNode.isClickable || matchedNode.isEnabled)) {
                        _targetDetected.value = true
                        _isPolling.value = false
                        val clicked = actionExecutor.click(matchedNode)
                        return@withContext clicked
                    }
                }
                delay(pollIntervalMs)
            }
            false
        } finally {
            _isPolling.value = false
        }
    }

    fun stopPolling() {
        _isPolling.value = false
    }
}
