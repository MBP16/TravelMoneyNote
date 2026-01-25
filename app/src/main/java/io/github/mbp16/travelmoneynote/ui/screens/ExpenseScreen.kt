package io.github.mbp16.travelmoneynote.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.PaymentMethod
import io.github.mbp16.travelmoneynote.data.Person
import io.github.mbp16.travelmoneynote.ui.components.ImageViewerDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class PaymentEntry(
    val person: Person?,
    val amount: String,
    val method: PaymentMethod
)

data class ExpenseUserEntry(
    val person: Person?,
    val amount: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: MainViewModel,
    expenseId: Long? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val persons by viewModel.persons.collectAsState()
    val currentCurrency by viewModel.currentCurrency.collectAsState()
    val standardCurrency by viewModel.standardCurrency.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()
    val currencySymbol = availableCurrencies.find { it.code == currentCurrency }?.symbol ?: "₩"
    val standardCurrencySymbol = availableCurrencies.find { it.code == standardCurrency }?.symbol ?: "₩"
    val showConversion = currentCurrency != standardCurrency && exchangeRates != null
    
    // Edit Mode State
    val expenseWithPayments = if (expenseId != null) {
        viewModel.getExpenseWithPayments(expenseId).collectAsState(initial = null).value
    } else null

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var payments by remember { mutableStateOf(listOf(PaymentEntry(null, "", PaymentMethod.CASH))) }
    var expenseUsers by remember { mutableStateOf(listOf(ExpenseUserEntry(null, "", ""))) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // Date/Time picker state
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Initialize state if editing
    LaunchedEffect(expenseWithPayments, persons) {
        if (!isInitialized && expenseId != null && expenseWithPayments != null && persons.isNotEmpty()) {
            title = expenseWithPayments.expense.title
            description = expenseWithPayments.expense.description
            createdAt = expenseWithPayments.expense.createdAt
            // Load photos from photoUris field
            val urisString = expenseWithPayments.expense.photoUris
            photoUris = if (!urisString.isNullOrEmpty()) {
                urisString.split(",").mapNotNull { uri ->
                    if (uri.isNotBlank()) Uri.parse(uri.trim()) else null
                }
            } else {
                emptyList()
            }
            payments = expenseWithPayments.payments.map { pwp ->
                PaymentEntry(
                    person = persons.find { it.id == pwp.payment.personId },
                    amount = pwp.payment.amount.toString(),
                    method = pwp.payment.method
                )
            }.ifEmpty { listOf(PaymentEntry(null, "", PaymentMethod.CASH)) }
            expenseUsers = expenseWithPayments.expenseUsers.map { eup ->
                ExpenseUserEntry(
                    person = persons.find { it.id == eup.expenseUser.personId },
                    amount = eup.expenseUser.amount.toString(),
                    description = eup.expenseUser.description
                )
            }.ifEmpty { listOf(ExpenseUserEntry(null, "", "")) }
            isInitialized = true
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(context.filesDir, "IMG_$timeStamp.jpg")
            inputStream?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            photoUris = photoUris + Uri.fromFile(imageFile)
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            photoUris = photoUris + tempPhotoUri!!
        }
    }
    
    fun createImageFile(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(context.filesDir, "IMG_$timeStamp.jpg")
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    
    val totalAmount = payments.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val totalUserAmount = expenseUsers.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val isValid = payments.all { it.person != null && (it.amount.toDoubleOrNull() ?: 0.0) > 0 } && payments.isNotEmpty() &&
        expenseUsers.all { it.person != null && (it.amount.toDoubleOrNull() ?: 0.0) > 0 } && expenseUsers.isNotEmpty()

    val divideEvenlyAmount = {
        expenseUsers = persons.map { person ->
            ExpenseUserEntry(
                person = person,
                amount = String.format("%.2f", totalAmount / persons.size),
                description = ""
            )
        }
    }

    val copyFromPayments = {
        expenseUsers = payments.mapNotNull { payment ->
            payment.person?.let { person ->
                ExpenseUserEntry(
                    person = person,
                    amount = payment.amount,
                    description = ""
                )
            }
        }
    }

    val onSave = {
        if (isValid) {
            val photoUrisString = if (photoUris.isNotEmpty()) {
                photoUris.joinToString(",") { it.toString() }
            } else null
            
            if (expenseId == null) {
                viewModel.addExpenseWithPayments(
                    title = title.trim(),
                    totalAmount = totalAmount,
                    description = description.trim(),
                    photoUri = photoUrisString,
                    payments = payments.mapNotNull { payment ->
                        payment.person?.let { person ->
                            val amount = payment.amount.toDoubleOrNull() ?: return@mapNotNull null
                            Triple(person.id, amount, payment.method)
                        }
                    },
                    expenseUsers = expenseUsers.mapNotNull { eu ->
                        eu.person?.let { person ->
                            val amount = eu.amount.toDoubleOrNull() ?: return@mapNotNull null
                            Triple(person.id, amount, eu.description)
                        }
                    },
                    createdAt = createdAt
                )
            } else {
                viewModel.updateExpenseWithPayments(
                    expenseId = expenseId,
                    title = title.trim(),
                    totalAmount = totalAmount,
                    description = description.trim(),
                    photoUri = photoUrisString,
                    payments = payments.mapNotNull { payment ->
                        payment.person?.let { person ->
                            val amount = payment.amount.toDoubleOrNull() ?: return@mapNotNull null
                            Triple(person.id, amount, payment.method)
                        }
                    },
                    expenseUsers = expenseUsers.mapNotNull { eu ->
                        eu.person?.let { person ->
                            val amount = eu.amount.toDoubleOrNull() ?: return@mapNotNull null
                            Triple(person.id, amount, eu.description)
                        }
                    },
                    createdAt = createdAt
                )
            }
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId == null) "소비 추가" else "소비 수정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = isValid) {
                        Text("저장")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                radius = 16.dp,
                                spread = 0.dp,
                                offset = DpOffset(0.dp, 8.dp)
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val formattedAmount = if (totalAmount % 1.0 == 0.0) {
                            totalAmount.toInt().toString()
                        } else {
                            String.format("%.2f", totalAmount).trimEnd('0').trimEnd('.')
                        }
                        val baseText = "총 금액: $formattedAmount$currencySymbol"
                        val displayText = if (showConversion) {
                            val converted = viewModel.convertToStandardCurrency(totalAmount, currentCurrency)
                            if (converted != null) {
                                "$baseText (${String.format("%,.0f", converted)}$standardCurrencySymbol)"
                            } else baseText
                        } else baseText
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            item {
                Text(
                    text = "결제 내역",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            if (persons.isEmpty()) {
                item {
                    Text(
                        text = "먼저 사람을 추가해주세요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                itemsIndexed(payments) { index, payment ->
                    PaymentEntryCard(
                        payment = payment,
                        persons = persons,
                        onPaymentChange = { newPayment ->
                            payments = payments.toMutableList().apply {
                                this[index] = newPayment
                            }
                        },
                        onDelete = {
                            if (payments.size > 1) {
                                payments = payments.toMutableList().apply {
                                    removeAt(index)
                                }
                            }
                        },
                        canDelete = payments.size > 1,
                        currencySymbol = currencySymbol
                    )
                }
                
                item {
                    OutlinedButton(
                        onClick = {
                            payments = payments + PaymentEntry(null, "", PaymentMethod.CASH)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("결제자 추가")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "사용자",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = copyFromPayments,
                            ) {
                                Text("결제내역 그대로")
                            }
                            Button(
                                onClick = divideEvenlyAmount,
                            ) {
                                Text("1/n 정산")
                            }
                        }
                    }
                }

                itemsIndexed(expenseUsers) { index, expenseUser ->
                    ExpenseUserEntryCard(
                        expenseUser = expenseUser,
                        persons = persons,
                        onExpenseUserChange = { newExpenseUser ->
                            expenseUsers = expenseUsers.toMutableList().apply {
                                this[index] = newExpenseUser
                            }
                        },
                        onDelete = {
                            if (expenseUsers.size > 1) {
                                expenseUsers = expenseUsers.toMutableList().apply {
                                    removeAt(index)
                                }
                            }
                        },
                        canDelete = expenseUsers.size > 1,
                        currencySymbol = currencySymbol
                    )
                }

                item {
                    OutlinedButton(
                        onClick = {
                            expenseUsers = expenseUsers + ExpenseUserEntry(null, "", "")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("사용자 추가")
                    }
                }

                item {
                    if (totalUserAmount > 0 && kotlin.math.abs(totalAmount - totalUserAmount) > 0.01) {
                        val formattedTotalAmount = if (totalAmount % 1.0 == 0.0) {
                            totalAmount.toInt().toString()
                        } else {
                            String.format("%.2f", totalAmount).trimEnd('0').trimEnd('.')
                        }
                        val formattedTotalUserAmount = if (totalUserAmount % 1.0 == 0.0) {
                            totalUserAmount.toInt().toString()
                        } else {
                            String.format("%.2f", totalUserAmount).trimEnd('0').trimEnd('.')
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ 결제 금액($formattedTotalAmount$currencySymbol)과 사용자 합계($formattedTotalUserAmount$currencySymbol)가 다릅니다",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "영수증 사진",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            tempPhotoUri = createImageFile()
                            cameraLauncher.launch(tempPhotoUri!!)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("카메라")
                    }
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("갤러리")
                    }
                }
            }
            
            if (photoUris.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(photoUris) { index, uri ->
                            Card(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(200.dp)
                            ) {
                                Box {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "영수증 사진 ${index + 1}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { selectedImageUri = uri },
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = {
                                            photoUris = photoUris.toMutableList().apply {
                                                removeAt(index)
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "사진 삭제",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    singleLine = false
                )
            }

            // Date and Time picker field
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "사용 일시",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
                val formattedDateTime = dateFormat.format(Date(createdAt))
                
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
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "사용 일시",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedDateTime,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    // DatePicker Dialog
    if (showDatePicker) {
        val calendar = remember(createdAt) {
            Calendar.getInstance().apply {
                timeInMillis = createdAt
            }
        }
        // Get date at midnight for DatePicker initialization
        val dateAtMidnight = remember(createdAt) {
            calendar.clone().let { cal ->
                (cal as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
        
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateAtMidnight
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate ->
                        // Preserve current time when changing date
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        val minute = calendar.get(Calendar.MINUTE)
                        
                        calendar.timeInMillis = selectedDate
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        createdAt = calendar.timeInMillis
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("다음")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // TimePicker Dialog
    if (showTimePicker) {
        val calendar = remember(createdAt) {
            Calendar.getInstance().apply {
                timeInMillis = createdAt
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Update time while preserving date, and reset seconds/milliseconds
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(Calendar.MINUTE, timePickerState.minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    createdAt = calendar.timeInMillis
                    showTimePicker = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("취소")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
    
    // Show image viewer dialog when an image is selected
    selectedImageUri?.let { uri ->
        ImageViewerDialog(
            imageUri = uri,
            onDismiss = { selectedImageUri = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEntryCard(
    payment: PaymentEntry,
    persons: List<Person>,
    onPaymentChange: (PaymentEntry) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    currencySymbol: String
) {
    var personExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("결제 정보", style = MaterialTheme.typography.titleSmall)
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            ExposedDropdownMenuBox(
                expanded = personExpanded,
                onExpandedChange = { personExpanded = !personExpanded }
            ) {
                OutlinedTextField(
                    value = payment.person?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("결제자") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = personExpanded,
                    onDismissRequest = { personExpanded = false }
                ) {
                    persons.forEach { person ->
                        DropdownMenuItem(
                            text = { Text(person.name) },
                            onClick = {
                                onPaymentChange(payment.copy(person = person))
                                personExpanded = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = payment.amount,
                onValueChange = { onPaymentChange(payment.copy(amount = it.filter { c -> c.isDigit() || c == '.' })) },
                label = { Text("금액") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text(currencySymbol) }
            )
            
            ExposedDropdownMenuBox(
                expanded = methodExpanded,
                onExpandedChange = { methodExpanded = !methodExpanded }
            ) {
                OutlinedTextField(
                    value = if (payment.method == PaymentMethod.CASH) "현금" else "카드",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("결제 수단") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = methodExpanded,
                    onDismissRequest = { methodExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("현금") },
                        onClick = {
                            onPaymentChange(payment.copy(method = PaymentMethod.CASH))
                            methodExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("카드") },
                        onClick = {
                            onPaymentChange(payment.copy(method = PaymentMethod.CARD))
                            methodExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseUserEntryCard(
    expenseUser: ExpenseUserEntry,
    persons: List<Person>,
    onExpenseUserChange: (ExpenseUserEntry) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    currencySymbol: String
) {
    var personExpanded by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("사용자 정보", style = MaterialTheme.typography.titleSmall)
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = personExpanded,
                onExpandedChange = { personExpanded = !personExpanded }
            ) {
                OutlinedTextField(
                    value = expenseUser.person?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("사용자") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = personExpanded,
                    onDismissRequest = { personExpanded = false }
                ) {
                    persons.forEach { person ->
                        DropdownMenuItem(
                            text = { Text(person.name) },
                            onClick = {
                                onExpenseUserChange(expenseUser.copy(person = person))
                                personExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = expenseUser.amount,
                onValueChange = { onExpenseUserChange(expenseUser.copy(amount = it.filter { c -> c.isDigit() || c == '.' })) },
                label = { Text("금액") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text(currencySymbol) }
            )

            OutlinedTextField(
                value = expenseUser.description,
                onValueChange = { onExpenseUserChange(expenseUser.copy(description = it)) },
                label = { Text("메모 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
