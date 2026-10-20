package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import br.com.meucontrole.financeiro.domain.calc.DebtSummary
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.InstallmentView
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.ConfirmDialog
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.components.ProgressLine
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.StatusBadge
import br.com.meucontrole.financeiro.presentation.components.TextRow
import br.com.meucontrole.financeiro.presentation.components.ValueRow
import br.com.meucontrole.financeiro.presentation.navigation.Routes
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors

@Composable
fun DebtDetailScreen(vm: MainViewModel, nav: NavController, debtId: Long) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var payTarget by remember { mutableStateOf<PayTarget?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var editInstallment by remember { mutableStateOf<InstallmentView?>(null) }

    val summary = ui?.engine?.summaryOf(debtId)

    AppScaffold(
        title = summary?.debt?.name ?: "Dívida",
        onBack = { nav.popBackStack() },
        actions = {
            if (summary != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais ações") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar dados") }, onClick = { menu = false; nav.navigate(Routes.editDebt(debtId)) })
                    if (summary.debt.status == DebtStatus.ATIVA) {
                        DropdownMenuItem(text = { Text("Quitar por acordo") }, onClick = {
                            menu = false
                            payTarget = PayTarget(PaymentTarget.DEBT_AGREEMENT, debtId, summary.debt.name, "Acordo de quitação", summary.outstanding, null)
                        })
                        DropdownMenuItem(text = { Text("Renegociar") }, onClick = { menu = false; nav.navigate(Routes.renegotiate(debtId)) })
                    }
                    DropdownMenuItem(text = { Text("Excluir dívida", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
    ) { padding ->
        if (ui == null) { Loading(); return@AppScaffold }
        if (summary == null) {
            Text("Dívida não encontrada.", Modifier.padding(padding).padding(16.dp))
            return@AppScaffold
        }
        val fin = LocalFinanceColors.current
        val d = summary.debt
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { DebtHeader(summary, nav) }
            item { SectionTitle("Parcelas") }
            items(summary.installments, key = { it.installment.id }) { v ->
                val open = expanded == v.installment.id
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.clickable { expanded = if (open) null else v.installment.id }.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Parcela ${v.installment.label}", fontWeight = FontWeight.SemiBold)
                                Text("Vence ${Fmt.date(v.installment.dueDate)}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(2.dp))
                                if (d.status == DebtStatus.ATIVA || v.settlement.paid > 0) StatusBadge(v.settlement.status)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(v.settlement.owed), fontWeight = FontWeight.SemiBold)
                                if (v.settlement.paid > 0) Text("pago ${Money.format(v.settlement.paid)}", style = MaterialTheme.typography.labelSmall, color = fin.income)
                                if (d.status == DebtStatus.ATIVA && v.settlement.remaining > 0 && v.settlement.paid > 0)
                                    Text("resta ${Money.format(v.settlement.remaining)}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (d.status == DebtStatus.ATIVA && v.settlement.status != ItemStatus.PAGA) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = {
                                    payTarget = PayTarget(PaymentTarget.INSTALLMENT, v.installment.id, d.name,
                                        "Parcela ${v.installment.label}", v.settlement.remaining, v.installment.dueDate)
                                }) { Icon(Icons.Filled.Check, contentDescription = "Pagar parcela ${v.installment.label}") }
                            }
                        }
                        if (open) {
                            Spacer(Modifier.height(8.dp))
                            ValueRow("Valor previsto", v.installment.amount)
                            if (v.settlement.charges > 0) ValueRow("Encargos cobrados", v.settlement.charges, fin.expense)
                            if (v.settlement.discount > 0) ValueRow("Descontos concedidos", v.settlement.discount, fin.income)
                            ValueRow("Pago", v.settlement.paid)
                            ValueRow("Saldo restante", if (d.status == DebtStatus.ATIVA) v.settlement.remaining else 0, bold = true)
                            if (v.installment.notes.isNotBlank()) Text(v.installment.notes, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("Histórico de pagamentos", style = MaterialTheme.typography.labelLarge)
                            PaymentHistory(vm, v.payments.sortedByDescending { it.date })
                            if (d.status == DebtStatus.ATIVA && v.payments.none { !it.reversed }) {
                                TextButton(onClick = { editInstallment = v }) { Text("Ajustar valor/vencimento desta parcela") }
                            }
                        }
                    }
                }
            }
            if (summary.agreementPayments.isNotEmpty()) {
                item {
                    SectionTitle("Acordo de quitação")
                    InfoCard { PaymentHistory(vm, summary.agreementPayments) }
                }
            }
            if (d.notes.isNotBlank()) item {
                SectionTitle("Observações")
                InfoCard { Text(d.notes) }
            }
        }
    }

    payTarget?.let { PayDialog(vm, it) { payTarget = null } }
    editInstallment?.let { v -> EditInstallmentDialog(vm, v) { editInstallment = null } }
    if (confirmDelete) ConfirmDialog(
        title = "Excluir dívida?",
        message = "A dívida, suas parcelas e TODOS os pagamentos registrados serão apagados definitivamente. Para manter o histórico, prefira quitar por acordo ou renegociar.",
        confirmText = "Excluir",
        destructive = true,
        onConfirm = { vm.run(block = { vm.repository.deleteDebt(debtId) }) { if (it is OpResult.Success) nav.popBackStack() } },
        onDismiss = { confirmDelete = false }
    )
}

