package br.com.meucontrole.financeiro.presentation.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.data.SecurityStore
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.ThemeMode
import br.com.meucontrole.financeiro.notifications.NotificationHelper
import br.com.meucontrole.financeiro.notifications.ReminderScheduler
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.DropdownField
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = ui?.data?.settings

    var loaded by remember { mutableStateOf(false) }
    var openingText by remember { mutableStateOf("") }
    var openingDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var hourText by remember { mutableStateOf("8") }
    var canNotify by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var pinDialog by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(vm.security.hasPin) }

    LaunchedEffect(settings != null) {
        val s = settings ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        openingText = if (s.openingDate != null || s.openingBalance != 0L) Money.formatPlain(s.openingBalance) else ""
        openingDate = s.openingDate ?: LocalDate.now()
        hourText = s.reminderHour.toString()
        loaded = true
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        vm.message(if (granted) "Notificações permitidas." else "Sem permissão, os lembretes não serão exibidos.")
    }

    AppScaffold(title = "Configurações", onBack = { nav.popBackStack() }) { padding ->
        val s = settings ?: run { Loading(); return@AppScaffold }
        Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Saldo inicial")
            Text("Quanto você tinha em conta e em dinheiro na data abaixo. A partir dela, o app soma o que você recebe e subtrai o que paga.",
                style = MaterialTheme.typography.bodySmall)
            MoneyField("Saldo em conta/dinheiro", openingText, { openingText = it }, required = false)
            DateField("Data do saldo", openingDate, { openingDate = it })
            Button(onClick = {
                val v = if (openingText.isBlank()) 0L else Money.parse(openingText)
                if (v == null) { vm.message("Valor inválido."); return@Button }
                vm.saveSettings(s.copy(openingBalance = v, openingDate = openingDate)) { vm.message("Saldo inicial salvo.") }
            }) { Text("Salvar saldo inicial") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Lembretes de vencimento")
            SwitchRow("Avisar 1 dia antes", s.remindDayBefore) { vm.saveSettings(s.copy(remindDayBefore = it)) }
            SwitchRow("Avisar no dia do vencimento", s.remindSameDay) { vm.saveSettings(s.copy(remindSameDay = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(hourText, { v -> hourText = v.filter { it.isDigit() }.take(2) }, label = { Text("Horário aproximado (0–23 h)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val h = hourText.toIntOrNull()
                    if (h == null || h !in 0..23) { vm.message("Informe uma hora de 0 a 23."); return@TextButton }
                    vm.saveSettings(s.copy(reminderHour = h)) {
                        ReminderScheduler.schedule(context, h, replace = true)
                        vm.message("Horário dos lembretes atualizado.")
                    }
                }) { Text("Aplicar") }
            }
            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Permitir notificações") }
            }
            InfoCard {
                Text(
                    "Os lembretes são gerados no próprio aparelho, sem internet. O Android pode atrasar ou agrupar a verificação diária conforme a economia de bateria, " +
                        "o modo soneca e as permissões do app — por isso o horário é aproximado. Para mais precisão, desative a otimização de bateria para este app nas configurações do Android.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Segurança")
            Text(if (hasPin) "Bloqueio por PIN ativado." else "Bloqueio por PIN desativado.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pinDialog = true }) { Text(if (hasPin) "Alterar PIN" else "Ativar PIN") }
                if (hasPin) OutlinedButton(onClick = { vm.security.clearPin(); hasPin = false; vm.message("PIN removido.") }) { Text("Remover PIN") }
            }
            Text("O app não armazena senhas bancárias e não envia dados a terceiros. Não há anúncios, rastreadores ou analytics.",
                style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Aparência")
            DropdownField("Tema", ThemeMode.entries, s.themeMode, { it.label }, { vm.saveSettings(s.copy(themeMode = it)) })
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pinDialog) PinDialog(vm.security, onDone = { hasPin = vm.security.hasPin; pinDialog = false; vm.message("PIN definido.") }) { pinDialog = false }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PinDialog(security: SecurityStore, onDone: () -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val needsCurrent = security.hasPin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (needsCurrent) "Alterar PIN" else "Definir PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsCurrent) PinField("PIN atual", current) { current = it }
                PinField("Novo PIN (4 a 8 dígitos)", pin) { pin = it }
                PinField("Confirmar novo PIN", confirm) { confirm = it }
                Text("Guarde bem: o PIN não pode ser recuperado.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    needsCurrent && !security.verify(current) -> "PIN atual incorreto."
                    !SecurityStore.isValidPin(pin) -> "O PIN deve ter de 4 a 8 dígitos."
                    pin != confirm -> "Os PINs não conferem."
                    else -> null
                }
                if (error == null && security.setPin(pin)) onDone()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PinField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> if (v.length <= 8 && v.all { it.isDigit() }) onValue(v) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ChangeLogScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    AppScaffold(title = "Histórico de alterações", onBack = { nav.popBackStack() }) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val names = remember(u) {
            u.data.debts.associate { ("debt" to it.id) to it.name } +
                u.data.billTemplates.associate { ("bill" to it.id) to it.name } +
                u.data.incomeSources.associate { ("income" to it.id) to it.name }
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (u.data.changeLog.isEmpty()) item { Text("Nenhuma alteração registrada ainda.") }
            items(u.data.changeLog, key = { it.id }) { e ->
                val ts = Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${Fmt.date(ts.toLocalDate())} ${"%02d:%02d".format(ts.hour, ts.minute)}", style = MaterialTheme.typography.labelSmall)
                    val owner = names[e.entityType to e.entityId]
                    if (owner != null) Text(owner, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(e.description, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
    }
}
