package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.meucontrole.financeiro.data.SecurityStore

@Composable
fun LockScreen(security: SecurityStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun tryUnlock() {
        if (security.verify(pin)) onUnlocked()
        else {
            pin = ""
            error = "PIN incorreto (${security.failedAttempts} tentativa(s))."
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Meu Controle Financeiro", style = MaterialTheme.typography.titleLarge)
            Text("Digite seu PIN para continuar", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) { pin = v; error = null } },
                label = { Text("PIN") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { msg -> @Composable { Text(msg) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { tryUnlock() }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Desbloquear") }
            Spacer(Modifier.height(24.dp))
            Text(
                "Esqueceu o PIN? Ele não pode ser recuperado. Será preciso limpar os dados do app nas configurações do Android e restaurar um backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
