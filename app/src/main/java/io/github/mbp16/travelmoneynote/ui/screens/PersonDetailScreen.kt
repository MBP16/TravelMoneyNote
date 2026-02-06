package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.CashEntry
import kotlinx.coroutines.launch
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
    onNavigateBack: () -> Unit,
    onNavigateToAssetHistory: (Long) -> Unit,
    onNavigateToUsageHistory: (Long) -> Unit
) {
    val personsWithBalance by viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val personWithBalance = personsWithBalance.find { it.person.id == personId }
    val transactions by viewModel.getTransactionsForPerson(personId).collectAsState(initial = emptyList())
    val usageHistory by viewModel.getUsageHistoryForPerson(personId).collectAsState(initial = emptyList())
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
        val formattedAmount = if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format("%.2f", amount).trimEnd('0').trimEnd('.')
        }
        val base = "$formattedAmount$currencySymbol"
        if (!showConversion) return base
        val converted = viewModel.convertToStandardCurrency(amount, currentCurrency)
        return if (converted != null) {
            "$base (${String.format("%,.0f", converted)}$standardCurrencySymbol)"
        } else base
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
                    text = "활동 요약",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                radius = 10.dp,
                                spread = 1.dp,
                                offset = DpOffset(0.dp, 5.dp)
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "💰",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "자산 변동",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (transactions.isEmpty()) {
                                    Text(
                                        text = "아직 변동 없음",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                } else {
                                    val lastTransaction = transactions.firstOrNull()
                                    Text(
                                        text = "${transactions.size}건 • ${
                                            lastTransaction?.let { dateFormat.format(Date(it.createdAt)) } ?: ""
                                        }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { onNavigateToAssetHistory(personId) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("보기")
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                radius = 10.dp,
                                spread = 1.dp,
                                offset = DpOffset(0.dp, 5.dp)
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🛍️",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "소비 내역",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (usageHistory.isEmpty()) {
                                    Text(
                                        text = "아직 사용 없음",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                } else {
                                    val totalUsage = usageHistory.sumOf { it.amount }
                                    Text(
                                        text = "${usageHistory.size}건 • 총 ${formatWithConversion(totalUsage)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { onNavigateToUsageHistory(personId) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("보기")
                        }
                    }
                }
            }
        }
    }
}
