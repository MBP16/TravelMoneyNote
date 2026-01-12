package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.data.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val persons by viewModel.persons.collectAsState()
    val currentCurrency by viewModel.currentCurrency.collectAsState()
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val currencySymbol = availableCurrencies.find { it.code == currentCurrency }?.symbol ?: "₩"
    
    LaunchedEffect(persons) {
        if (selectedPerson == null && persons.isNotEmpty()) {
            selectedPerson = persons.first()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "현금 추가",
            style = MaterialTheme.typography.titleLarge
        )

        if (persons.isEmpty()) {
            Text(
                text = "먼저 사람을 추가해주세요",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedPerson?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("사람 선택") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    persons.forEach { person ->
                        DropdownMenuItem(
                            text = { Text(person.name) },
                            onClick = {
                                selectedPerson = person
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("금액") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text(currencySymbol) }
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("설명 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (selectedPerson != null && amountValue != null && amountValue > 0) {
                        viewModel.addCashEntry(
                            personId = selectedPerson!!.id,
                            amount = amountValue,
                            description = description.trim()
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPerson != null && amount.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text("추가")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
