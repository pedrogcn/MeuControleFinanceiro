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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import br.com.meucontrole.financeiro.data.repository.OpResult
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.InstallmentPlanner
import br.com.meucontrole.financeiro.domain.model.Categories
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.usecase.DebtDraft
import br.com.meucontrole.financeiro.domain.usecase.DebtEntryMode
import br.com.meucontrole.financeiro.domain.usecase.ManualInstallment
import br.com.meucontrole.financeiro.presentation.MainViewModel
import br.com.meucontrole.financeiro.presentation.components.AppScaffold
import br.com.meucontrole.financeiro.presentation.components.DateField
import br.com.meucontrole.financeiro.presentation.components.InfoCard
import br.com.meucontrole.financeiro.presentation.components.MoneyField
import br.com.meucontrole.financeiro.presentation.components.SectionTitle
import br.com.meucontrole.financeiro.presentation.components.SuggestField
import java.time.LocalDate

private class ManualRow(amount: String, date: LocalDate) {
    var amount by mutableStateOf(amount)
    var date by mutableStateOf(date)
}

@Composable
fun DebtFormScreen(vm: MainViewModel, nav: NavController, editId: Long, renegotiateId: Long) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val isEdit = editId > 0
    val isReneg = renegotiateId > 0

    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var creditor by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.debts.first()) }
    var originalText by remember { mutableStateOf("") }
    var downText by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var contractDate by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableStateOf(DebtEntryMode.ESTIMADA) }
    var countText by remember { mutableStateOf("") }
    var firstDue by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var dueDayText by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var interest by remember { mutableStateOf("") }
    var chargesText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val manual = remember { mutableStateListOf<ManualRow>() }
    var error by remember { mutableStateOf<String?>(null) }

    // Pré-preenchimento para edição ou renegociação
    LaunchedEffect(ui != null) {
        val u = ui ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        if (isEdit) u.data.debts.firstOrNull { it.id == editId }?.let { d ->
            name = d.name; creditor = d.creditor; category = d.category; originalText = Money.formatPlain(d.originalAmount)
            interest = d.interestRateInfo; notes = d.notes
            chargesText = if (d.registeredCharges > 0) Money.formatPlain(d.registeredCharges) else ""
            discountText = if (d.negotiatedDiscount > 0) Money.formatPlain(d.negotiatedDiscount) else ""
        }
        if (isReneg) u.engine.summaryOf(renegotiateId)?.let { s ->
            name = s.debt.name + " (renegociação)"; creditor = s.debt.creditor; category = s.debt.category
            originalText = Money.formatPlain(s.outstanding); totalText = Money.formatPlain(s.outstanding)
            notes = "Renegociação de \"${s.debt.name}\". Saldo anterior: ${Money.format(s.outstanding)}."
        }
        loaded = true
    }
    // Enquanto o usuário não editar o total contratado, ele acompanha o valor original
    var totalTouched by remember { mutableStateOf(false) }
    LaunchedEffect(originalText) { if (!isEdit && !isReneg && !totalTouched) totalText = originalText }

    val title = when { isEdit -> "Editar dívida"; isReneg -> "Renegociar dívida"; else -> "Nova dívida" }

    fun save() {
        error = null
        val original = Money.parse(originalText.ifBlank { "0" })
        val charges = if (chargesText.isBlank()) 0L else Money.parse(chargesText)
        val discount = if (discountText.isBlank()) 0L else Money.parse(discountText)
        if (original == null || charges == null || discount == null) { error = "Verifique os valores informados."; return }
        if (isEdit) {
            val current = ui?.data?.debts?.firstOrNull { it.id == editId } ?: return
            vm.run(block = {
                vm.repository.updateDebtInfo(current.copy(name = name, creditor = creditor, category = category, originalAmount = original,
                    interestRateInfo = interest, registeredCharges = charges, negotiatedDiscount = discount, notes = notes))
            }) { if (it is OpResult.Success) nav.popBackStack() }
            return
        }
        val down = if (downText.isBlank()) 0L else Money.parse(downText)
        val total = Money.parse(totalText)
        val dueDay = dueDayText.toIntOrNull()
        if (down == null) { error = "Entrada inválida."; return }
        if (dueDay == null || dueDay !in 1..31) { error = "Dia de vencimento deve ser de 1 a 31."; return }
        val manualList = if (mode == DebtEntryMode.MANUAL) manual.map {
            val v = Money.parse(it.amount) ?: run { error = "Há parcela com valor inválido."; return }
            ManualInstallment(v, it.date)
        } else emptyList()
        if (mode == DebtEntryMode.ESTIMADA && total == null) { error = "Informe o valor total contratado."; return }
        val draft = DebtDraft(
            name = name, creditor = creditor, category = category, originalAmount = original, downPayment = down,
            contractDate = contractDate, totalContracted = total ?: 0, mode = mode,
            installmentCount = countText.toIntOrNull() ?: 0, firstDueDate = firstDue, dueDay = dueDay,
            manualInstallments = manualList, interestRateInfo = interest, registeredCharges = charges,
            negotiatedDiscount = discount, notes = notes
        )
        vm.run(block = { if (isReneg) vm.repository.renegotiate(renegotiateId, draft) else vm.repository.createDebt(draft) }) { r ->
            if (r is OpResult.Success) {
                nav.popBackStack()
                if (isReneg) nav.popBackStack()
                nav.navigate(br.com.meucontrole.financeiro.presentation.navigation.Routes.debt(r.id))
            } else if (r is OpResult.Failure) error = r.message
        }
    }

    AppScaffold(title = title, onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isReneg) InfoCard {
                Text("A dívida atual será marcada como RENEGOCIADA, com todo o histórico preservado. Informe abaixo as condições do novo acordo.",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nome da dívida *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(creditor, { creditor = it }, label = { Text("Credor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SuggestField("Categoria", category, { category = it }, Categories.debts)
            MoneyField("Valor original", originalText, { originalText = it }, required = false,
                supporting = "Valor da compra/empréstimo, antes de juros (informativo).")

            if (!isEdit) {
                SectionTitle("Condições de pagamento")
                MoneyField("Entrada paga", downText, { downText = it }, required = false)
                DateField("Data da contratação", contractDate, { if (it != null) contractDate = it })
                Text("Como deseja informar as parcelas?", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = mode == DebtEntryMode.ESTIMADA, onClick = { mode = DebtEntryMode.ESTIMADA },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Dividir valor") }
                    SegmentedButton(selected = mode == DebtEntryMode.MANUAL, onClick = { mode = DebtEntryMode.MANUAL },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Uma a uma") }
                }
                if (mode == DebtEntryMode.ESTIMADA) {
                    MoneyField("Valor total contratado (com entrada)", totalText, { totalText = it; totalTouched = true },
                        supporting = "Total que você vai pagar, incluindo a entrada.")
                    OutlinedTextField(countText, { v -> countText = v.filter { it.isDigit() }.take(3) }, label = { Text("Quantidade de parcelas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    DateField("Vencimento da 1ª parcela", firstDue, { if (it != null) { firstDue = it; dueDayText = it.dayOfMonth.toString() } })
                    OutlinedTextField(dueDayText, { v -> dueDayText = v.filter { it.isDigit() }.take(2) }, label = { Text("Dia de vencimento (demais parcelas)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    // Pré-visualização da divisão
                    val total = Money.parse(totalText)
                    val down = if (downText.isBlank()) 0L else Money.parse(downText)
                    val count = countText.toIntOrNull()
                    if (total != null && down != null && count != null && count in 1..600 && total > down) {
                        val values = InstallmentPlanner.splitEvenly(total - down, count)
                        InfoCard {
                            Text("Saldo a parcelar: ${Money.format(total - down)}", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (values.first() == values.last()) "$count × ${Money.format(values.first())}"
                                else "${count - 1} × ${Money.format(values.first())} + última de ${Money.format(values.last())} (ajuste de centavos)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("Valores estimados. Se o credor cobra parcelas diferentes, use \"Uma a uma\".", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    OutlinedTextField(dueDayText, { v -> dueDayText = v.filter { it.isDigit() }.take(2) }, label = { Text("Dia de vencimento habitual") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    manual.forEachIndexed { i, row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", Modifier.padding(end = 4.dp))
                            Column(Modifier.weight(1f)) {
                                MoneyField("Valor", row.amount, { row.amount = it })
                                DateField("Vencimento", row.date, { if (it != null) row.date = it })
                            }
                            IconButton(onClick = { manual.removeAt(i) }) { Icon(Icons.Filled.Delete, contentDescription = "Remover parcela ${i + 1}") }
                        }
                    }
                    OutlinedButton(onClick = {
                        val last = manual.lastOrNull()
                        manual.add(ManualRow(last?.amount ?: "", last?.date?.plusMonths(1) ?: firstDue))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null); Text(" Adicionar parcela")
                    }
                    val sum = manual.sumOf { Money.parse(it.amount) ?: 0L }
                    val down = if (downText.isBlank()) 0L else (Money.parse(downText) ?: 0L)
                    Text("Soma das parcelas: ${Money.format(sum)} • Total com entrada: ${Money.format(sum + down)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (manual.isNotEmpty()) Text("Ordenadas por vencimento ao salvar. Primeira: ${Fmt.date(manual.minOf { it.date })}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionTitle("Juros e encargos (opcional)")
            OutlinedTextField(interest, { interest = it }, label = { Text("Taxa de juros informada (ex.: 2,5% a.m.)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MoneyField("Multa/encargos informados", chargesText, { chargesText = it }, required = false,
                supporting = "Apenas registro. O app não calcula nem inventa juros.")
            MoneyField("Descontos negociados", discountText, { discountText = it }, required = false)
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            if (isEdit) Text("Para alterar parcelas, use \"Ajustar valor/vencimento\" em cada parcela ou renegocie a dívida.",
                style = MaterialTheme.typography.bodySmall)

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { save() }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Salvando…" else "Salvar")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
