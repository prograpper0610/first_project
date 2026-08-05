package com.autobuy.core.accessibility.module

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 5: Handover Layer — 최종 결제 화면 이관 처리.
 *
 * 최종 결제 버튼 직전 감지 시:
 * 1. 자동화 일시 정지
 * 2. 긴급 푸시 알림 발송
 * 3. 진동 패턴으로 즉각 주의 유도
 * 4. 앱을 화면 전면으로 포커스
 */
@Singleton
class HandoverLayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID_HANDOVER = "autobuy_handover"
        private const val NOTIFICATION_ID_HANDOVER = 1001
    }

    init {
        createNotificationChannels()
    }

    /**
     * Handover 실행: 알림 + 진동 + 앱 포커스.
     */
    fun execute() {
        vibrate()
        sendHandoverNotification()
        bringAppToForeground()
    }

    /**
     * 긴급 진동 패턴 (즉각적 주의 유도).
     * 패턴: 100ms ON → 100ms OFF → 100ms ON → 200ms OFF × 3회
     */
    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val pattern = longArrayOf(0, 100, 100, 100, 100, 200, 0, 100, 100, 100, 100, 200, 0, 100, 100, 100)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 100, 100, 100, 100, 200, 0, 100, 100, 100, 100, 200)
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // 진동 실패 무시 (권한 미획득 등)
        }
    }

    /**
     * HIGH Priority 긴급 결제 알림 발송.
     */
    private fun sendHandoverNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "handover")
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_HANDOVER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🛒 결제 준비 완료!")
            .setContentText("자동구매가 완료 직전입니다. 지금 바로 결제를 완료하세요!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("자동구매 프로세스가 최종 결제 화면까지 완료되었습니다.\n\n지금 알림을 탭하여 결제를 완료하세요. (생체인증 또는 비밀번호 입력)"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HANDOVER, notification)
    }

    /**
     * 앱을 화면 최전면으로 가져옵니다.
     */
    private fun bringAppToForeground() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "handover")
        }
        intent?.let { context.startActivity(it) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val handoverChannel = NotificationChannel(
                CHANNEL_ID_HANDOVER,
                "결제 완료 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "자동구매 최종 결제 단계에서 사용자에게 알립니다"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(listOf(handoverChannel))
        }
    }
}

/**
 * 사용자 알림 유틸 (진동/소리/알림 통합).
 */
@Singleton
class UserNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * CAPTCHA Fallback 알림: 짧은 진동 + 알림.
     */
    fun notifyCaptchaFallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(500)
            }
        } catch (e: Exception) { /* 무시 */ }

        sendCaptchaNotification()
    }

    private fun sendCaptchaNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "autobuy_captcha", "CAPTCHA 알림", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "autobuy_captcha")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 보안문자 확인 필요")
            .setContentText("자동 우회에 실패했습니다. CAPTCHA를 직접 입력해주세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }
}
