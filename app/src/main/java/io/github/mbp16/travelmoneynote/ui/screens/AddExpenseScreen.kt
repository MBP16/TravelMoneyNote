package io.github.mbp16.travelmoneynote.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.PaymentMethod
import io.github.mbp16.travelmoneynote.data.Person
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class PaymentEntry(
    val person: Person?,
    val amount: String,
    val method: PaymentMethod
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val persons by viewModel.persons.collectAsState()
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var payments by remember { mutableStateOf(listOf(PaymentEntry(null, "", PaymentMethod.CASH))) }
    
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
            photoUri = Uri.fromFile(imageFile)
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            photoUri = null
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
    val isValid = payments.all { it.person != null && (it.amount.toDoubleOrNull() ?: 0.0) > 0 } && payments.isNotEmpty()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("소비 추가") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("설명") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "총 금액: ${String.format("%,.0f", totalAmount)}원",
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
                        canDelete = payments.size > 1
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
                            photoUri = createImageFile()
                            cameraLauncher.launch(photoUri!!)
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
            
            if (photoUri != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box {
                            Image(
                                painter = rememberAsyncImagePainter(photoUri),
                                contentDescription = "영수증 사진",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { photoUri = null },
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
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (isValid) {
                            viewModel.addExpenseWithPayments(
                                totalAmount = totalAmount,
                                description = description.trim(),
                                photoUri = photoUri?.toString(),
                                payments = payments.mapNotNull { payment ->
                                    payment.person?.let { person ->
                                        val amount = payment.amount.toDoubleOrNull() ?: return@mapNotNull null
                                        Triple(person.id, amount, payment.method)
                                    }
                                }
                            )
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isValid
                ) {
                    Text("저장")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEntryCard(
    payment: PaymentEntry,
    persons: List<Person>,
    onPaymentChange: (PaymentEntry) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    var personExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
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
                suffix = { Text("원") }
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
