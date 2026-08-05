package com.autobuy.core.accessibility.module

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.autobuy.core.accessibility.engine.NodeScanner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Module 4: Dynamic Anti-Bot Interceptor.
 *
 * CAPTCHA 감지 → ML Kit OCR 자동 해석 → 1.5초 내 실패 시 사용자 Fallback
 *
 * 지원하는 CAPTCHA 유형:
 * - 텍스트 CAPTCHA: OCR로 자동 인식 및 입력
 * - 슬라이더 CAPTCHA: 스와이프 제스처 자동화
 * - 이미지 선택 CAPTCHA: 사용자 Fallback (자동 불가)
 */
@Singleton
class AntiBotInterceptor @Inject constructor(
    private val nodeScanner: NodeScanner,
    private val userNotifier: UserNotifier
) {
    companion object {
        private const val AUTO_SOLVE_TIMEOUT_MS = 1500L  // 1.5초 타임아웃
    }

    private var accessibilityService: AccessibilityService? = null

    // ML Kit 텍스트 인식기 (한국어 + 영문)
    private val koreanRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun bindService(service: AccessibilityService) {
        accessibilityService = service
    }

    /**
     * 현재 화면에서 안티봇/CAPTCHA 요소를 감지합니다.
     */
    fun detectAntiBot(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        for (keyword in keywords) {
            if (root.findAccessibilityNodeInfosByText(keyword).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * CAPTCHA 자동 해결을 시도합니다.
     * 1.5초 내 실패 시 사용자에게 알림.
     *
     * @return true: 자동 해결 성공, false: 실패 (사용자 개입 필요)
     */
    suspend fun attemptAutoSolve(root: AccessibilityNodeInfo): Boolean = withContext(Dispatchers.Default) {
        val solved = withTimeoutOrNull(AUTO_SOLVE_TIMEOUT_MS) {
            // 1단계: CAPTCHA 유형 분류
            val captchaType = classifyCaptchaType(root)

            when (captchaType) {
                CaptchaType.TEXT -> solveTextCaptcha(root)
                CaptchaType.SLIDER -> solveSliderCaptcha(root)
                CaptchaType.IMAGE_SELECT -> false  // 자동 해결 불가
                CaptchaType.UNKNOWN -> false
            }
        }

        if (solved != true) {
            // 자동 해결 실패 → 사용자에게 즉각 알림
            userNotifier.notifyCaptchaFallback()
        }

        solved == true
    }

    /**
     * CAPTCHA 유형을 분류합니다.
     */
    private fun classifyCaptchaType(root: AccessibilityNodeInfo): CaptchaType {
        val allTexts = getAllTexts(root)
        val combined = allTexts.joinToString(" ")

        return when {
            combined.contains(Regex("슬라이드|밀어서|slide", RegexOption.IGNORE_CASE)) -> CaptchaType.SLIDER
            combined.contains(Regex("선택하세요|클릭하세요|select.*image", RegexOption.IGNORE_CASE)) -> CaptchaType.IMAGE_SELECT
            root.findAccessibilityNodeInfosByText("보안문자").isNotEmpty() ||
                    root.findAccessibilityNodeInfosByText("자동입력 방지").isNotEmpty() -> CaptchaType.TEXT
            else -> CaptchaType.UNKNOWN
        }
    }

    /**
     * 텍스트 CAPTCHA를 OCR로 인식하여 자동 입력합니다.
     */
    private suspend fun solveTextCaptcha(root: AccessibilityNodeInfo): Boolean {
        // 화면 캡처
        val bitmap = captureScreen() ?: return false

        // ML Kit OCR 실행 (한국어 우선)
        val text = runOcr(bitmap) ?: return false

        // CAPTCHA 입력 필드 탐색
        val inputNode = nodeScanner.findNodeBySelector(
            root,
            com.autobuy.core.data.model.NodeSelector(
                type = com.autobuy.core.data.model.SelectorType.CLASS_NAME,
                value = "android.widget.EditText"
            )
        ) ?: return false

        // 인식된 텍스트 입력
        val bundle = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text.trim()
            )
        }
        inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        // 확인 버튼 클릭
        val confirmNode = root.findAccessibilityNodeInfosByText("확인").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("인증").firstOrNull()

        confirmNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        return confirmNode != null
    }

    /**
     * 슬라이더 CAPTCHA를 스와이프 제스처로 해결합니다.
     */
    private suspend fun solveSliderCaptcha(root: AccessibilityNodeInfo): Boolean {
        val sliderNode = nodeScanner.findNodeBySelector(
            root,
            com.autobuy.core.data.model.NodeSelector(
                type = com.autobuy.core.data.model.SelectorType.CLASS_NAME,
                value = "android.widget.SeekBar"
            )
        ) ?: return false

        val bounds = android.graphics.Rect()
        sliderNode.getBoundsInScreen(bounds)

        // 슬라이더를 왼쪽 끝에서 오른쪽 끝까지 스와이프
        // (실제 스와이프는 ActionExecutor.swipe로 처리 — 여기서는 AccessibilityAction 시도)
        val dragBundle = android.os.Bundle().apply {
            putFloat("startX", bounds.left.toFloat())
            putFloat("startY", bounds.centerY().toFloat())
            putFloat("endX", bounds.right.toFloat())
            putFloat("endY", bounds.centerY().toFloat())
        }
        return sliderNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * ML Kit으로 OCR을 실행합니다.
     */
    private suspend fun runOcr(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        // 한국어 인식 우선 시도
        koreanRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                continuation.resume(if (text.isNotEmpty()) text else null)
            }
            .addOnFailureListener {
                // Fallback: 영문 인식
                latinRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text.trim().ifEmpty { null })
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
    }

    /**
     * 화면 캡처 (Android 9+ MediaProjection 또는 AccessibilityService takeScreenshot).
     */
    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val service = accessibilityService ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(
                AccessibilityService.TAKE_SCREENSHOT_NEXT_WINDOW,
                { it.run() },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()
                        continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(null)
                    }
                }
            )
        } else {
            continuation.resume(null)
        }
    }

    private fun getAllTexts(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        traverseForText(node, texts)
        return texts
    }

    private fun traverseForText(node: AccessibilityNodeInfo, result: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) result.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseForText(child, result)
        }
    }
}

enum class CaptchaType { TEXT, SLIDER, IMAGE_SELECT, UNKNOWN }
