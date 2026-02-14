package io.github.mbp16.travelmoneynote.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.R
import io.github.mbp16.travelmoneynote.data.Travel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class Currency(
    val code: String,
    val nameResId: Int,
    val symbol: String
)

val availableCurrencies = listOf(
    Currency("KRW", R.string.currency_krw, "₩"),
    Currency("USD", R.string.currency_usd, "$"),
    Currency("EUR", R.string.currency_eur, "€"),
    Currency("JPY", R.string.currency_jpy, "¥"),
    Currency("CNY", R.string.currency_cny, "¥"),
    Currency("GBP", R.string.currency_gbp, "£"),
    Currency("THB", R.string.currency_thb, "฿"),
    Currency("VND", R.string.currency_vnd, "₫"),
    Currency("TWD", R.string.currency_twd, "NT$"),
    Currency("SGD", R.string.currency_sgd, "S$"),
    Currency("AUD", R.string.currency_aud, "A$"),
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
                    if (success) context.getString(R.string.setting_export_complete)
                    else context.getString(R.string.setting_export_failure),
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
                    Toast.makeText(context, context.getString(R.string.setting_cannot_read_file), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                        text = stringResource(R.string.setting_manage_trip),
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
                            text = stringResource(R.string.setting_no_trip_added),
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
                    text = stringResource(R.string.setting_standard_currency_title),
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
                        value = if (selectedCurrency != null) "${selectedCurrency.symbol} ${stringResource(selectedCurrency.nameResId)}" else standardCurrency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.setting_standard_currency_field_title)) },
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
                                text = { Text("${currencyOption.symbol} ${stringResource(currencyOption.nameResId)}") },
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
                    text = stringResource(R.string.setting_data_manage_title),
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
                                text = stringResource(R.string.setting_export_to_file),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.setting_export_to_file_detail),
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
                                text = stringResource(R.string.setting_import_from_file),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.setting_import_from_file_detail),
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
                                text = stringResource(R.string.setting_reset_database),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.setting_reset_database_detail),
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
            title = stringResource(R.string.setting_tripdialog_title_add),
            onDismiss = { showAddTravelDialog = false },
            onConfirm = { name, startDate, endDate, currency ->
                viewModel.addTravel(name, startDate, endDate, currency)
                showAddTravelDialog = false
            }
        )
    }
    
    showEditTravelDialog?.let { travel ->
        TravelDialog(
            title = stringResource(R.string.setting_tripdialog_title_edit),
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
            title = { Text(stringResource(R.string.setting_tripdeletedialog_title)) },
            text = { Text(stringResource(R.string.setting_tripdeletedialog_detail, travel.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTravel(travel)
                        showDeleteTravelDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTravelDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.setting_reset_database)) },
            text = { Text(stringResource(R.string.setting_reset_database_dialog_detail)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetDatabase()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
            title = { Text(stringResource(R.string.setting_importdialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.setting_importdialog_detail))
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
                                        text = context.getString(
                                            R.string.setting_importdialog_people,
                                            travel.persons.joinToString(", ") { it.name }),
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
                    Text(stringResource(R.string.setting_importdialog_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportConfirmDialog = false
                    pendingImportUri = null
                    pendingImportData = null
                }) {
                    Text(stringResource(R.string.cancel))
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
                    label = { Text(stringResource(R.string.setting_tripdialog_trip_title)) },
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
                        Text(stringResource(R.string.setting_tripdialog_start_date))
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
                        Text(stringResource(R.string.setting_tripdialog_end_date))
                        Text(dateFormatter.format(Date(endDate)))
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = showCurrencyDropdown,
                    onExpandedChange = { showCurrencyDropdown = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedCurrency != null) "${selectedCurrency.symbol} ${stringResource(selectedCurrency.nameResId)}" else currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.setting_tripdialog_currency)) },
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
                                text = { Text("${currencyOption.symbol} ${stringResource(currencyOption.nameResId)}") },
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
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
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
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
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
            text = stringResource(R.string.language_setting), // strings.xml: @string/settings_language
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
                     )
                 )
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
                        text = stringResource(R.string.setting_language_box_text), // strings.xml: @string/change_language
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
        title = { Text(stringResource(R.string.setting_exportdialog_title)) },
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
                                        text = stringResource(
                                            R.string.setting_exportdialog_people,
                                            persons.joinToString(", ")
                                        ),
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
                Text(stringResource(R.string.setting_exportdialog_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
