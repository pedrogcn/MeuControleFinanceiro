package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.data.repository.OpResult
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.model.IncomeFrequency
import br.com.meucontrole.financeiro.domain.model.IncomeKind
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.ConfirmDialog
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.DropdownField
import br.com.meucontrole.financeiro.presentation.components.EmptyState
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.components.MonthSelector
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.SummaryCard
import br.com.meucontrole.financeiro.presentation.navigation.Routes
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import java.time.LocalDate

@Composable
fun IncomesScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val actions = rememberDueItemActions()
    var extraFor by remember { mutableStateOf<IncomeSource?>(null) }
    val fin = LocalFinanceColors.current

    AppScaffold(
        title = "Salário e receitas",
        onBack = { nav.popBackStack() },
        fab = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.income()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Nova receita") })
        }
    ) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val items = remember(u, month) { u.engine.itemsForMonth(month).filter { it.kind == DueKind.INCOME } }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item { MonthSelector(month, vm::setMonth) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Previsto", items.sumOf { it.expected }, Modifier.weight(1f))
                    SummaryCard("Recebido", items.sumOf { it.done }, Modifier.weight(1f), color = fin.income)
                    SummaryCard("A receber", items.sumOf { it.remaining }, Modifier.weight(1f))
                }
            }
            item { SectionTitle("Recebimentos do mês") }
            if (items.isEmpty()) item { Text("Nenhum recebimento previsto neste mês.", style = MaterialTheme.typography.bodyMedium) }
            items(items, key = { it.refId }) { item ->
                DueItemRow(item, onClick = { actions.open(item) }, onQuickAction = { actions.open(item) })
                HorizontalDivider()
            }
            item { SectionTitle("Fontes de renda") }
            if (u.data.incomeSources.isEmpty()) item {
                EmptyState("Nenhuma receita cadastrada", "Cadastre seu salário líquido, benefícios e rendas extras. O salário bruto é opcional e serve só para consulta.")
            }
            items(u.data.incomeSources, key = { "s" + it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.income(s.id)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Medium)
                        Text("${s.kind.label} • ${s.frequency.label}${if (!s.active) " • inativa" else ""}" +
                            (s.grossAmount?.let { " • bruto ${Money.format(it)} (informativo)" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(s.netAmount), color = fin.income)
                        TextButton(onClick = { extraFor = s }) { Text("+ lançamento") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
    DueItemDialogs(vm, actions)
    extraFor?.let { s -> ExtraIncomeDialog(vm, s) { extraFor = null } }
}

/** Lançamento avulso para uma fonte (ex.: hora extra de um mês, bônus, renda eventual). */
@Composable
private fun ExtraIncomeDialog(vm: MainViewModel, source: IncomeSource, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var received by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lançamento em ${source.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Valor", amountText, { amountText = it })
                DateField("Data", date, { if (it != null) date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(received, { received = it }); Text("Já recebido")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = Money.parse(amountText) != null, onClick = {
                vm.run(block = { vm.repository.addOneOffIncome(source.id, date, Money.parse(amountText)!!, received) }) {
                    if (it is OpResult.Success) onDismiss()
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun IncomeFormScreen(vm: MainViewModel, nav: NavController, id: Long) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(IncomeKind.SALARIO) }
    var grossText by remember { mutableStateOf("") }
    var netText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(IncomeFrequency.MENSAL) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var active by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui != null) {
        if (loaded) return@LaunchedEffect
        ui?.data?.incomeSources?.firstOrNull { it.id == id }?.let { s ->
            name = s.name; kind = s.kind; grossText = s.grossAmount?.let { Money.formatPlain(it) } ?: ""
            netText = Money.formatPlain(s.netAmount); frequency = s.frequency; start = s.startDate; end = s.endDate
            active = s.active; notes = s.notes
        }
        loaded = true
    }

    AppScaffold(
        title = if (id == 0L) "Nova receita" else "Editar receita",
        onBack = { nav.popBackStack() },
        actions = { if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir receita") } }
    ) { padding ->
        Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome da fonte (ex.: Empresa X) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DropdownField("Tipo", IncomeKind.entries, kind, { it.label }, { kind = it })
            MoneyField("Valor líquido recebido *", netText, { netText = it },
                supporting = "É este valor que entra no orçamento.")
            MoneyField("Salário bruto (opcional)", grossText, { grossText = it }, required = false,
                supporting = "Apenas informativo. Nunca é somado ao líquido.")
            DropdownField("Frequência", IncomeFrequency.entries, frequency, { it.label }, { frequency = it })
            DateField(if (frequency == IncomeFrequency.EVENTUAL) "Data prevista" else "Primeiro recebimento", start, { if (it != null) start = it })
            if (frequency == IncomeFrequency.QUINZENAL) {
                val d = start.dayOfMonth
                val (d1, d2) = if (d <= 15) d to d + 15 else (d - 15) to d
                Text("Recebimentos nos dias $d1 e $d2 de cada mês (ajustado ao fim do mês quando necessário).", style = MaterialTheme.typography.bodySmall)
            }
            if (frequency != IncomeFrequency.EVENTUAL) DateField("Até (opcional)", end, { end = it }, allowClear = true)
            if (kind == IncomeKind.EXTRA || kind == IncomeKind.VARIAVEL) {
                Text("Para renda variável, informe uma estimativa. Ao receber, ajuste o valor de cada ocorrência.", style = MaterialTheme.typography.bodySmall)
            }
            if (id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ativa (gera novas previsões)", Modifier.weight(1f)); Switch(active, { active = it })
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
            if (id != 0L) Text("Ao salvar, previsões futuras ainda não recebidas são recalculadas. Recebimentos já registrados não mudam.",
                style = MaterialTheme.typography.bodySmall)
            val net = Money.parse(netText)
            val gross = if (grossText.isBlank()) null else Money.parse(grossText)
            Button(
                enabled = !busy && name.isNotBlank() && net != null && (grossText.isBlank() || gross != null),
                onClick = {
                    val s = IncomeSource(id = id, name = name.trim(), kind = kind, grossAmount = gross, netAmount = net!!,
                        frequency = frequency, startDate = start, endDate = if (frequency == IncomeFrequency.EVENTUAL) null else end,
                        active = active, notes = notes)
                    vm.run(block = { vm.repository.saveIncomeSource(s) }) { if (it is OpResult.Success) nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salvar") }
            if (gross != null && net != null && gross < net) {
                Text("Atenção: o bruto informado é menor que o líquido.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Excluir receita?", "A fonte de renda e todos os seus recebimentos (previstos e registrados) serão apagados.",
        confirmText = "Excluir", destructive = true,
        onConfirm = { vm.run(block = { vm.repository.deleteIncomeSource(id) }) { if (it is OpResult.Success) nav.popBackStack() } },
        onDismiss = { confirmDelete = false }
    )
}
