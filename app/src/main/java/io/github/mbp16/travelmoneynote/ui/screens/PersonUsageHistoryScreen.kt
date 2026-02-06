package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonUsageHistoryScreen(
    viewModel: MainViewModel,
    personId: Long,
    onNavigateBack: () -> Unit
) {
    val personListFlow = viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val currentPerson = personListFlow.value.find { it.person.id == personId }
    val expenseHistory = viewModel.getUsageHistoryForPerson(personId).collectAsState(initial = emptyList())
    val travelCurrency by viewModel.currentCurrency.collectAsState()
    val standardCurrency by viewModel.standardCurrency.collectAsState()
    val exchangeRateData by viewModel.exchangeRates.collectAsState()
    
    val currencyDisplay = availableCurrencies.find { it.code == travelCurrency }?.symbol ?: "₩"
    val standardCurrencyDisplay = availableCurrencies.find { it.code == standardCurrency }?.symbol ?: "₩"
    val isExchangeRateDisplayEnabled = travelCurrency != standardCurrency && exchangeRateData != null
    val dateDisplayStyle = remember { SimpleDateFormat("yy.MM.dd HH:mm", Locale.getDefault()) }

    fun formatMoney(money: Double): String {
        val refinedMoney = if (money % 1.0 == 0.0) {
            money.toInt().toString()
        } else {
            String.format("%.2f", money).trimEnd('0').trimEnd('.')
        }
        val defaultText = "$refinedMoney$currencyDisplay"
        if (!isExchangeRateDisplayEnabled) return defaultText
        val exchangedMoney = viewModel.convertToStandardCurrency(money, travelCurrency)
        return if (exchangedMoney != null) {
            "$defaultText (${String.format("%,.0f", exchangedMoney)}$standardCurrencyDisplay)"
        } else defaultText
    }

    val totalMoney = expenseHistory.value.sumOf { it.amount }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${currentPerson?.person?.name ?: "알수없음"}의 소비 내역") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingSettings ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingSettings)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                )
                            )
                        )
                        .dropShadow(
                            shape = RoundedCornerShape(20.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                                radius = 18.dp,
                                spread = 3.dp,
                                offset = DpOffset(0.dp, 8.dp)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "전체 소비 내역",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                text = "${expenseHistory.value.size}건의 사용 기록",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "합계",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatMoney(totalMoney),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            if (expenseHistory.value.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dropShadow(
                                shape = RoundedCornerShape(16.dp),
                                shadow = Shadow(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                    radius = 8.dp,
                                    offset = DpOffset(0.dp, 4.dp)
                                )
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "💸",
                                    style = MaterialTheme.typography.displaySmall
                                )
                                Text(
                                    text = "아직 소비 내역이 없습니다",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(expenseHistory.value) { expenseItem ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dropShadow(
                                shape = RoundedCornerShape(18.dp),
                                shadow = Shadow(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    radius = 14.dp,
                                    spread = 2.dp,
                                    offset = DpOffset(0.dp, 7.dp)
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = expenseItem.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (expenseItem.description.isNotEmpty()) {
                                        Text(
                                            text = expenseItem.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = formatMoney(expenseItem.amount),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.alpha(0.5f),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "결제자",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        expenseItem.payerNames.take(3).forEach { payerName ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                modifier = Modifier.border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            ) {
                                                Text(
                                                    text = payerName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (expenseItem.payerNames.size > 3) {
                                            Text(
                                                text = "+${expenseItem.payerNames.size - 3}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                Text(
                                    text = dateDisplayStyle.format(Date(expenseItem.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
