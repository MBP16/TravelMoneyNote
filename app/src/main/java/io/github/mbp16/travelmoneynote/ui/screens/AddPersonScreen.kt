package io.github.mbp16.travelmoneynote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mbp16.travelmoneynote.MainViewModel

@Composable
fun AddPersonScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(), // Handle safe area
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "사람 추가",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("이름") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (name.isNotBlank()) {
                        viewModel.addPerson(name.trim())
                        onDismiss()
                    }
                }
            )
        )
        
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    viewModel.addPerson(name.trim())
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank()
        ) {
            Text("추가")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
