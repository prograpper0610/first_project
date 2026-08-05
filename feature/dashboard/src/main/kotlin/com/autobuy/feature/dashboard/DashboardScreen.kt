package com.autobuy.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autobuy.core.accessibility.engine.AutoBuyState
import com.autobuy.core.accessibility.module.QueueStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToConfig: () -> Unit,
    onNavigateToRecorder: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.autoBuyState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val queueStatus by viewModel.queueStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AutoBuy Assistant",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToRecorder) {
                        Icon(Icons.Outlined.Videocam, contentDescription = "레코더")
                    }
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF09090B),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color(0xFFA78BFA)
                )
            )
        },
        containerColor = Color(0xFF09090B)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🚨 비상 강제 종료 배너 (언제든 사용 가능)
            item {
                EmergencyKillBanner(
                    onEmergencyKill = { viewModel.triggerEmergencyKill(hardKill = true) }
                )
            }

            // 상태 카드
            item {
                StateCard(state = state, elapsedSeconds = elapsed)
            }

            // 대기열 카드 (대기 중일 때만 표시)
            item {
                AnimatedVisibility(
                    visible = queueStatus == QueueStatus.IN_QUEUE,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    QueueCard(viewModel = viewModel)
                }
            }

            // 제어 버튼
            item {
                ControlButtons(
                    state = state,
                    onStop = viewModel::stopAutoBuy,
                    onResume = viewModel::resume,
                    onStart = onNavigateToConfig
                )
            }

            // 실행 로그
            item {
                Text(
                    text = "실행 로그",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFA1A1AA),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(logs, key = { it.id }) { log ->
                LogRow(entry = log)
            }
        }
    }
}

/**
 * 🚨 비상 강제 종료 전용 강조 배너 버튼
 */
@Composable
private fun EmergencyKillBanner(onEmergencyKill: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                Column {
                    Text("과부하/무한루프/발열 시", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    Text("모든 스레드 및 프로세스 즉시 파기", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFCA5A5))
                }
            }
            Button(
                onClick = onEmergencyKill,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("🚨 비상 강제종료", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun StateCard(state: AutoBuyState, elapsedSeconds: Long) {
    val (statusText, statusColor, isAnimating) = when (state) {
        is AutoBuyState.Idle -> Triple("대기 중", Color(0xFF52525B), false)
        is AutoBuyState.Waiting -> Triple("오픈 시간 대기", Color(0xFFF59E0B), true)
        is AutoBuyState.ModeAPending, is AutoBuyState.ModeBPolling ->
            Triple("모니터링 중", Color(0xFF14B8A6), true)
        is AutoBuyState.PurchaseStarted -> Triple("구매 시작!", Color(0xFF7C3AED), true)
        is AutoBuyState.QueueHandling -> Triple("대기열 처리", Color(0xFFF59E0B), true)
        is AutoBuyState.FormFilling -> Triple("자동 입력 중", Color(0xFF7C3AED), true)
        is AutoBuyState.AntiBotDetected -> Triple("⚠️ CAPTCHA 감지", Color(0xFFEF4444), true)
        is AutoBuyState.Handover -> Triple("🎉 결제 이관 완료!", Color(0xFF10B981), false)
        is AutoBuyState.Complete -> Triple("✅ 구매 완료", Color(0xFF10B981), false)
        is AutoBuyState.Error -> Triple("❌ 오류 발생", Color(0xFFEF4444), false)
        is AutoBuyState.Paused -> Triple("⏸ 일시 정지", Color(0xFF52525B), false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = statusColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAnimating) statusColor.copy(alpha = alpha)
                                else statusColor
                            )
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusItem(label = "경과 시간", value = formatElapsed(elapsedSeconds))
                    StatusItem(label = "상태", value = state::class.simpleName ?: "-")
                }
            }
        }
    }
}

@Composable
private fun QueueCard(viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "⏳ 대기열 처리 중",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF59E0B),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "대기열 통과까지 포그라운드를 유지합니다.\n화면을 꺼도 자동으로 계속 실행됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA1A1AA)
            )
        }
    }
}

@Composable
private fun ControlButtons(
    state: AutoBuyState,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onStart: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            is AutoBuyState.Idle, is AutoBuyState.Complete, is AutoBuyState.Error -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("자동구매 설정 및 시작", fontWeight = FontWeight.SemiBold)
                }
            }
            is AutoBuyState.Paused -> {
                OutlinedButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF14B8A6)),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("재개", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("중단", fontWeight = FontWeight.SemiBold)
                }
            }
            else -> {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("일반 중단", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF18181B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF52525B),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD4D4D8),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF71717A))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF4F4F5), fontWeight = FontWeight.Medium)
    }
}

private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
