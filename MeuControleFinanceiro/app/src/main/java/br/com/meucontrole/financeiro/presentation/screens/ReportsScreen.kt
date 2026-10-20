package br.com.meucontrole.financeiro.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.ReportBuilder
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.BalanceLine
import br.com.meucontrole.financeiro.presentation.components.IncomeExpenseBars
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.MonthSelector
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.TextRow
import br.com.meucontrole.financeiro.presentation.components.ValueRow
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import br.com.meucontrole.financeiro.util.FileExport
import br.com.meucontrole.financeiro.util.PdfReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportsScreen(vm: MainViewModel, nav: NavController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fin = LocalFinanceColors.current
    val report = remember(ui, month) { ui?.let { ReportBuilder.monthly(it.data, month, it.today) } }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val r = report ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { FileExport.writeText(context, uri, ReportBuilder.monthlyCsv(r), withBom = true) } }
            vm.message(if (ok.isSuccess) "CSV exportado." else "Falha ao exportar: ${ok.exceptionOrNull()?.message}")
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val r = report ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { PdfReport.write(context, uri, r) } }
            vm.message(if (ok.isSuccess) "PDF exportado." else "Falha ao exportar: ${ok.exceptionOrNull()?.message}")
        }
    }

    AppScaffold(title = "Relatórios", onBack = { nav.popBackStack() }) { padding ->
        val r = report ?: run { Loading(); return@AppScaffold }
        val s = r.summary
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            MonthSelector(month, vm::setMonth)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { csvLauncher.launch("relatorio-${month}.csv") }, modifier = Modifier.weight(1f)) { Text("Exportar CSV") }
                OutlinedButton(onClick = { pdfLauncher.launch("relatorio-${month}.pdf") }, modifier = Modifier.weight(1f)) { Text("Exportar PDF") }
            }
            SectionTitle("Resumo de ${Fmt.month(month)}")
            InfoCard {
                ValueRow("Total recebido", s.incomeReceived, fin.income)
                ValueRow("Total gasto (pago)", s.totalPaidInMonth, fin.expense)
                ValueRow("Pago em dívidas", s.installmentsPaid)
                ValueRow("Pago em contas", s.billsPaid)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ValueRow("Saldo devedor atualizado", s.debtOutstandingTotal, bold = true)
                TextRow("Parcelas pagas no mês", r.paidInstallmentsInMonth.toString())
                TextRow("Parcelas restantes (todas as dívidas)", r.remainingInstallments.toString())
                ValueRow("Contas/parcelas vencidas", s.overdueTotal, if (s.overdueTotal > 0) fin.expense else MaterialTheme.colorScheme.onSurface)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ValueRow("Resultado realizado", s.budget.realizedPeriod, bold = true)
                ValueRow("Saldo projetado ao fim do mês", s.budget.projected)
            }
            SectionTitle("Despesas por categoria")
            InfoCard {
                if (r.categories.isEmpty()) Text("Sem despesas neste mês.")
                Row { Text("Categoria", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Text("Previsto / Pago", style = MaterialTheme.typography.labelMedium) }
                r.categories.forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(it.category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${Money.format(it.expected)} / ${Money.format(it.paid)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionTitle("Receitas x despesas")
            InfoCard { IncomeExpenseBars(r.history) }
            SectionTitle("Evolução do saldo")
            InfoCard { BalanceLine(r.history) }
            SectionTitle("Previsão de quitação")
            InfoCard {
                val active = r.debts.filter { it.debt.status == DebtStatus.ATIVA }
                if (active.isEmpty()) Text("Nenhuma dívida ativa.")
                active.forEach {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(it.debt.name, fontWeight = FontWeight.SemiBold)
                        Text("Saldo ${Money.format(it.outstanding)} • ${it.remainingCount} parcela(s) restante(s) • quitação prevista em ${Fmt.date(it.forecastPayoff)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                val quitadas = r.debts.count { it.debt.status == DebtStatus.QUITADA }
                if (quitadas > 0) Text("$quitadas dívida(s) já quitada(s).", style = MaterialTheme.typography.bodySmall, color = fin.income)
            }
            Text("Os relatórios usam exatamente os mesmos cálculos do painel inicial.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}
