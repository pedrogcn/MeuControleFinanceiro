package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.data.repository.OpResult
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.Categories
import br.com.meucontrole.financeiro.domain.model.Recurrence
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.ConfirmDialog
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.DropdownField
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.SuggestField
import br.com.meucontrole.financeiro.domain.model.Money
import java.time.LocalDate

@Composable
fun BillFormScreen(vm: MainViewModel, nav: NavController, id: Long) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.bills.first()) }
    var amountText by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(Recurrence.MENSAL) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var active by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui != null) {
        if (loaded) return@LaunchedEffect
        ui?.data?.billTemplates?.firstOrNull { it.id == id }?.let { t ->
            name = t.name; category = t.category; amountText = Money.formatPlain(t.expectedAmount); recurrence = t.recurrence
            start = t.startDate; end = t.endDate; active = t.active; notes = t.notes
        }
        loaded = true
    }

    AppScaffold(
        title = if (id == 0L) "Nova conta" else "Editar conta",
        onBack = { nav.popBackStack() },
        actions = { if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir conta") } }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SuggestField("Categoria", category, { category = it }, Categories.bills)
            MoneyField("Valor previsto", amountText, { amountText = it },
                supporting = "Para contas variáveis (energia, água), use uma estimativa e ajuste cada mês.")
            DropdownField("Recorrência", Recurrence.entries, recurrence, { it.label }, { recurrence = it })
            DateField(if (recurrence == Recurrence.NENHUMA) "Vencimento" else "Primeiro vencimento", start, { if (it != null) start = it })
            if (recurrence != Recurrence.NENHUMA) {
                DateField("Repetir até (opcional)", end, { end = it }, allowClear = true)
                Text(
                    when (recurrence) {
                        Recurrence.MENSAL -> "Vence todo dia ${start.dayOfMonth}. Em meses mais curtos, vence no último dia."
                        Recurrence.SEMANAL -> "Vence toda semana a partir de ${Fmt.date(start)}."
                        Recurrence.ANUAL -> "Vence todo ano em ${start.dayOfMonth}/${start.monthValue}."
                        Recurrence.NENHUMA -> ""
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Conta ativa (gera novas ocorrências)", Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
            if (id != 0L) Text(
                "Alterar o valor atualiza apenas ocorrências futuras ainda não pagas. Pagamentos e meses anteriores não mudam.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = !busy && name.isNotBlank() && Money.parse(amountText) != null,
                onClick = {
                    val t = BillTemplate(id = id, name = name.trim(), category = category.ifBlank { "Outras despesas" },
                        expectedAmount = Money.parse(amountText)!!, recurrence = recurrence, startDate = start,
                        endDate = if (recurrence == Recurrence.NENHUMA) null else end, active = active, notes = notes)
                    vm.run(block = { vm.repository.saveBillTemplate(t) }) { if (it is OpResult.Success) nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salvar") }

            if (id != 0L) {
                val history = ui?.data?.changeLog?.filter { it.entityType == "bill" && it.entityId == id }.orEmpty()
                if (history.isNotEmpty()) {
                    SectionTitle("Histórico de alterações")
                    history.take(30).forEach {
                        Text("${Fmt.date(java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate())} — ${it.description}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Excluir conta?",
        "A conta, todas as suas ocorrências e os pagamentos registrados nela serão apagados. Para parar de gerar novas ocorrências mantendo o histórico, desative a conta.",
        confirmText = "Excluir", destructive = true,
        onConfirm = { vm.run(block = { vm.repository.deleteBillTemplate(id) }) { if (it is OpResult.Success) nav.popBackStack() } },
        onDismiss = { confirmDelete = false }
    )
}
