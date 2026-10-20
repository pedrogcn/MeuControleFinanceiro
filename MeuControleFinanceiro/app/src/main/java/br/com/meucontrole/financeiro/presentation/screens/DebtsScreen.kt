package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.domain.calc.DebtSummary
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.EmptyState
import br.com.meucontrole.financeiro.presentation.components.ProgressLine
import br.com.meucontrole.financeiro.presentation.components.SummaryCard
import br.com.meucontrole.financeiro.presentation.navigation.Routes
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors

@Composable
fun DebtsScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<DebtStatus?>(DebtStatus.ATIVA) }

    AppScaffold(
        title = "Dívidas",
        topLevel = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.newDebt()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova dívida") }
            )
        }
    ) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val all = u.engine.debtSummaries
        val list = remember(all, query, filter) {
            all.filter { filter == null || it.debt.status == filter }
                .filter { query.isBlank() || it.debt.name.contains(query, true) || it.debt.creditor.contains(query, true) || it.debt.category.contains(query, true) }
                .sortedWith(compareBy<DebtSummary> { it.debt.status.ordinal }.thenBy { it.nextDue?.installment?.dueDate })
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Saldo devedor", u.engine.totalOutstanding, Modifier.weight(1f))
                    SummaryCard("Vencido", all.sumOf { it.overdue }, Modifier.weight(1f),
                        color = if (all.sumOf { it.overdue } > 0) LocalFinanceColors.current.expense else MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                OutlinedTextField(
                    query, { query = it }, label = { Text("Buscar por nome, credor ou categoria") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todas") })
                    DebtStatus.entries.forEach { st ->
                        FilterChip(selected = filter == st, onClick = { filter = st }, label = { Text(st.label + "s") })
                    }
                }
            }
            if (list.isEmpty()) item {
                EmptyState(
                    if (all.isEmpty()) "Nenhuma dívida cadastrada" else "Nada encontrado",
                    if (all.isEmpty()) "Toque em \"Nova dívida\" para cadastrar empréstimos, cartões, financiamentos ou acordos." else "Ajuste a busca ou o filtro."
                )
            }
            items(list, key = { it.debt.id }) { s -> DebtCard(s) { nav.navigate(Routes.debt(s.debt.id)) } }
        }
    }
}

@Composable
private fun DebtCard(s: DebtSummary, onClick: () -> Unit) {
    val fin = LocalFinanceColors.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.debt.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(s.debt.creditor, s.debt.category).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(s.outstanding), fontWeight = FontWeight.Bold)
                    Text(s.debt.status.label, style = MaterialTheme.typography.labelSmall,
                        color = when (s.debt.status) {
                            DebtStatus.QUITADA -> fin.income
                            DebtStatus.RENEGOCIADA -> fin.neutral
                            DebtStatus.ATIVA -> if (s.overdue > 0) fin.expense else MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
            }
            Spacer(Modifier.height(8.dp))
            ProgressLine(s.progress, "${s.paidCount}/${s.installments.size} parcelas pagas")
            s.nextDue?.let { n ->
                Spacer(Modifier.height(6.dp))
                Text("Próxima: ${n.installment.label} em ${Fmt.date(n.installment.dueDate)} — ${Money.format(n.settlement.remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.overdue > 0) fin.expense else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.overdue > 0) Text("Vencido: ${Money.format(s.overdue)}", style = MaterialTheme.typography.bodySmall, color = fin.expense)
        }
    }
}
