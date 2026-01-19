package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.PersonWithBalance
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToAddExpense: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPersonDetail: (Long) -> Unit,
    onNavigateToEditExpense: (Long) -> Unit
) {
    val personsWithBalance by viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val expenses by viewModel.expenses.collectAsState()
    val currentCurrency by viewModel.currentCurrency.collectAsState()
    val standardCurrency by viewModel.standardCurrency.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()
    val selectedTravelId by viewModel.selectedTravelId.collectAsState()
    val travels by viewModel.travels.collectAsState()
    
    val currencySymbol = availableCurrencies.find { it.code == currentCurrency }?.symbol ?: "₩"
    val standardCurrencySymbol = availableCurrencies.find { it.code == standardCurrency }?.symbol ?: "₩"
    val showConversion = currentCurrency != standardCurrency && exchangeRates != null
    val selectedTravel = travels.find { it.id == selectedTravelId }

    var showAddPersonSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val groupedExpenses = remember(expenses) {
        expenses.groupBy { formatDate(it.createdAt) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("여행 가계부")
                        if (selectedTravel != null) {
                            Text(
                                text = selectedTravel.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSettings
            ) {
                Icon(Icons.Default.Settings, contentDescription = "설정")
            }
        }
    ) { paddingValues ->
        if (selectedTravelId <= 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "여행을 선택해주세요",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "설정에서 여행을 추가하고 선택할 수 있습니다",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("설정으로 이동")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "인원 현황",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Button(
                            onClick = { showAddPersonSheet = true },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("사람 추가")
                        }
                    }
                }
                
                if (personsWithBalance.isEmpty()) {
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
                                text = "등록된 인원이 없습니다",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(personsWithBalance) { personWithBalance ->
                        PersonBalanceCard(
                            personWithBalance = personWithBalance,
                            currencySymbol = currencySymbol,
                            standardCurrencySymbol = standardCurrencySymbol,
                            showConversion = showConversion,
                            convertToStandard = { amount -> viewModel.convertToStandardCurrency(amount, currentCurrency) },
                            onDelete = { viewModel.deletePerson(personWithBalance.person) },
                            onEdit = { newName -> 
                                viewModel.updatePerson(personWithBalance.person.copy(name = newName))
                            },
                            onClick = { onNavigateToPersonDetail(personWithBalance.person.id) }
                        )
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "소비 내역",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Button(
                            onClick = onNavigateToAddExpense,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("소비 추가")
                        }
                    }
                }
                
                if (expenses.isEmpty()) {
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
                                text = "소비 내역이 없습니다",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    groupedExpenses.forEach { (date, dailyExpenses) ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(dailyExpenses) { expense ->
                            ExpenseCard(
                                expense = expense,
                                viewModel = viewModel,
                                currencySymbol = currencySymbol,
                                standardCurrencySymbol = standardCurrencySymbol,
                                showConversion = showConversion,
                                convertToStandard = { amount -> viewModel.convertToStandardCurrency(amount, currentCurrency) },
                                onClick = { onNavigateToEditExpense(expense.id) },
                                onDelete = { viewModel.deleteExpense(expense) }
                            )
                        }
                    }
                }
            }
        }

        if (showAddPersonSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddPersonSheet = false },
                sheetState = sheetState
            ) {
                AddPersonScreen(
                    viewModel = viewModel,
                    onDismiss = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showAddPersonSheet = false
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PersonBalanceCard(
    personWithBalance: PersonWithBalance,
    currencySymbol: String,
    standardCurrencySymbol: String = currencySymbol,
    showConversion: Boolean = false,
    convertToStandard: (Double) -> Double? = { null },
    onDelete: () -> Unit,
    onEdit: (String) -> Unit,
    onClick: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(personWithBalance.person.name) }
    
    fun formatWithConversion(amount: Double): String {
        val base = "${if (amount % 1.0 == 0.0) amount.toInt() else amount}$currencySymbol"
        if (!showConversion) return base
        val converted = convertToStandard(amount)
        return if (converted != null) {
            "$base (${String.format("%,.0f", converted)}$standardCurrencySymbol)"
        } else base
    }
    
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
            .clickable { onClick() }
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
                    text = personWithBalance.person.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "보유 현금: ${formatWithConversion(personWithBalance.totalCash)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "현금 사용: ${formatWithConversion(personWithBalance.cashSpent)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "카드 사용: ${formatWithConversion(personWithBalance.cardSpent)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "남은 현금: ${formatWithConversion(personWithBalance.remainingCash)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (personWithBalance.remainingCash < 0) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            }
            Row {
                IconButton(onClick = { 
                    editName = personWithBalance.person.name
                    showEditDialog = true 
                }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "수정",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("이름 수정") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onEdit(editName.trim())
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("사람 삭제") },
            text = { Text("정말로 ${personWithBalance.person.name}님을 삭제하시겠습니까?\n관련된 모든 기록이 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun ExpenseCard(
    expense: io.github.mbp16.travelmoneynote.data.Expense,
    viewModel: MainViewModel,
    currencySymbol: String,
    standardCurrencySymbol: String = currencySymbol,
    showConversion: Boolean = false,
    convertToStandard: (Double) -> Double? = { null },
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val expenseWithPayments by viewModel.getExpenseWithPayments(expense.id).collectAsState(initial = null)
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    fun formatWithConversion(amount: Double): String {
        val base = "${if (amount % 1.0 == 0.0) amount.toInt() else amount}$currencySymbol"
        if (!showConversion) return base
        val converted = convertToStandard(amount)
        return if (converted != null) {
            "$base (${String.format("%,.0f", converted)}$standardCurrencySymbol)"
        } else base
    }
    
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
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = expense.title.ifBlank { "소비" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTime(expense.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatWithConversion(expense.totalAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                expenseWithPayments?.payments?.forEach { paymentWithPerson ->
                    Text(
                        text = "${paymentWithPerson.personName}: ${formatWithConversion(paymentWithPerson.payment.amount)} (${if (paymentWithPerson.payment.method == io.github.mbp16.travelmoneynote.data.PaymentMethod.CASH) "현금" else "카드"})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (expense.photoUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📷 사진 첨부됨",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("소비 내역 삭제") },
            text = { Text("정말로 이 소비 내역을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy년 MM월 dd일 EEEE", Locale.KOREA)
    return sdf.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("a h:mm", Locale.KOREA)
    return sdf.format(Date(timestamp))
}
