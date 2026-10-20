package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.BalanceLine
import br.com.meucontrole.financeiro.presentation.components.EmptyState
import br.com.meucontrole.financeiro.presentation.components.IncomeExpenseBars
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.MonthSelector
import br.com.meucontrole.financeiro.presentation.components.ProgressLine
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.SummaryCard
import br.com.meucontrole.financeiro.presentation.components.ValueRow
import br.com.meucontrole.financeiro.presentation.navigation.Routes
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val actions = rememberDueItemActions()
    val fin = LocalFinanceColors.current

    AppScaffold(title = "Meu Controle Financeiro", topLevel = true) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val s = remember(u, month) { u.engine.monthSummary(month) }
        val history = remember(u, month) { u.engine.history((5 downTo 0).map { month.minusMonths(it.toLong()) }) }

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonthSelector(month, vm::setMonth)

            if (!s.hasAnyData) {
                InfoCard {
                    EmptyState(
                        title = "Comece cadastrando suas informações",
                        message = "Cadastre seu salário, suas contas do mês e suas dívidas. O painel mostrará previsões, pagamentos e quanto sobra — sem dados fictícios."
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nav.navigate(Routes.income()) }) { Text("Cadastrar salário/receita") }
                            OutlinedButton(onClick = { nav.navigate(Routes.bill()) }) { Text("Cadastrar conta mensal") }
                            OutlinedButton(onClick = { nav.navigate(Routes.newDebt()) }) { Text("Cadastrar dívida") }
                            TextButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Informar saldo inicial em conta") }
                        }
                    }
                }
                return@Column
            }

            // ---- Quanto posso gastar ----
            val b = s.budget
            Card(
                onClick = { nav.navigate(Routes.BUDGET) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quanto posso gastar? (estimado até o fim do mês)", style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(b.projected), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = if (b.projected < 0) fin.expense else MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Caixa estimado hoje: ${Money.format(b.cashEstimate)} • reservado para compromissos: ${Money.format(b.reserved)}",
                        style = MaterialTheme.typography.bodySmall)
                    if (b.expensesExceedIncome) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = fin.expense)
                            Text(" Compromissos previstos superam o dinheiro esperado.", color = fin.expense, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ---- Indicadores ----
            SectionTitle("Resumo do mês")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Salário líquido previsto", s.salaryExpected, Modifier.weight(1f), color = fin.income)
                SummaryCard("Outras receitas", s.otherIncomeExpected, Modifier.weight(1f), color = fin.income)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Contas previstas", s.billsExpected, Modifier.weight(1f), onClick = { nav.navigate(Routes.BILLS) })
                SummaryCard("Parcelas do mês", s.installmentsDue, Modifier.weight(1f), onClick = { nav.navigate(Routes.DEBTS) })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Pago no mês", s.totalPaidInMonth, Modifier.weight(1f), color = fin.expense)
                SummaryCard("Saldo devedor total", s.debtOutstandingTotal, Modifier.weight(1f), onClick = { nav.navigate(Routes.DEBTS) })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Vencidos (${s.overdueCount})", s.overdueTotal, Modifier.weight(1f),
                    color = if (s.overdueTotal > 0) fin.expense else MaterialTheme.colorScheme.onSurface,
                    onClick = { nav.navigate(Routes.CALENDAR) })
                SummaryCard("Recebido no mês", s.incomeReceived, Modifier.weight(1f), color = fin.income, onClick = { nav.navigate(Routes.INCOMES) })
            }

            // ---- Previsto x pago x pendente ----
            SectionTitle("Previsto, pago e pendente")
            InfoCard {
                Text("Receitas", fontWeight = FontWeight.SemiBold)
                ValueRow("Previstas", s.salaryExpected + s.otherIncomeExpected)
                ValueRow("Recebidas", s.incomeReceived, fin.income)
                ValueRow("A receber", s.incomePending)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Contas", fontWeight = FontWeight.SemiBold)
                ValueRow("Previstas", s.billsExpected)
                ValueRow("Pagas", s.billsPaid, fin.expense)
                ValueRow("Pendentes", s.billsPending)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Parcelas de dívidas", fontWeight = FontWeight.SemiBold)
                ValueRow("Previstas no mês", s.installmentsDue)
                ValueRow("Pagas no mês", s.installmentsPaid, fin.expense)
                ValueRow("Pendentes no mês", s.installmentsPending)
            }

            // ---- Progresso ----
            SectionTitle("Quitação das dívidas")
            InfoCard { ProgressLine(s.debtProgress, "Progresso geral de quitação") }

            // ---- Próximos vencimentos ----
            SectionTitle("Próximos 30 dias")
            InfoCard {
                if (s.upcoming.isEmpty()) Text("Nada pendente nos próximos 30 dias.", style = MaterialTheme.typography.bodyMedium)
                s.upcoming.forEach { item ->
                    DueItemRow(
                        item,
                        onClick = {
                            if (item.kind == DueKind.INSTALLMENT) {
                                u.data.installments.firstOrNull { it.id == item.refId }?.let { nav.navigate(Routes.debt(it.debtId)) }
                            } else actions.open(item)
                        },
                        onQuickAction = { actions.open(item) }
                    )
                }
            }

            // ---- Gráficos ----
            SectionTitle("Recebido x pago (6 meses)")
            InfoCard { IncomeExpenseBars(history) }
            SectionTitle("Histórico do saldo")
            InfoCard {
                BalanceLine(history)
                Spacer(Modifier.height(4.dp))
                Text("Saldo acumulado a partir do saldo inicial informado, considerando apenas valores efetivamente recebidos e pagos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    DueItemDialogs(vm, actions)
}
