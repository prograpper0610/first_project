package com.autobuy.core.accessibility.module

import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.accessibility.engine.NodeScanner
import com.autobuy.core.data.model.NodeSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 3: 대기열 감지 및 처리 모듈.
 *
 * 대기열 판별 전략:
 * 1. Recipe의 queueDetectors 조건 매칭
 * 2. 공통 키워드 내장 DB 매칭
 * 3. WebView URL 변경 감지 (대기열 전용 URL 패턴)
 */
@Singleton
class QueueHandler @Inject constructor(
    private val nodeScanner: NodeScanner
) {
    // 공통 대기열 키워드 내장 DB
    private val commonQueueKeywords = listOf(
        "대기중", "대기 중", "대기번호", "잠시만 기다려", "잠시 기다려",
        "queue", "waiting", "대기열", "순서를 기다리", "곧 연결",
        "접속 대기", "사람이 많아", "트래픽이 많"
    )

    // 대기열 진입 URL 패턴
    private val queueUrlPatterns = listOf(
        "queue", "waiting", "wait", "hold", "throttle"
    )

    private val _queueStatus = MutableStateFlow(QueueStatus.NONE)
    val queueStatus: StateFlow<QueueStatus> = _queueStatus

    private var queueEnteredAt: Long = 0L

    /**
     * 현재 화면에서 대기열 여부를 감지합니다.
     * @return true: 대기열 발생, false: 대기열 없음
     */
    fun detectQueue(root: AccessibilityNodeInfo, recipeDetectors: List<NodeSelector>): Boolean {
        // 1. Recipe 정의 감지자 확인
        for (selector in recipeDetectors) {
            if (nodeScanner.findNodeBySelector(root, selector) != null) {
                onQueueDetected()
                return true
            }
        }

        // 2. 공통 키워드 텍스트 검색
        for (keyword in commonQueueKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNotEmpty()) {
                onQueueDetected()
                return true
            }
        }

        return false
    }

    /**
     * 대기열이 해소되었는지 확인합니다.
     * @return true: 대기열 해소, false: 아직 대기 중
     */
    fun isQueueCleared(root: AccessibilityNodeInfo, recipeDetectors: List<NodeSelector>): Boolean {
        val stillInQueue = detectQueue(root, recipeDetectors)
        if (!stillInQueue) {
            _queueStatus.value = QueueStatus.CLEARED
            return true
        }
        return false
    }

    /**
     * 현재 대기 번호를 화면에서 추출 시도합니다 (선택적).
     */
    fun extractQueueNumber(root: AccessibilityNodeInfo): Int? {
        val numberPattern = Regex("""대기\s*번호\s*[:：]?\s*(\d+)""")
        val allTexts = getAllTexts(root)
        for (text in allTexts) {
            val match = numberPattern.find(text)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        return null
    }

    /**
     * 대기 경과 시간 (초) 반환.
     */
    fun getQueueElapsedSeconds(): Long {
        return if (queueEnteredAt > 0) {
            (System.currentTimeMillis() - queueEnteredAt) / 1000
        } else 0L
    }

    private fun onQueueDetected() {
        if (_queueStatus.value == QueueStatus.NONE) {
            queueEnteredAt = System.currentTimeMillis()
            _queueStatus.value = QueueStatus.IN_QUEUE
        }
    }

    private fun getAllTexts(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        traverseForText(node, texts)
        return texts
    }

    private fun traverseForText(node: AccessibilityNodeInfo, result: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) result.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) result.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseForText(child, result)
        }
    }
}

enum class QueueStatus { NONE, IN_QUEUE, CLEARED }
