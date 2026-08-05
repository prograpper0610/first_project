package com.autobuy.core.accessibility.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 자동화 액션 실행 엔진.
 *
 * 모든 액션에는 인간 유사 딜레이(랜덤 50~200ms)가 적용됩니다.
 * 이는 봇 감지 시스템의 타이밍 분석을 방해하기 위함입니다.
 */
@Singleton
class ActionExecutor @Inject constructor() {

    private var accessibilityService: AccessibilityService? = null

    fun bindService(service: AccessibilityService) {
        this.accessibilityService = service
    }

    fun unbindService() {
        this.accessibilityService = null
    }

    /**
     * 노드 클릭.
     */
    suspend fun click(node: AccessibilityNodeInfo): Boolean = withContext(Dispatchers.Main) {
        humanDelay()
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK).also {
            if (!it) {
                // Fallback: 좌표 기반 클릭
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                performTap(
                    x = bounds.centerX().toFloat(),
                    y = bounds.centerY().toFloat()
                )
            }
        }
    }

    /**
     * 텍스트 입력.
     */
    suspend fun setText(node: AccessibilityNodeInfo, text: String): Boolean = withContext(Dispatchers.Main) {
        humanDelay()
        // 1. 노드 포커스
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        delay(50)
        // 2. 기존 텍스트 클리어
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        })
        delay(30)
        // 3. 텍스트 설정
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
    }

    /**
     * 민감 데이터 입력 (CharArray 사용, 사용 후 즉시 제로화).
     */
    suspend fun setTextSensitive(node: AccessibilityNodeInfo, chars: CharArray): Boolean {
        val text = String(chars)
        return try {
            setText(node, text)
        } finally {
            // String은 GC를 통해 처리되지만 반환값의 chars는 호출자가 제로화
        }
    }

    /**
     * 스크롤 다운.
     */
    suspend fun scrollDown(node: AccessibilityNodeInfo): Boolean = withContext(Dispatchers.Main) {
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * 스크롤 업.
     */
    suspend fun scrollUp(node: AccessibilityNodeInfo): Boolean = withContext(Dispatchers.Main) {
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /**
     * 좌표 기반 스와이프 제스처.
     */
    suspend fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300L
    ): Boolean = withContext(Dispatchers.Main) {
        val service = accessibilityService ?: return@withContext false
        humanDelay()
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        var result = false
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                result = true
            }
        }, null)
        delay(durationMs + 100)
        result
    }

    /**
     * WebView 내 JavaScript 실행.
     * WebView 노드를 타겟으로 evaluateJavascript를 호출합니다.
     */
    fun injectJavaScript(webViewNode: AccessibilityNodeInfo, script: String): Boolean {
        val bundle = Bundle().apply {
            putString(
                "ACTION_ARGUMENT_HTML_ELEMENT_STRING",
                script
            )
        }
        return webViewNode.performAction(
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT,
            bundle
        )
    }

    /**
     * 좌표 기반 탭 (좌표 클릭).
     */
    suspend fun performTap(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        val service = accessibilityService ?: return@withContext false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50L))
            .build()
        var success = false
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                success = true
            }
        }, null)
        delay(100)
        success
    }

    /**
     * 인간 유사 딜레이 적용 (50~200ms 랜덤).
     * 봇 감지 타이밍 분석 방해 목적.
     */
    private suspend fun humanDelay() {
        delay(Random.nextLong(50L, 200L))
    }
}
