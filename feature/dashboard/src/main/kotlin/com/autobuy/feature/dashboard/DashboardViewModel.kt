package com.autobuy.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobuy.core.accessibility.AutoBuyAccessibilityService
import com.autobuy.core.accessibility.engine.AutoBuyOrchestrator
import com.autobuy.core.accessibility.engine.AutoBuyState
import com.autobuy.core.accessibility.engine.NtpTimeSyncer
import com.autobuy.core.accessibility.engine.SystemKillSwitch
import com.autobuy.core.accessibility.module.QueueHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orchestrator: AutoBuyOrchestrator,
    private val ntpTimeSyncer: NtpTimeSyncer,
    private val queueHandler: QueueHandler,
    private val killSwitch: SystemKillSwitch
) : ViewModel() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    val autoBuyState: StateFlow<AutoBuyState> = orchestrator.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoBuyState.Idle)

    val isAccessibilityEnabled: StateFlow<Boolean> = MutableStateFlow(
        AutoBuyAccessibilityService.isRunning
    )

    val queueStatus = queueHandler.queueStatus

    val ntpSyncDescription: String get() = ntpTimeSyncer.getLastSyncDescription()

    private var startedAt: Long = 0L

    init {
        observeStateChanges()
        startElapsedTimer()
    }

    private fun observeStateChanges() {
        viewModelScope.launch {
            orchestrator.state.collect { state ->
                val message = when (state) {
                    is AutoBuyState.Idle -> null
                    is AutoBuyState.Waiting -> "⏰ 오픈 시간 대기 중..."
                    is AutoBuyState.ModeAPending -> "🔍 Mode A: 구매 버튼 활성화 감지 중"
                    is AutoBuyState.ModeBPolling -> "🔄 Mode B: 고속 상품 폴링 중"
                    is AutoBuyState.PurchaseStarted -> "🛒 구매 프로세스 시작"
                    is AutoBuyState.QueueHandling -> "⏳ 대기열 통과 대기 중"
                    is AutoBuyState.FormFilling -> "📝 결제 정보 입력 중 (Step ${state.stepIndex + 1})"
                    is AutoBuyState.AntiBotDetected -> "⚠️ CAPTCHA 감지 — 자동 해결 중"
                    is AutoBuyState.Handover -> "🎉 최종 결제 화면 이관 완료!"
                    is AutoBuyState.Complete -> "✅ 구매 완료"
                    is AutoBuyState.Error -> "❌ 오류: ${state.reason}"
                    is AutoBuyState.Paused -> "⏸ 일시 정지"
                }
                message?.let { addLog(it) }

                if (state is AutoBuyState.Waiting) startedAt = System.currentTimeMillis()
            }
        }
    }

    private fun startElapsedTimer() {
        viewModelScope.launch {
            while (true) {
                if (startedAt > 0L && autoBuyState.value !is AutoBuyState.Idle) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - startedAt) / 1000
                }
                delay(1000)
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = LogEntry(timestamp = timestamp, message = message)
        _logs.value = (listOf(entry) + _logs.value).take(100)
    }

    fun stopAutoBuy() {
        orchestrator.stopAutoBuy()
        startedAt = 0L
        _elapsedSeconds.value = 0L
        addLog("⏹ 사용자에 의해 중단됨")
    }

    fun resume() {
        orchestrator.resume()
        addLog("▶ 재개됨")
    }

    /**
     * 🚨 비상 강제 종료 (Emergency Kill Switch)
     * 무한루프, CPU 과부하, 발열 발생 시 원클릭으로 모든 스레드 및 프로세스 파기
     */
    fun triggerEmergencyKill(hardKill: Boolean = true) {
        addLog("🚨 비상 강제 종료 발동!")
        killSwitch.emergencyKill(hardProcessKill = hardKill)
    }
}