@Composable
private fun DebtHeader(s: DebtSummary, nav: NavController) {
    val d = s.debt
    val fin = LocalFinanceColors.current
    InfoCard {
        Text(listOf(d.creditor, d.category, d.status.label).filter { it.isNotBlank() }.joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(Money.format(s.outstanding), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("saldo devedor atualizado", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        ProgressLine(s.progress, "${s.paidCount} de ${s.installments.size} parcelas quitadas")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ValueRow("Valor original", d.originalAmount)
        if (d.downPayment > 0) ValueRow("Entrada paga", d.downPayment)
        ValueRow("Total contratado", d.totalContracted)
        ValueRow("Saldo parcelado", s.installmentsPlanned)
        ValueRow("Total previsto para pagamento", s.totalForecast, bold = true)
        if (d.registeredCharges > 0) ValueRow("Encargos informados no cadastro", d.registeredCharges)
        if (d.negotiatedDiscount > 0) ValueRow("Descontos negociados no cadastro", d.negotiatedDiscount)
        ValueRow("Encargos cobrados em pagamentos", s.chargesCharged, if (s.chargesCharged > 0) fin.expense else MaterialTheme.colorScheme.onSurface)
        ValueRow("Descontos obtidos em pagamentos", s.discountsGranted, if (s.discountsGranted > 0) fin.income else MaterialTheme.colorScheme.onSurface)
        ValueRow("Total pago (sem a entrada)", s.totalPaid, fin.income)
        if (s.overdue > 0) ValueRow("Vencido", s.overdue, fin.expense, bold = true)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextRow("Contratação", Fmt.date(d.contractDate))
        TextRow("Vencimento", "todo dia ${d.dueDay}")
        if (d.interestRateInfo.isNotBlank()) TextRow("Juros informados", d.interestRateInfo)
        s.forecastPayoff?.let { TextRow("Previsão de quitação", Fmt.date(it)) }
        d.renegotiatedFromId?.let { oldId ->
            TextButton(onClick = { nav.navigate(Routes.debt(oldId)) }) { Text("Ver dívida original (renegociada)") }
        }
        if (d.status == DebtStatus.RENEGOCIADA) {
            Text("Esta dívida foi renegociada. O histórico abaixo foi preservado e o saldo não é mais contado.",
                style = MaterialTheme.typography.bodySmall, color = fin.neutral)
        }
    }
}

@Composable
private fun EditInstallmentDialog(vm: MainViewModel, v: InstallmentView, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf(Money.formatPlain(v.installment.amount)) }
    var date by remember { mutableStateOf(v.installment.dueDate) }
    var notes by remember { mutableStateOf(v.installment.notes) }
    val amount = Money.parse(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parcela ${v.installment.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Valor previsto", amountText, { amountText = it })
                DateField("Vencimento", date, { if (it != null) date = it })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            FilledTonalButton(enabled = amount != null && amount > 0, onClick = {
                vm.run(block = { vm.repository.updateInstallment(v.installment.copy(amount = amount!!, dueDate = date, notes = notes)) }) {
                    if (it is OpResult.Success) onDismiss()
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
