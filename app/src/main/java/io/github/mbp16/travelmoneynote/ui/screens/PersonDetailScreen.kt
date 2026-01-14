package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.CashEntry
import java.text.SimpleDateFormat
import java.util.*

data class TransactionItem(
    val id: Long,
    val amount: Double,
    val isPositive: Boolean,
    val description: String,
    val type: String,
    val createdAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    viewModel: MainViewModel,
    personId: Long,
    onNavigateBack: () -> Unit
) {
    val personsWithBalance by viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val personWithBalance = personsWithBalance.find { it.person.id == personId }
    val transactions by viewModel.getTransactionsForPerson(personId).collectAsState(initial = emptyList())
    val settlements by viewModel.getSettlementsForTravel().collectAsState(initial = emptyList())
    val currentCurrency by viewModel.currentCurrency.collectAsState()
    val standardCurrency by viewModel.standardCurrency.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()
    
    val currencySymbol = availableCurrencies.find { it.code == currentCurrency }?.symbol ?: "₩"
    val standardCurrencySymbol = availableCurrencies.find { it.code == standardCurrency }?.symbol ?: "₩"
    val showConversion = currentCurrency != standardCurrency && exchangeRates != null
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    // 이 사람이 받아야 할 금액 (다른 사람들이 이 사람에게)
    val toReceive = settlements.filter { it.toPersonId == personId }
    // 이 사람이 갚아야 할 금액 (이 사람이 다른 사람에게)
    val toPay = settlements.filter { it.fromPersonId == personId }
    
    fun formatWithConversion(amount: Double): String {
        val base = "${String.format("%,.0f", amount)}$currencySymbol"
        if (!showConversion) return base
        val converted = viewModel.convertToStandardCurrency(amount, currentCurrency)
        return if (converted != null) {
            "$base (${String.format("%,.0f", converted)}$standardCurrencySymbol)"
        } else base
    }
    
    var transactionToDelete by remember { mutableStateOf<TransactionItem?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionItem?>(null) }

    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("삭제 확인") },
            text = { Text("정말 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionToDelete?.let { t ->
                            viewModel.deleteCashEntry(
                                CashEntry(
                                    id = t.id,
                                    personId = personId,
                                    amount = t.amount,
                                    description = t.description,
                                    createdAt = t.createdAt
                                )
                            )
                        }
                        transactionToDelete = null
                    }
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("취소") }
            }
        )
    }

    if (transactionToEdit != null) {
        val t = transactionToEdit!!
        var amount by remember(t) { 
            mutableStateOf(if (t.amount % 1.0 == 0.0) String.format("%.0f", t.amount) else t.amount.toString()) 
        }
        var description by remember(t) { mutableStateOf(t.description) }

        AlertDialog(
            onDismissRequest = { transactionToEdit = null },
            title = { Text("현금 추가 수정") },
            text = {
                Column {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                        label = { Text("금액") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("내용") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newAmount = amount.toDoubleOrNull()
                        if (newAmount != null) {
                            viewModel.updateCashEntry(
                                CashEntry(
                                    id = t.id,
                                    personId = personId,
                                    amount = newAmount,
                                    description = description,
                                    createdAt = t.createdAt
                                )
                            )
                            transactionToEdit = null
                        }
                    }
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToEdit = null }) { Text("취소") }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personWithBalance?.person?.name ?: "상세 정보") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            personWithBalance?.let { pwb ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "잔액 현황",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("보유 현금")
                                Text(formatWithConversion(pwb.totalCash))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("현금 사용")
                                Text(
                                    "-${formatWithConversion(pwb.cashSpent)}",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("카드 사용")
                                Text(
                                    "-${formatWithConversion(pwb.cardSpent)}",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "남은 현금",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    formatWithConversion(pwb.remainingCash),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (pwb.remainingCash < 0)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 정산 정보 섹션
            if (toReceive.isNotEmpty() || toPay.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "정산 정보",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (toReceive.isNotEmpty()) {
                                Text(
                                    text = "받을 금액",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                toReceive.forEach { settlement ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${settlement.fromPersonName}에게서")
                                        Text(
                                            "+${formatWithConversion(settlement.amount)}",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (toReceive.isNotEmpty() && toPay.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            if (toPay.isNotEmpty()) {
                                Text(
                                    text = "갚아야 할 금액",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                toPay.forEach { settlement ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${settlement.toPersonName}에게")
                                        Text(
                                            "-${formatWithConversion(settlement.amount)}",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "자산 변동 내역",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            if (transactions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "변동 내역이 없습니다",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(transactions) { transaction ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = transaction.description.ifEmpty { transaction.type },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "${transaction.type} • ${dateFormat.format(Date(transaction.createdAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${if (transaction.isPositive) "+" else "-"}${formatWithConversion(transaction.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (transaction.isPositive)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                            }
                            if (transaction.type == "현금 추가") {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { transactionToEdit = transaction }) {
                                        Text("수정")
                                    }
                                    TextButton(onClick = { transactionToDelete = transaction }) {
                                        Text("삭제", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
