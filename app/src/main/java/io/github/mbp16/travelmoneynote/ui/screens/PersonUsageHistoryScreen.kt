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
    val 사람목록흐름 = viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val 현재사람정보 = 사람목록흐름.value.find { it.person.id == personId }
    val 소비기록들 = viewModel.getUsageHistoryForPerson(personId).collectAsState(initial = emptyList())
    val 여행통화 by viewModel.currentCurrency.collectAsState()
    val 표준통화 by viewModel.standardCurrency.collectAsState()
    val 환율데이터 by viewModel.exchangeRates.collectAsState()
    
    val 통화표시문자 = availableCurrencies.find { it.code == 여행통화 }?.symbol ?: "₩"
    val 표준통화표시 = availableCurrencies.find { it.code == 표준통화 }?.symbol ?: "₩"
    val 환율표시활성화 = 여행통화 != 표준통화 && 환율데이터 != null
    val 날짜표시형식 = remember { SimpleDateFormat("yy.MM.dd HH:mm", Locale.getDefault()) }

    fun 금액을문자열로(금액값: Double): String {
        val 정리된금액 = if (금액값 % 1.0 == 0.0) {
            금액값.toInt().toString()
        } else {
            String.format("%.2f", 금액값).trimEnd('0').trimEnd('.')
        }
        val 기본문자열 = "$정리된금액$통화표시문자"
        if (!환율표시활성화) return 기본문자열
        val 변환금액 = viewModel.convertToStandardCurrency(금액값, 여행통화)
        return if (변환금액 != null) {
            "$기본문자열 (${String.format("%,.0f", 변환금액)}$표준통화표시)"
        } else 기본문자열
    }

    val 총사용금액 = 소비기록들.value.sumOf { it.amount }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${현재사람정보?.person?.name ?: "알수없음"}의 소비 내역") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { 패딩설정 ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(패딩설정)
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
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = "${소비기록들.value.size}건의 사용 기록",
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
                                text = 금액을문자열로(총사용금액),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            if (소비기록들.value.isEmpty()) {
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
                items(소비기록들.value) { 소비항목 ->
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
                                        text = 소비항목.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (소비항목.description.isNotEmpty()) {
                                        Text(
                                            text = 소비항목.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                </Column>
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = 금액을문자열로(소비항목.amount),
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
                                        소비항목.payerNames.take(3).forEach { 결제자명 ->
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
                                                    text = 결제자명,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (소비항목.payerNames.size > 3) {
                                            Text(
                                                text = "+${소비항목.payerNames.size - 3}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                </Column>
                                
                                Text(
                                    text = 날짜표시형식.format(Date(소비항목.createdAt)),
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
