package com.autobuy.feature.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autobuy.core.accessibility.engine.PurchaseMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val targetUrl by viewModel.targetUrl.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()

    val recipientName by viewModel.recipientName.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val address by viewModel.address.collectAsState()
    val addressDetail by viewModel.addressDetail.collectAsState()
    val postalCode by viewModel.postalCode.collectAsState()

    val cardNumber by viewModel.cardNumber.collectAsState()
    val expiry by viewModel.expiry.collectAsState()
    val cvv by viewModel.cvv.collectAsState()
    val cardHolder by viewModel.cardHolder.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToDashboard.collect {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자동구매 설정", fontWeight = FontWeight.Bold) },
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
        bottomBar = {
            Surface(
                color = Color(0xFF18181B),
                tonalElevation = 8.dp
            ) {
                Button(
                    onClick = { viewModel.startAutoBuy(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("설정 저장 및 자동구매 시작", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        containerColor = Color(0xFF09090B)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // 1. 타겟 상품 & 진입 모드
            item {
                ConfigCard(title = "🎯 타겟 상품 및 모드") {
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { viewModel.targetUrl.value = it },
                        label = { Text("상품 상세 URL 또는 검색 키워드") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("진입 모드 선택", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA1A1AA))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == PurchaseMode.MODE_A,
                            onClick = { viewModel.selectedMode.value = PurchaseMode.MODE_A },
                            label = { Text("Mode A (사전 진입 정각 새로고침)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C3AED),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = selectedMode == PurchaseMode.MODE_B,
                            onClick = { viewModel.selectedMode.value = PurchaseMode.MODE_B },
                            label = { Text("Mode B (목록 고속 폴링)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF14B8A6),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 2. 쇼핑몰 프로필 선택
            item {
                ConfigCard(title = "🛒 쇼핑몰 레시피 선택") {
                    Text("지원되는 레시피", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA1A1AA))
                    Spacer(modifier = Modifier.height(8.dp))
                    profiles.forEach { profile ->
                        val isSelected = selectedProfileId == profile.id ||
                                (selectedProfileId == null && profile.shopId == "coupang")
                        Card(
                            onClick = { viewModel.selectedProfileId.value = profile.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF27272A) else Color(0xFF09090B)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C3AED)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(profile.shopName, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("v${profile.version} (${if (profile.isBuiltin) "내장" else "커스텀"})", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA1A1AA))
                                }
                                if (isSelected) {
                                    Text("선택됨", color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. 배송지 정보 (AES-256 암호화)
            item {
                ConfigCard(
                    title = "🏠 배송지 정보",
                    trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "암호화", tint = Color(0xFF14B8A6)) }
                ) {
                    Text("모든 개인정보는 단말 내부 로컬 DB에 AES-256으로 암호화 저장됩니다.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF14B8A6))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { viewModel.recipientName.value = it },
                        label = { Text("수령인 이름") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { viewModel.phoneNumber.value = it },
                        label = { Text("연락처") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { viewModel.address.value = it },
                            label = { Text("기본 주소") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = postalCode,
                            onValueChange = { viewModel.postalCode.value = it },
                            label = { Text("우편번호") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = textFieldColors()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addressDetail,
                        onValueChange = { viewModel.addressDetail.value = it },
                        label = { Text("상세 주소") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors()
                    )
                }
            }

            // 4. 결제 수단 (AES-256 암호화)
            item {
                ConfigCard(
                    title = "💳 결제 수단",
                    trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "암호화", tint = Color(0xFF14B8A6)) }
                ) {
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { viewModel.cardNumber.value = it },
                        label = { Text("카드 번호 (16자리)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expiry,
                            onValueChange = { viewModel.expiry.value = it },
                            label = { Text("유효기간 (MM/YY)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = cvv,
                            onValueChange = { viewModel.cvv.value = it },
                            label = { Text("CVV") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = textFieldColors()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cardHolder,
                        onValueChange = { viewModel.cardHolder.value = it },
                        label = { Text("카드 소유자명") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors()
                    )
                }
            }

            // 5. 동적 커스텀 필드 (미정 사이트/앱 완벽 대처!)
            item {
                ConfigCard(
                    title = "🧩 동적 커스텀 필드 (미정 사이트/앱 확장용)",
                    trailingIcon = {
                        IconButton(onClick = viewModel::addCustomField) {
                            Icon(Icons.Default.Add, contentDescription = "필드 추가", tint = Color(0xFF7C3AED))
                        }
                    }
                ) {
                    Text(
                        "추후 테스트할 사이트/앱에 따라 특수한 옵션, 쿠폰 코드, 비밀번호 힌트 등의 추가 입력을 유연하게 동적 등록할 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (viewModel.customFields.isEmpty()) {
                        Text("등록된 커스텀 필드가 없습니다. 오른쪽 위 (+) 버튼을 눌러 추가하세요.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF52525B))
                    } else {
                        viewModel.customFields.forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = entry.key,
                                    onValueChange = { newKey -> viewModel.updateCustomField(index, newKey, entry.value) },
                                    label = { Text("Key (예: option1)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = textFieldColors()
                                )
                                OutlinedTextField(
                                    value = entry.value,
                                    onValueChange = { newVal -> viewModel.updateCustomField(index, entry.key, newVal) },
                                    label = { Text("Value (입력값)") },
                                    modifier = Modifier.weight(1.5f),
                                    singleLine = true,
                                    colors = textFieldColors()
                                )
                                IconButton(onClick = { viewModel.removeCustomField(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    title: String,
    trailingIcon: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF4F4F5), fontWeight = FontWeight.SemiBold)
                trailingIcon?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF7C3AED),
    unfocusedBorderColor = Color(0xFF3F3F46),
    focusedLabelColor = Color(0xFFA78BFA),
    unfocusedLabelColor = Color(0xFF71717A),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color(0xFFF4F4F5),
    cursorColor = Color(0xFF7C3AED)
)
