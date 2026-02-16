package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel
import io.github.mbp16.travelmoneynote.R
import io.github.mbp16.travelmoneynote.data.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashScreen(
    person: Person,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val currentCurrency by viewModel.currentCurrency.collectAsState()
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val currencySymbol = availableCurrencies.find { it.code == currentCurrency }?.symbol ?: "₩"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.personasset_cashadd_title),
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(stringResource(R.string.amount)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            suffix = { Text(currencySymbol) }
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.memo)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val amountValue = amount.toDoubleOrNull()
                amountValue?.let {
                    viewModel.addCashEntry(
                        personId = person.id,
                        amount = it,
                        description = description.trim()
                    )
                }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true
        ) {
            Text(stringResource(R.string.add))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
