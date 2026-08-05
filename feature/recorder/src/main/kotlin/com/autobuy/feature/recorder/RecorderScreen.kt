package com.autobuy.feature.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecorderViewModel = hiltViewModel()
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordedSteps by viewModel.recordedSteps.collectAsState()
    val shopName by viewModel.shopName.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect {
            snackbarHostState.showSnackbar("🎉 레코딩이 완료되어 프로필로 저장되었습니다!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Smart Recorder (1회 학습 모드)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF09090B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color(0xFFA78BFA)
                )
            )
        },
        containerColor = Color(0xFF09090B)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 컨트롤 카드
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { viewModel.shopName.value = it },
                        label = { Text("대상 쇼핑몰/사이트 이름") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF3F3F46),
                            focusedLabelColor = Color(0xFFA78BFA),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFF4F4F5)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = viewModel::toggleRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFF7C3AED)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "레코딩 중단 및 프로필 저장" else "레코딩 시작 (구매 동선 추적)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "수집된 동선 스텝 목록 (${recordedSteps.size}개)",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFA1A1AA)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 기록된 스텝 목록
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordedSteps) { step ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF27272A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(step.description, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Action: ${step.action} | Selectors: ${step.selectors.size}개", style = MaterialTheme.typography.labelSmall, color = Color(0xFF14B8A6))
                        }
                    }
                }
            }
        }
    }
}
