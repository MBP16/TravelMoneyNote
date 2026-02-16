package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.R
import io.github.mbp16.travelmoneynote.data.CashEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAssetHistoryScreen(
    viewModel: MainViewModel,
    personId: Long,
    onNavigateBack: () -> Unit
) {
    val peopleBalanceFlow = viewModel.getPersonsWithBalance().collectAsState(initial = emptyList())
    val foundPersonData = peopleBalanceFlow.value.find { it.person.id == personId }
    val moneyFlowRecords = viewModel.getTransactionsForPerson(personId).collectAsState(initial = emptyList())
    val activeCurrencyCode by viewModel.currentCurrency.collectAsState()
    val baseCurrencyCode by viewModel.standardCurrency.collectAsState()
    val rateDataMap by viewModel.exchangeRates.collectAsState()
    
    val mainSymbol = availableCurrencies.find { it.code == activeCurrencyCode }?.symbol ?: "₩"
    val baseSymbol = availableCurrencies.find { it.code == baseCurrencyCode }?.symbol ?: "₩"
    val needsConversionDisplay = activeCurrencyCode != baseCurrencyCode && rateDataMap != null
    val timestampFormatter = remember { SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()) }

    var bottomSheetVisible by remember { mutableStateOf(false) }
    val sheetStateObj = rememberModalBottomSheetState()
    val coroutineHandler = rememberCoroutineScope()

    var recordToRemove by remember { mutableStateOf<TransactionItem?>(null) }
    var recordToModify by remember { mutableStateOf<TransactionItem?>(null) }
    var expandedCardIdx by remember { mutableStateOf<Int?>(null) }

    fun formatMoneyWithRate(cashAmount: Double): String {
        val cleanAmount = if (cashAmount % 1.0 == 0.0) {
            cashAmount.toInt().toString()
        } else {
            String.format("%.2f", cashAmount).trimEnd('0').trimEnd('.')
        }
        val primaryText = "$cleanAmount$mainSymbol"
        if (!needsConversionDisplay) return primaryText
        val convertedVal = viewModel.convertToStandardCurrency(cashAmount, activeCurrencyCode)
        return if (convertedVal != null) {
            "$primaryText (${String.format("%,.0f", convertedVal)}$baseSymbol)"
        } else primaryText
    }

    if (bottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { bottomSheetVisible = false },
            sheetState = sheetStateObj
        ) {
            AddCashScreen(
                person = foundPersonData!!.person,
                viewModel = viewModel,
                onDismiss = {
                    coroutineHandler.launch { sheetStateObj.hide() }.invokeOnCompletion {
                        if (!sheetStateObj.isVisible) {
                            bottomSheetVisible = false
                        }
                    }
                }
            )
        }
    }

    if (recordToRemove != null) {
        AlertDialog(
            onDismissRequest = { recordToRemove = null },
            title = { Text(stringResource(R.string.personasset_cashadddelete_title)) },
            text = { Text("${recordToRemove!!.description}\n${formatMoneyWithRate(recordToRemove!!.amount)}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToRemove?.let { rec ->
                            viewModel.deleteCashEntry(
                                CashEntry(
                                    id = rec.id,
                                    personId = personId,
                                    amount = rec.amount,
                                    description = rec.description,
                                    createdAt = rec.createdAt
                                )
                            )
                        }
                        recordToRemove = null
                    }
                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recordToRemove = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (recordToModify != null) {
        val modRec = recordToModify!!
        var amtInput by remember(modRec) { 
            mutableStateOf(if (modRec.amount % 1.0 == 0.0) String.format("%.0f", modRec.amount) else modRec.amount.toString()) 
        }
        var descInput by remember(modRec) { mutableStateOf(modRec.description) }

        AlertDialog(
            onDismissRequest = { recordToModify = null },
            title = { Text(stringResource(R.string.personasset_cashaddedit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amtInput,
                        onValueChange = { if (it.all { ch -> ch.isDigit() || ch == '.' }) amtInput = it },
                        label = { Text(stringResource(R.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text(stringResource(R.string.memo)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedAmt = amtInput.toDoubleOrNull()
                        if (updatedAmt != null) {
                            viewModel.updateCashEntry(
                                CashEntry(
                                    id = modRec.id,
                                    personId = personId,
                                    amount = updatedAmt,
                                    description = descInput,
                                    createdAt = modRec.createdAt
                                )
                            )
                            recordToModify = null
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { recordToModify = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${foundPersonData?.person?.name ?: "알수없음"}의 자산 변동") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { bottomSheetVisible = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "현금 추가")
                Text(" " + stringResource(R.string.personasset_cashadd_title))
            }
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                radius = 20.dp,
                                spread = 2.dp,
                                offset = DpOffset(0.dp, 10.dp)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "총 ${moneyFlowRecords.value.size}건의 변동",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = "누적 현금 추가",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatMoneyWithRate(
                                    moneyFlowRecords.value.filter { it.isPositive }.sumOf { it.amount }
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            }
                        Column {
                            Text(
                                text = "누적 결제",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatMoneyWithRate(
                                    moneyFlowRecords.value.filter { !it.isPositive }.sumOf { it.amount }
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (moneyFlowRecords.value.isEmpty()) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "아직 자산 변동이 없습니다\n우측 하단 버튼으로 현금을 추가하세요",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(moneyFlowRecords.value) { idx, flowRec ->
                    val isExpanded = expandedCardIdx == idx
                    
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (flowRec.isPositive)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dropShadow(
                                shape = RoundedCornerShape(16.dp),
                                shadow = Shadow(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    radius = 12.dp,
                                    spread = 1.dp,
                                    offset = DpOffset(0.dp, 6.dp)
                                )
                            )
                            .clickable { 
                                if (flowRec.type == "cash" && flowRec.isPositive) {
                                    expandedCardIdx = if (isExpanded) null else idx
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (flowRec.isPositive)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (flowRec.isPositive) "+" else "-",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (flowRec.isPositive)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = flowRec.description.ifEmpty { flowRec.type },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = timestampFormatter.format(Date(flowRec.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = (if (flowRec.type == "cash") "💵 " + stringResource(R.string.cash) else "💳 " + stringResource(R.string.card))
                                            + " " + (if (flowRec.isPositive) stringResource(R.string.add) else "결제"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            
                            Text(
                                text = "${if (flowRec.isPositive) "+" else "-"}${formatMoneyWithRate(flowRec.amount)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (flowRec.isPositive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = isExpanded && flowRec.type == "cash" && flowRec.isPositive,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                TextButton(onClick = { recordToModify = flowRec }) {
                                    Text("수정")
                                }
                                TextButton(onClick = { recordToRemove = flowRec }) {
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
