package br.com.meucontrole.financeiro.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.meucontrole.financeiro.data.repository.OpResult
import br.com.meucontrole.financeiro.domain.calc.DueItem
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.model.Categories
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.domain.usecase.OverpayMode
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlan
import br.com.meucontrole.financeiro.domain.usecase.PaymentRequest
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.DropdownField
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.theme.LocalFinanceColors
import java.time.LocalDate
import java.util.UUID

/** O que está sendo pago. */
data class PayTarget(
    val target: PaymentTarget,
    val targetId: Long,
    val title: String,
    val subtitle: String,
    val remaining: Long,
    val dueDate: LocalDate?
)

fun DueItem.toPayTarget(): PayTarget? = when (kind) {
    DueKind.BILL -> PayTarget(PaymentTarget.BILL, refId, title, subtitle, remaining, date)
    DueKind.INSTALLMENT -> PayTarget(PaymentTarget.INSTALLMENT, refId, title, subtitle, remaining, date)
    DueKind.INCOME -> null
}

/**
 * Diálogo de pagamento. O identificador da operação (requestId) é criado UMA vez quando o diálogo abre:
 * repetir o toque ou reenviar a mesma operação nunca grava dois pagamentos.
 */
@Composable
fun PayDialog(vm: MainViewModel, target: PayTarget, onDismiss: () -> Unit) {
    val requestId = remember { UUID.randomUUID().toString() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var amountText by remember { mutableStateOf(Money.formatPlain(target.remaining)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var method by remember { mutableStateOf(Categories.paymentMethods.first()) }
    var showExtras by remember { mutableStateOf(false) }
    var chargesText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var decision by remember { mutableStateOf<PaymentPlan.NeedsDecision?>(null) }

    val amount = Money.parse(amountText)
    val charges = if (chargesText.isBlank()) 0L else Money.parse(chargesText)
    val discount = if (discountText.isBlank()) 0L else Money.parse(discountText)
    val valid = amount != null && charges != null && discount != null && (amount > 0 || (discount ?: 0) > 0)
    val isAgreement = target.target == PaymentTarget.DEBT_AGREEMENT

    fun request(mode: OverpayMode) = PaymentRequest(
        requestId = requestId, target = target.target, targetId = target.targetId,
        amount = amount ?: 0, charges = charges ?: 0, discount = discount ?: 0,
        date = date, method = method, notes = notes, overpayMode = mode
    )

    fun submit(mode: OverpayMode) {
        vm.pay(request(mode)) { result ->
            when (result) {
                is OpResult.Success -> onDismiss()
                is OpResult.NeedsDecision -> decision = result.decision
                is OpResult.Failure -> Unit
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isAgreement) "Quitar por acordo" else "Registrar pagamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.title, fontWeight = FontWeight.SemiBold)
                Text(
                    target.subtitle + (target.dueDate?.let { " • vence ${Fmt.date(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    (if (isAgreement) "Saldo devedor: " else "Restante: ") + Money.format(target.remaining),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                if (isAgreement) Text(
                    "Informe o valor efetivamente pago no acordo. A diferença será registrada como desconto e a dívida ficará quitada.",
                    style = MaterialTheme.typography.bodySmall
                )
                MoneyField(if (isAgreement) "Valor pago no acordo" else "Valor pago", amountText, { amountText = it })
                DateField("Data do pagamento", date, { if (it != null) date = it })
                DropdownField("Forma de pagamento", Categories.paymentMethods, method, { it }, { method = it })
                if (!isAgreement) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showExtras, onCheckedChange = { showExtras = it })
                        Text("Houve juros, multa ou desconto")
                    }
                    if (showExtras) {
                        MoneyField("Encargos cobrados (juros/multa)", chargesText, { chargesText = it }, required = false)
                        MoneyField("Desconto concedido", discountText, { discountText = it }, required = false)
                        val owed = target.remaining + (charges ?: 0) - (discount ?: 0)
                        Text("Total devido com ajustes: ${Money.format(owed.coerceAtLeast(0))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
                if (target.dueDate != null && date.isBefore(target.dueDate) && !isAgreement) {
                    Text("Pagamento antecipado (antes do vencimento).", style = MaterialTheme.typography.bodySmall, color = LocalFinanceColors.current.income)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit(OverpayMode.ASK) }, enabled = valid && !busy) { Text(if (busy) "Salvando…" else "Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )

    decision?.let { d ->
        OverpayDecisionDialog(
            decision = d,
            isAgreement = isAgreement,
            onChoose = { mode -> decision = null; submit(mode) },
            onDismiss = { decision = null }
        )
    }
}

/** Valor acima do saldo: o usuário escolhe explicitamente o que fazer com a diferença. */
@Composable
private fun OverpayDecisionDialog(
    decision: PaymentPlan.NeedsDecision,
    isAgreement: Boolean,
    onChoose: (OverpayMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valor acima do saldo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("O saldo deste item é ${Money.format(decision.itemRemaining)}, mas o valor informado excede em ${Money.format(decision.excess)}.")
                if (decision.excessBeyondDebt > 0 && decision.canApplyToNext) {
                    Text("Mesmo abatendo nas próximas parcelas, sobrariam ${Money.format(decision.excessBeyondDebt)} além do saldo total da dívida.",
                        color = MaterialTheme.colorScheme.error)
                }
                Text("O que deseja fazer com a diferença?")
                if (decision.canApplyToNext && decision.excessBeyondDebt == 0L) {
                    OutlinedButton(onClick = { onChoose(OverpayMode.APPLY_TO_NEXT) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Abater nas próximas parcelas")
                    }
                }
                OutlinedButton(onClick = { onChoose(OverpayMode.REGISTER_AS_CHARGES) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Registrar diferença como juros/multa")
                }
                OutlinedButton(onClick = { onChoose(OverpayMode.LIMIT_TO_BALANCE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isAgreement) "Registrar só o saldo devedor" else "Registrar só ${Money.format(decision.itemRemaining)}")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } }
    )
}

/** Histórico de pagamentos com ações de corrigir e estornar. */
@Composable
fun PaymentHistory(vm: MainViewModel, payments: List<Payment>) {
    var editing by remember { mutableStateOf<Payment?>(null) }
    var reversing by remember { mutableStateOf<Payment?>(null) }
    val fin = LocalFinanceColors.current
    if (payments.isEmpty()) {
        Text("Nenhum pagamento registrado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    payments.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Fmt.date(p.date)} • ${Money.format(p.amount)}" + (if (p.method.isNotBlank()) " • ${p.method}" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (p.reversed) TextDecoration.LineThrough else null,
                    color = if (p.reversed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                val extras = buildList {
                    if (p.charges > 0) add("encargos ${Money.format(p.charges)}")
                    if (p.discount > 0) add("desconto ${Money.format(p.discount)}")
                    if (p.target == PaymentTarget.DEBT_AGREEMENT) add("acordo de quitação")
                    if (p.editedAt != null) add("corrigido")
                }
                if (extras.isNotEmpty()) Text(extras.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall)
                if (p.reversed) Text("Estornado: ${p.reversalReason ?: ""}", style = MaterialTheme.typography.bodySmall, color = fin.expense)
            }
            if (!p.reversed) {
                if (p.target != PaymentTarget.DEBT_AGREEMENT) {
                    IconButton(onClick = { editing = p }) { Icon(Icons.Filled.Edit, contentDescription = "Corrigir lançamento") }
                }
                IconButton(onClick = { reversing = p }) { Icon(Icons.Filled.Replay, contentDescription = "Estornar pagamento") }
            }
        }
        HorizontalDivider()
    }
    editing?.let { p -> EditPaymentDialog(vm, p) { editing = null } }
    reversing?.let { p -> ReverseDialog(vm, p) { reversing = null } }
}

@Composable
private fun ReverseDialog(vm: MainViewModel, p: Payment, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Estornar pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("O pagamento de ${Money.format(p.amount)} em ${Fmt.date(p.date)} deixará de contar nos saldos, mas continuará no histórico.")
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo do estorno (obrigatório)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = reason.isNotBlank(), onClick = {
                vm.run(block = { vm.repository.reversePayment(p.id, reason) }) { if (it is OpResult.Success) onDismiss() }
            }) { Text("Estornar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditPaymentDialog(vm: MainViewModel, p: Payment, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf(Money.formatPlain(p.amount)) }
    var chargesText by remember { mutableStateOf(if (p.charges > 0) Money.formatPlain(p.charges) else "") }
    var discountText by remember { mutableStateOf(if (p.discount > 0) Money.formatPlain(p.discount) else "") }
    var date by remember { mutableStateOf(p.date) }
    var method by remember { mutableStateOf(p.method.ifBlank { Categories.paymentMethods.first() }) }
    var notes by remember { mutableStateOf(p.notes) }
    val amount = Money.parse(amountText)
    val charges = if (chargesText.isBlank()) 0L else Money.parse(chargesText)
    val discount = if (discountText.isBlank()) 0L else Money.parse(discountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrigir lançamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Valor pago", amountText, { amountText = it })
                MoneyField("Encargos", chargesText, { chargesText = it }, required = false)
                MoneyField("Desconto", discountText, { discountText = it }, required = false)
                DateField("Data", date, { if (it != null) date = it })
                DropdownField("Forma de pagamento", Categories.paymentMethods, method, { it }, { method = it })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
                Text("A correção fica registrada no histórico de alterações.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && charges != null && discount != null, onClick = {
                vm.run(block = { vm.repository.editPayment(p.id, amount!!, charges!!, discount!!, date, method, notes) }) {
                    if (it is OpResult.Success) onDismiss()
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Registrar/corrigir recebimento de uma receita prevista. */
@Composable
fun ReceiveDialog(vm: MainViewModel, item: DueItem, received: Boolean, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf(Money.formatPlain(if (received) item.done else item.expected)) }
    var expectedText by remember { mutableStateOf(Money.formatPlain(item.expected)) }
    var date by remember { mutableStateOf(if (received) item.date else LocalDate.now()) }
    val amount = Money.parse(amountText)
    val expected = Money.parse(expectedText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (received) "Recebimento" else "Registrar recebimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.title} • previsto para ${Fmt.date(item.date)}", fontWeight = FontWeight.SemiBold)
                MoneyField("Valor previsto", expectedText, { expectedText = it },
                    supporting = "Ajuste se esta ocorrência tiver valor diferente (renda variável).")
                MoneyField("Valor recebido", amountText, { amountText = it })
                DateField("Data do recebimento", date, { if (it != null) date = it })
                Spacer(Modifier.height(4.dp))
                Text("Receitas só entram no saldo realizado depois de recebidas.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && expected != null, onClick = {
                vm.run(block = { vm.repository.setReceived(item.refId, amount, date, expected) }) { if (it is OpResult.Success) onDismiss() }
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (received) TextButton(onClick = {
                    vm.run(block = { vm.repository.setReceived(item.refId, null, null) }) { if (it is OpResult.Success) onDismiss() }
                }) { Text("Desfazer", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
