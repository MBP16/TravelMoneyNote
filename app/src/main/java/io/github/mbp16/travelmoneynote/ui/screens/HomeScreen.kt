package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.PersonWithBalance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToAddPerson: () -> Unit,
    onNavigateToAddCash: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPersonDetail: (Long) -> Unit,
    onNavigateToEditExpense: (Long) -> Unit
) {
    val personsWithBalance by viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val expenses by viewModel.expenses.collectAsState()
    val currentCurrency by viewModel.currentCurrency.collectAsState()
    val selectedTravelId by viewModel.selectedTravelId.collectAsState()
    val travels by viewModel.travels.collectAsState()
    
    val currencySymbol = availableCurrencies.find { it.code == currentCurrency }?.symbol ?: "₩"
    val selectedTravel = travels.find { it.id == selectedTravelId }
    
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
                Text(
                    text = "인원 현황",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            if (personsWithBalance.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
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
                        onDelete = { viewModel.deletePerson(personWithBalance.person) },
                        onEdit = { newName -> 
                            viewModel.updatePerson(personWithBalance.person.copy(name = newName))
                        },
                        onClick = { onNavigateToPersonDetail(personWithBalance.person.id) }
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToAddPerson,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("사람 추가")
                    }
                    Button(
                        onClick = onNavigateToAddCash,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("현금 추가")
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "소비 내역이 없습니다",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(expenses) { expense ->
                    ExpenseCard(
                        expense = expense,
                        viewModel = viewModel,
                        currencySymbol = currencySymbol,
                        onClick = { onNavigateToEditExpense(expense.id) }
                    )
                }
            }
        }
        }
    }
}

@Composable
fun PersonBalanceCard(
    personWithBalance: PersonWithBalance,
    currencySymbol: String,
    onDelete: () -> Unit,
    onEdit: (String) -> Unit,
    onClick: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(personWithBalance.person.name) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                    text = "보유 현금: ${String.format("%,.0f", personWithBalance.totalCash)}$currencySymbol",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "현금 사용: ${String.format("%,.0f", personWithBalance.cashSpent)}$currencySymbol",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "카드 사용: ${String.format("%,.0f", personWithBalance.cardSpent)}$currencySymbol",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "남은 현금: ${String.format("%,.0f", personWithBalance.remainingCash)}$currencySymbol",
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
                IconButton(onClick = onDelete) {
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
}

@Composable
fun ExpenseCard(
    expense: io.github.mbp16.travelmoneynote.data.Expense,
    viewModel: MainViewModel,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val expenseWithPayments by viewModel.getExpenseWithPayments(expense.id).collectAsState(initial = null)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = expense.description.ifEmpty { "소비" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${String.format("%,.0f", expense.totalAmount)}$currencySymbol",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            expenseWithPayments?.payments?.forEach { paymentWithPerson ->
                Text(
                    text = "${paymentWithPerson.personName}: ${String.format("%,.0f", paymentWithPerson.payment.amount)}$currencySymbol (${if (paymentWithPerson.payment.method == io.github.mbp16.travelmoneynote.data.PaymentMethod.CASH) "현금" else "카드"})",
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
    }
}
