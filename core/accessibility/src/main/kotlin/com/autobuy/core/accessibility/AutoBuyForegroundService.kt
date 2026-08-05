package com.autobuy.core.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import com.autobuy.core.accessibility.engine.AutoBuyConfig
import com.autobuy.core.accessibility.engine.AutoBuyOrchestrator
import com.autobuy.core.accessibility.engine.AutoBuyState
import com.autobuy.core.accessibility.engine.NtpTimeSyncer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AutoBuy 포그라운드 서비스.
 *
 * 비상 강제 중단(ACTION_KILL_ALL) 수신 시 WakeLock 및 모든 스레드를 즉시 강제 종료합니다.
 */
@AndroidEntryPoint
class AutoBuyForegroundService : Service() {

    @Inject
    lateinit var orchestrator: AutoBuyOrchestrator

    @Inject
    lateinit var ntpTimeSyncer: NtpTimeSyncer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "autobuy_foreground"
        private const val NOTIFICATION_ID = 1000
        const val ACTION_START = "com.autobuy.START"
        const val ACTION_STOP = "com.autobuy.STOP"
        const val ACTION_KILL_ALL = "com.autobuy.KILL_ALL"
        const val EXTRA_CONFIG = "extra_config"

        fun startIntent(context: Context, config: AutoBuyConfig): Intent {
            return Intent(context, AutoBuyForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, config.sessionId)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, AutoBuyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }

        fun killAllIntent(context: Context): Intent {
            return Intent(context, AutoBuyForegroundService::class.java).apply {
                action = ACTION_KILL_ALL
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("자동구매 시스템 작동 준비 완료"))
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_KILL_ALL -> handleKillAll()
        }
        return START_NOT_STICKY  // 비상 시 완전 종료되도록 조정
    }

    private fun handleStart() {
        serviceScope.launch {
            updateNotification("⏰ 서버 시간 동기화 중...")
            val synced = ntpTimeSyncer.sync()
            if (!synced) {
                updateNotification("⚠️ 시간 동기화 실패 — 로컬 시간 사용")
            } else {
                updateNotification("✅ 시간 동기화 완료 (오프셋: ${ntpTimeSyncer.getOffsetMs()}ms)")
            }

            orchestrator.state.collect { state ->
                val message = when (state) {
                    is AutoBuyState.Idle -> "대기 중"
                    is AutoBuyState.Waiting -> "오픈 시간 대기 중..."
                    is AutoBuyState.ModeAPending -> "Mode A: 구매 버튼 활성화 대기"
                    is AutoBuyState.ModeBPolling -> "Mode B: 고속 폴링 중..."
                    is AutoBuyState.PurchaseStarted -> "구매 시작!"
                    is AutoBuyState.QueueHandling -> "⏳ 대기열 처리 중"
                    is AutoBuyState.FormFilling -> "📝 자동 입력 중 (Step ${state.stepIndex + 1})"
                    is AutoBuyState.AntiBotDetected -> "⚠️ CAPTCHA 감지 — 자동 해결 시도 중"
                    is AutoBuyState.Handover -> "🎉 결제 화면 이관 완료!"
                    is AutoBuyState.Complete -> "✅ 구매 완료"
                    is AutoBuyState.Error -> "❌ 오류: ${state.reason}"
                    is AutoBuyState.Paused -> "⏸ 일시 정지됨"
                }
                updateNotification(message)

                if (state is AutoBuyState.Complete) {
                    delay(3000)
                    stopSelf()
                }
            }
        }
    }

    private fun handleStop() {
        orchestrator.stopAutoBuy()
        stopSelf()
    }

    /**
     * 🚨 비상 강제 종료 처리 (WakeLock 해제, 코루틴 캔슬, 프로세스 파기)
     */
    private fun handleKillAll() {
        releaseWakeLock()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoBuyAssistant:AutoBuyWakeLock"
        ).apply { acquire(24 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun updateNotification(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(contentText: String): Notification {
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val killAllPending = PendingIntent.getService(
            this, 1, killAllIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("🛒 AutoBuy Assistant")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_delete, "중단", stopPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🚨 비상 강제종료", killAllPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AutoBuy 실행 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "자동구매 진행 중 상태를 표시합니다"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
