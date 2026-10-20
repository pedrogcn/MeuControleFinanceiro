package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.ValueRow
import br.com.meucontrole.financeiro.presentation.navigation.Routes
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import java.time.YearMonth

private enum class Period(val label: String) { MES("Este mês"), D7("Próximos 7 dias"), D15("Próximos 15 dias"), D30("Próximos 30 dias"), PROX("Até o fim do próximo mês") }

@Composable
fun BudgetScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(Period.MES) }
    val fin = LocalFinanceColors.current

    AppScaffold(title = "Quanto posso gastar?", onBack = { nav.popBackStack() }) { padding ->
        val u = ui ?: run { Loading(); return@AppScaffold }
        val today = u.today
        val (start, end) = when (period) {
            Period.MES -> YearMonth.from(today).atDay(1) to YearMonth.from(today).atEndOfMonth()
            Period.D7 -> today to today.plusDays(7)
            Period.D15 -> today to today.plusDays(15)
            Period.D30 -> today to today.plusDays(30)
            Period.PROX -> YearMonth.from(today).atDay(1) to YearMonth.from(today).plusMonths(1).atEndOfMonth()
        }
        val b = remember(u, start, end) { u.engine.budget(start, end) }

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) }) }
            }
            Text("Período: ${Fmt.date(start)} a ${Fmt.date(end)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

            SectionTitle("B. Saldo projetado")
            InfoCard {
                Text(Money.format(b.projected), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = if (b.projected < 0) fin.expense else fin.income)
                Text("Estimativa do que sobra até ${Fmt.date(b.periodEnd)}, se tudo ocorrer como previsto.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ValueRow("Caixa estimado hoje", b.cashEstimate)
                ValueRow("+ Receitas ainda previstas", b.pendingIncome, fin.income)
                ValueRow("− Contas pendentes (inclui vencidas)", b.pendingBills, fin.expense)
                ValueRow("− Parcelas a vencer/vencidas", b.pendingInstallments, fin.expense)
                ValueRow("= Saldo projetado", b.projected, bold = true)
                Spacer(Modifier.height(4.dp))
                ValueRow("Valor reservado para compromissos", b.reserved)
                if (b.expensesExceedIncome) {
                    Spacer(Modifier.height(6.dp))
                    Text("Alerta: os compromissos previstos (${Money.format(b.reserved)}) superam o caixa estimado mais as receitas esperadas.",
                        color = fin.expense, style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionTitle("A. Saldo realizado no período")
            InfoCard {
                ValueRow("Receitas efetivamente recebidas", b.receivedIncome, fin.income)
                ValueRow("Despesas efetivamente pagas", b.paidExpenses, fin.expense)
                ValueRow("= Resultado realizado", b.realizedPeriod, bold = true)
                Text("Considera apenas dinheiro que entrou e saiu de fato. Previsões não entram aqui.", style = MaterialTheme.typography.bodySmall)
            }

            SectionTitle("Como o caixa é estimado")
            InfoCard {
                ValueRow("Saldo inicial informado", b.openingBalance)
                Text(
                    if (b.openingDate != null) "Data do saldo inicial: ${Fmt.date(b.openingDate)}. Desde então somamos o que foi recebido e subtraímos o que foi pago."
                    else "Nenhum saldo inicial informado. Informe quanto você tem hoje em conta/dinheiro para que a estimativa fique correta.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Ajustar saldo inicial") }
                Text(
                    "Importante: o saldo projetado é uma estimativa feita a partir do que você registrou. Ele pode ser diferente do valor real no banco " +
                        "(tarifas, compras não lançadas, rendimentos). Confira seu extrato periodicamente e ajuste o saldo inicial quando necessário.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
