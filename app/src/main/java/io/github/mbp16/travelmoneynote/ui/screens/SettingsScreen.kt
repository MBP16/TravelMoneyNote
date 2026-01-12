package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.Travel
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
    var showResetDialog by remember { mutableStateOf(false) }
    var showAddTravelDialog by remember { mutableStateOf(false) }
    var showEditTravelDialog by remember { mutableStateOf<Travel?>(null) }
    var showDeleteTravelDialog by remember { mutableStateOf<Travel?>(null) }
    
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    
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
                        modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier
                            .fillMaxWidth()
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
                    text = "데이터 관리",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
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
