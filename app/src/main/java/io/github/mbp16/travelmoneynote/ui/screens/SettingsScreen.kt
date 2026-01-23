package io.github.mbp16.travelmoneynote.ui.screens

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Language
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.Travel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class Currency(
    val code: String,
    val name: String,
    val symbol: String
)

val availableCurrencies = listOf(
    Currency("KRW", "한국 원", "₩"),
    Currency("USD", "미국 달러", "$"),
    Currency("EUR", "유로", "€"),
    Currency("JPY", "일본 엔", "¥"),
    Currency("CNY", "중국 위안", "¥"),
    Currency("GBP", "영국 파운드", "£"),
    Currency("THB", "태국 바트", "฿"),
    Currency("VND", "베트남 동", "₫"),
    Currency("TWD", "대만 달러", "NT$"),
    Currency("SGD", "싱가포르 달러", "S$"),
    Currency("AUD", "호주 달러", "A$"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val travels by viewModel.travels.collectAsState()
    val selectedTravelId by viewModel.selectedTravelId.collectAsState()
    val standardCurrency by viewModel.standardCurrency.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    var settingStandardCurrency by remember { mutableStateOf(false) }
    var showAddTravelDialog by remember { mutableStateOf(false) }
    var showEditTravelDialog by remember { mutableStateOf<Travel?>(null) }
    var showDeleteTravelDialog by remember { mutableStateOf<Travel?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showExportSelectDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportData by remember { mutableStateOf<io.github.mbp16.travelmoneynote.data.ExportData?>(null) }
    var pendingExportTravelIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    val fileNameFormatter = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            viewModel.exportToFile(it, pendingExportTravelIds) { success ->
                Toast.makeText(
                    context,
                    if (success) "내보내기 완료" else "내보내기 실패",
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingExportTravelIds = emptyList()
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            // Parse the file first to show preview
            coroutineScope.launch {
                val exportData = viewModel.parseExportFile(it)
                if (exportData != null) {
                    pendingImportData = exportData
                    showImportConfirmDialog = true
                } else {
                    Toast.makeText(context, "파일을 읽을 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "여행 관리",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { showAddTravelDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "여행 추가")
                    }
                }
            }
            
            if (travels.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dropShadow(
                                shape = RoundedCornerShape(16.dp),
                                shadow = Shadow(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    radius = 8.dp,
                                    offset = DpOffset(0.dp, 4.dp)
                                )
                            )
                    ) {
                        Text(
                            text = "등록된 여행이 없습니다",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(travels) { travel ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dropShadow(
                                shape = RoundedCornerShape(16.dp),
                                shadow = Shadow(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    radius = 16.dp,
                                    spread = 0.dp,
                                    offset = DpOffset(0.dp, 8.dp)
                                )
                            )
                            .clickable { viewModel.selectTravel(travel.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (travel.id == selectedTravelId) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = travel.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                val currency = availableCurrencies.find { it.code == travel.currency }
                                Text(
                                    text = "${dateFormatter.format(Date(travel.startDate))} ~ ${dateFormatter.format(Date(travel.endDate))} | ${currency?.symbol ?: travel.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (travel.id == selectedTravelId) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "선택됨",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showEditTravelDialog = travel }) {
                                Icon(Icons.Default.Edit, contentDescription = "수정")
                            }
                            IconButton(onClick = { showDeleteTravelDialog = travel }) {
                                Icon(Icons.Default.Delete, contentDescription = "삭제")
                            }
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Text(
                    text = "기준 화페 설정",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                val selectedCurrency = availableCurrencies.find { it.code == standardCurrency }
                ExposedDropdownMenuBox(
                    expanded = settingStandardCurrency,
                    onExpandedChange = { settingStandardCurrency = it }
                ) {
                    OutlinedTextField(
                        value = "${selectedCurrency?.symbol ?: ""} ${selectedCurrency?.name ?: standardCurrency}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("기준 화폐") },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface
                        ),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = settingStandardCurrency) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = settingStandardCurrency,
                        onDismissRequest = { settingStandardCurrency = false }
                    ) {
                        availableCurrencies.forEach { currencyOption ->
                            DropdownMenuItem(
                                text = { Text("${currencyOption.symbol} ${currencyOption.name}") },
                                onClick = {
                                    viewModel.setStandardCurrency(currencyOption.code)
                                    settingStandardCurrency = false
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item { LanguageSettingSection() }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            
            item {
                Text(
                    text = "데이터 관리",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                radius = 8.dp,
                                offset = DpOffset(0.dp, 4.dp)
                            )
                        )
                        .clickable {
                            val fileName = "backup_${fileNameFormatter.format(Date())}.zip"
                            showExportSelectDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "파일로 내보내기",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "모든 데이터와 영수증 사진을 ZIP 파일로 저장합니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                radius = 8.dp,
                                offset = DpOffset(0.dp, 4.dp)
                            )
                        )
                        .clickable {
                            importLauncher.launch(arrayOf("application/zip", "application/json"))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "파일에서 불러오기",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "ZIP 또는 JSON 파일에서 데이터를 복원합니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                radius = 8.dp,
                                offset = DpOffset(0.dp, 4.dp)
                            )
                        )
                        .clickable { showResetDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "데이터베이스 초기화",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "모든 데이터가 삭제됩니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showAddTravelDialog) {
        TravelDialog(
            title = "여행 추가",
            onDismiss = { showAddTravelDialog = false },
            onConfirm = { name, startDate, endDate, currency ->
                viewModel.addTravel(name, startDate, endDate, currency)
                showAddTravelDialog = false
            }
        )
    }
    
    showEditTravelDialog?.let { travel ->
        TravelDialog(
            title = "여행 수정",
            initialName = travel.name,
            initialStartDate = travel.startDate,
            initialEndDate = travel.endDate,
            initialCurrency = travel.currency,
            onDismiss = { showEditTravelDialog = null },
            onConfirm = { name, startDate, endDate, currency ->
                viewModel.updateTravel(travel.copy(name = name, startDate = startDate, endDate = endDate, currency = currency))
                showEditTravelDialog = null
            }
        )
    }
    
    showDeleteTravelDialog?.let { travel ->
        AlertDialog(
            onDismissRequest = { showDeleteTravelDialog = null },
            title = { Text("여행 삭제") },
            text = { Text("'${travel.name}' 여행을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTravel(travel)
                        showDeleteTravelDialog = null
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTravelDialog = null }) {
                    Text("취소")
                }
            }
        )
    }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("데이터베이스 초기화") },
            text = { Text("정말로 모든 데이터를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetDatabase()
                        showResetDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
    
    if (showImportConfirmDialog && pendingImportData != null) {
        val importData = pendingImportData ?: return
        AlertDialog(
            onDismissRequest = { 
                showImportConfirmDialog = false
                pendingImportUri = null
                pendingImportData = null
            },
            title = { Text("데이터 불러오기") },
            text = {
                Column {
                    Text("다음 여행들을 추가합니다:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(importData.travels) { travel ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = travel.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${dateFormatter.format(Date(travel.startDate))} ~ ${dateFormatter.format(Date(travel.endDate))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (travel.persons.isNotEmpty()) {
                                    Text(
                                        text = "참가자: ${travel.persons.joinToString(", ") { it.name }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let { uri ->
                            viewModel.importFromFile(uri) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                        showImportConfirmDialog = false
                        pendingImportUri = null
                        pendingImportData = null
                    }
                ) {
                    Text("불러오기")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportConfirmDialog = false
                    pendingImportUri = null
                    pendingImportData = null
                }) {
                    Text("취소")
                }
            }
        )
    }
    
    if (showExportSelectDialog) {
        ExportTravelSelectDialog(
            travels = travels,
            viewModel = viewModel,
            onDismiss = {
                showExportSelectDialog = false
            },
            onConfirm = { selectedTravelIds ->
                pendingExportTravelIds = selectedTravelIds
                showExportSelectDialog = false
                val fileName = "backup_${fileNameFormatter.format(Date())}.zip"
                exportLauncher.launch(fileName)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelDialog(
    title: String,
    initialName: String = "",
    initialStartDate: Long = System.currentTimeMillis(),
    initialEndDate: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
    initialCurrency: String = "KRW",
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDate: Long, endDate: Long, currency: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }
    var currency by remember { mutableStateOf(initialCurrency) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    val selectedCurrency = availableCurrencies.find { it.code == currency }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("여행 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("시작일")
                        Text(dateFormatter.format(Date(startDate)))
                    }
                }
                
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("종료일")
                        Text(dateFormatter.format(Date(endDate)))
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = showCurrencyDropdown,
                    onExpandedChange = { showCurrencyDropdown = it }
                ) {
                    OutlinedTextField(
                        value = "${selectedCurrency?.symbol ?: ""} ${selectedCurrency?.name ?: currency}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("화폐") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCurrencyDropdown,
                        onDismissRequest = { showCurrencyDropdown = false }
                    ) {
                        availableCurrencies.forEach { currencyOption ->
                            DropdownMenuItem(
                                text = { Text("${currencyOption.symbol} ${currencyOption.name}") },
                                onClick = {
                                    currency = currencyOption.code
                                    showCurrencyDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, startDate, endDate, currency) },
                enabled = name.isNotBlank()
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
    
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDate = it }
                    showStartDatePicker = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { endDate = it }
                    showEndDatePicker = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingSection() {
    val context = LocalContext.current
    
    // 현재 설정된 언어 이름 가져오기 (화면 표시용)
    val currentLocale = context.resources.configuration.locales[0]
    val displayLanguage = currentLocale.displayName 

    Column {
        Text(
            text = "언어 설정", // strings.xml: @string/settings_language
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // 드롭다운 대신 클릭 가능한 카드 사용
        Card(
             shape = RoundedCornerShape(16.dp),
             modifier = Modifier
                 .fillMaxWidth()
                 .dropShadow(
                     shape = RoundedCornerShape(16.dp),
                     shadow = Shadow(
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                         radius = 8.dp,
                         offset = DpOffset(0.dp, 4.dp)
                     ))
                 .clickable {
                    // [핵심] 시스템의 앱 언어 설정 화면으로 이동하는 인텐트
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_language),
                        contentDescription = "언어 설정",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "언어 변경", // strings.xml: @string/change_language
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // 현재 언어 상태 보여주기 (예: 한국어)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayLanguage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward, // 혹은 ChevronRight
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportTravelSelectDialog(
    travels: List<Travel>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedTravelIds by remember { mutableStateOf(travels.map { it.id }.toSet()) }
    val personsMap = remember { mutableStateMapOf<Long, List<String>>() }
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    
    // Fetch persons for each travel
    LaunchedEffect(travels) {
        travels.forEach { travel ->
            viewModel.getPersonsForTravel(travel.id) { persons ->
                personsMap[travel.id] = persons.map { it.name }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내보낼 여행 선택") },
        text = {
            LazyColumn {
                items(travels) { travel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTravelIds = if (travel.id in selectedTravelIds) {
                                    selectedTravelIds - travel.id
                                } else {
                                    selectedTravelIds + travel.id
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = travel.id in selectedTravelIds,
                            onCheckedChange = { checked ->
                                selectedTravelIds = if (checked) {
                                    selectedTravelIds + travel.id
                                } else {
                                    selectedTravelIds - travel.id
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = travel.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${dateFormatter.format(Date(travel.startDate))} ~ ${dateFormatter.format(Date(travel.endDate))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            personsMap[travel.id]?.let { persons ->
                                if (persons.isNotEmpty()) {
                                    Text(
                                        text = "참가자: ${persons.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTravelIds.toList()) },
                enabled = selectedTravelIds.isNotEmpty()
            ) {
                Text("내보내기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
