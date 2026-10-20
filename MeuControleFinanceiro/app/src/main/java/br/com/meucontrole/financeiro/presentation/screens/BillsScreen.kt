package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.data.repository.OpResult
import br.com.meucontrole.financeiro.domain.calc.DueItem
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.EmptyState
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.components.MonthSelector
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.SummaryCard
import br.com.meucontrole.financeiro.presentation.components.ValueRow
import br.com.meucontrole.financeiro.presentation.navigation.Routes

private enum class BillFilter(val label: String) { TODAS("Todas"), PENDENTES("Pendentes"), PAGAS("Pagas"), VENCIDAS("Vencidas") }

@Composable
fun BillsScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(BillFilter.TODAS) }
    val actions = rememberDueItemActions()
    var detail by remember { mutableStateOf<DueItem?>(null) }

    AppScaffold(
        title = "Contas",
        topLevel = true,
        fab = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.bill()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Nova conta") })
        }
    ) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val monthItems = remember(u, month) { u.engine.itemsForMonth(month).filter { it.kind == DueKind.BILL } }
        val shown = monthItems
            .filter {
                when (filter) {
                    BillFilter.TODAS -> true
                    BillFilter.PENDENTES -> it.status != ItemStatus.PAGA
                    BillFilter.PAGAS -> it.status == ItemStatus.PAGA
                    BillFilter.VENCIDAS -> it.status == ItemStatus.VENCIDA
                }
            }
            .filter { query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true) }

        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            item { MonthSelector(month, vm::setMonth) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Previsto", monthItems.sumOf { it.expected }, Modifier.weight(1f))
                    SummaryCard("Pago", monthItems.sumOf { it.done }, Modifier.weight(1f))
                    SummaryCard("Pendente", monthItems.sumOf { it.remaining }, Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar conta ou categoria") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                }
            }
            if (u.data.billTemplates.isEmpty()) item {
                EmptyState("Nenhuma conta cadastrada", "Cadastre água, energia, aluguel, internet e outras despesas. Contas recorrentes são geradas automaticamente a cada mês.")
            } else if (shown.isEmpty()) item {
                EmptyState("Nada neste filtro", "Não há contas ${filter.label.lowercase()} em ${Fmt.month(month)}.")
            }
            items(shown, key = { it.refId }) { item ->
                DueItemRow(item, onClick = { detail = item }, onQuickAction = { actions.open(item) })
                HorizontalDivider()
            }
            if (u.data.billTemplates.isNotEmpty()) {
                item { SectionTitle("Contas cadastradas") }
                items(u.data.billTemplates, key = { "t" + it.id }) { t ->
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.bill(t.id)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Medium)
                            Text("${t.category} • ${t.recurrence.label}${if (!t.active) " • desativada" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Money.format(t.expectedAmount))
                    }
                }
            }
        }
    }
    DueItemDialogs(vm, actions)
    detail?.let { d -> BillOccurrenceDialog(vm, d, onPay = { detail = null; actions.open(d) }, onEditTemplate = { tid -> detail = null; nav.navigate(Routes.bill(tid)) }) { detail = null } }
}

/** Detalhes de uma ocorrência: histórico de pagamentos, ajuste de valor desta ocorrência e opção de ignorar. */
@Composable
fun BillOccurrenceDialog(vm: MainViewModel, item: DueItem, onPay: () -> Unit, onEditTemplate: (Long) -> Unit, onDismiss: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val occ = ui?.data?.billOccurrences?.firstOrNull { it.id == item.refId } ?: return
    val live = ui?.engine?.billItem(occ) ?: item
    val payments = ui?.engine?.paymentsOf(PaymentTarget.BILL, occ.id).orEmpty()
    var editing by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(Money.formatPlain(occ.expectedAmount)) }
    var date by remember { mutableStateOf(occ.dueDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(live.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${live.category} • vence ${Fmt.date(occ.dueDate)} • ${live.status.label}", style = MaterialTheme.typography.bodySmall)
                ValueRow("Previsto (com ajustes)", live.expected)
                ValueRow("Pago", live.done)
                ValueRow("Restante", live.remaining, bold = true)
                if (editing) {
                    InfoCard {
                        Text("Alterar somente esta ocorrência", style = MaterialTheme.typography.labelLarge)
                        MoneyField("Valor previsto", amountText, { amountText = it })
                        DateField("Vencimento", date, { if (it != null) date = it })
                        TextButton(onClick = {
                            val v = Money.parse(amountText) ?: return@TextButton
                            vm.run(block = { vm.repository.updateBillOccurrence(occ.copy(expectedAmount = v, dueDate = date)) }) {
                                if (it is OpResult.Success) editing = false
                            }
                        }) { Text("Salvar alteração") }
                    }
                }
                SectionTitle("Pagamentos")
                PaymentHistory(vm, payments)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    if (!editing) TextButton(onClick = { editing = true }) { Text("Ajustar valor") }
                    TextButton(onClick = { onEditTemplate(occ.templateId) }) { Text("Editar conta") }
                    if (payments.none { !it.reversed }) TextButton(onClick = {
                        vm.run(block = { vm.repository.updateBillOccurrence(occ.copy(skipped = true)) }) { if (it is OpResult.Success) onDismiss() }
                    }) { Text("Ignorar este mês") }
                }
            }
        },
        confirmButton = {
            if (live.status != ItemStatus.PAGA) TextButton(onClick = onPay) { Text("Pagar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
