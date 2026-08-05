package com.autobuy.core.accessibility.engine

import android.content.Context
import android.content.Intent
import android.os.Process
import com.autobuy.core.accessibility.AutoBuyForegroundService
import com.autobuy.core.accessibility.module.ProductMonitorLoop
import com.autobuy.core.accessibility.module.TouchEventRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 비상 강제 중단 컨트롤러 (Emergency Kill Switch).
 *
 * 무한 루프, CPU 과부하, 발열 발생 시 사용자가 원클릭으로
 * 모든 백그라운드 스레드, 고속 폴링 루프, 포그라운드 서비스, WakeLock을
 * 즉시 강제 파기하고 프로세스를 완전 정지시킵니다.
 */
@Singleton
class SystemKillSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orchestrator: AutoBuyOrchestrator,
    private val productMonitorLoop: ProductMonitorLoop,
    private val touchEventRecorder: TouchEventRecorder,
    private val recipeExecutor: RecipeExecutor
) {
    /**
     * 비상 강제 중단 (Emergency Hard Kill) 실행.
     * @param hardProcessKill true 시 프로세스 자체를 자폭(Process.killProcess) 시켜 스레드 잔존 100% 차단
     */
    fun emergencyKill(hardProcessKill: Boolean = false) {
        try {
            // 1. 고속 폴링 루프 및 레코더 즉시 파기
            productMonitorLoop.stopPolling()
            touchEventRecorder.stopRecording()

            // 2. 메모리 내 민감 데이터 즉시 제로화 및 삭제
            recipeExecutor.clearSensitiveData()

            // 3. 오케스트레이터 상태 초기화
            orchestrator.stopAutoBuy()

            // 4. 포그라운드 서비스 강제 종료 Intent 발송
            val stopIntent = Intent(context, AutoBuyForegroundService::class.java).apply {
                action = AutoBuyForegroundService.ACTION_KILL_ALL
            }
            context.startService(stopIntent)

        } catch (e: Exception) {
            // 실패 무시
        } finally {
            if (hardProcessKill) {
                // 5. 하드 프로세스 파기 (CPU 과부하/무한루프 시 100% 즉시 종료)
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(0)
            }
        }
    }
}
