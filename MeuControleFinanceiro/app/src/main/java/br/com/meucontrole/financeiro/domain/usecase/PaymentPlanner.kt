package br.com.meucontrole.financeiro.domain.usecase

import br.com.meucontrole.financeiro.domain.calc.FinanceEngine
import br.com.meucontrole.financeiro.domain.calc.InstallmentPlanner
import br.com.meucontrole.financeiro.domain.calc.Settlements
import br.com.meucontrole.financeiro.domain.model.Debt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.Installment
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import java.time.LocalDate

/** O que fazer quando o valor pago supera o saldo do item. */
enum class OverpayMode {
    /** Não decidir: o planejador devolve [PaymentPlan.NeedsDecision]. */
    ASK,
    /** Abater o excedente nas próximas parcelas da mesma dívida. */
    APPLY_TO_NEXT,
    /** Registrar o excedente como encargos (juros/multa) efetivamente cobrados. */
    REGISTER_AS_CHARGES,
    /** Registrar apenas o valor necessário para quitar o item. */
    LIMIT_TO_BALANCE
}

data class PaymentRequest(
    val requestId: String,
    val target: PaymentTarget,
    val targetId: Long,
    val amount: Long,
    val charges: Long = 0,
    val discount: Long = 0,
    val date: LocalDate,
    val method: String = "",
    val notes: String = "",
    val overpayMode: OverpayMode = OverpayMode.ASK
)

sealed class PaymentPlan {
    /** Pagamentos a gravar (em uma única transação). */
    data class Ready(val payments: List<Payment>) : PaymentPlan()

    /**
     * Valor maior que o saldo: o usuário precisa escolher.
     * [excess] = quanto excede o item; [excessBeyondDebt] = quanto excede a dívida inteira.
     */
    data class NeedsDecision(
        val itemRemaining: Long,
        val excess: Long,
        val canApplyToNext: Boolean,
        val excessBeyondDebt: Long
    ) : PaymentPlan()

    data class Invalid(val message: String) : PaymentPlan()
}

object PaymentPlanner {

    fun validateBasics(amount: Long, charges: Long, discount: Long): String? = when {
        amount < 0 || charges < 0 || discount < 0 -> "Valores não podem ser negativos."
        amount == 0L && discount == 0L -> "Informe um valor maior que zero."
        else -> null
    }

    fun plan(req: PaymentRequest, data: FinanceData, today: LocalDate, now: Long): PaymentPlan {
        validateBasics(req.amount, req.charges, req.discount)?.let { return PaymentPlan.Invalid(it) }
        if (data.payments.any { it.requestId == req.requestId }) {
            return PaymentPlan.Invalid("Este pagamento já foi registrado.")
        }
        return when (req.target) {
            PaymentTarget.INSTALLMENT -> planInstallment(req, data, today, now)
            PaymentTarget.BILL -> planBill(req, data, today, now)
            PaymentTarget.DEBT_AGREEMENT -> planAgreement(req, data, today, now)
        }
    }

    private fun base(req: PaymentRequest, now: Long, debtId: Long?) = Payment(
        requestId = req.requestId,
        target = req.target,
        targetId = req.targetId,
        debtId = debtId,
        amount = req.amount,
        charges = req.charges,
        discount = req.discount,
        date = req.date,
        method = req.method,
        notes = req.notes.trim(),
        createdAt = now
    )

    private fun planInstallment(req: PaymentRequest, data: FinanceData, today: LocalDate, now: Long): PaymentPlan {
        val inst: Installment = data.installments.firstOrNull { it.id == req.targetId }
            ?: return PaymentPlan.Invalid("Parcela não encontrada.")
        val debt: Debt = data.debts.firstOrNull { it.id == inst.debtId }
            ?: return PaymentPlan.Invalid("Dívida não encontrada.")
        if (debt.status != DebtStatus.ATIVA) return PaymentPlan.Invalid("A dívida está ${debt.status.label.lowercase()}; não aceita novos pagamentos.")

        val current = Settlements.of(inst.amount, data.payments.filter { it.target == PaymentTarget.INSTALLMENT && it.targetId == inst.id }, inst.dueDate, today)
        val itemDue = (current.remaining + req.charges - req.discount).coerceAtLeast(0)
        if (current.remaining == 0L && req.charges == 0L) return PaymentPlan.Invalid("Esta parcela já está paga.")
        val payment = base(req, now, debt.id)
        if (req.amount <= itemDue) return PaymentPlan.Ready(listOf(payment))

        val excess = req.amount - itemDue
        val engine = FinanceEngine(data, today)
        val nextOnes = engine.summaryOf(debt.id)!!.installments
            .filter { it.installment.id != inst.id && !it.settlement.isSettled && it.installment.number > inst.number }
            .map { it.installment.id to it.settlement.remaining }
        val otherOpen = engine.summaryOf(debt.id)!!.installments
            .filter { it.installment.id != inst.id && !it.settlement.isSettled }
            .sumOf { it.settlement.remaining }
        val beyondDebt = (excess - otherOpen).coerceAtLeast(0)

        return when (req.overpayMode) {
            OverpayMode.ASK -> PaymentPlan.NeedsDecision(itemDue, excess, nextOnes.isNotEmpty(), beyondDebt)
            OverpayMode.LIMIT_TO_BALANCE ->
                if (itemDue == 0L) PaymentPlan.Invalid("Não há saldo a pagar nesta parcela.")
                else PaymentPlan.Ready(listOf(payment.copy(amount = itemDue)))
            OverpayMode.REGISTER_AS_CHARGES -> PaymentPlan.Ready(listOf(payment.copy(charges = req.charges + excess)))
            OverpayMode.APPLY_TO_NEXT -> {
                val (allocs, left) = InstallmentPlanner.allocate(excess, nextOnes)
                if (left > 0) return PaymentPlan.Invalid(
                    "O valor excede o saldo de toda a dívida em ${Money.format(left)}. Registre o excedente como encargos ou limite o valor."
                )
                val first = payment.copy(amount = itemDue, notes = (req.notes + " (pagamento com excedente abatido nas próximas parcelas)").trim())
                val extra = allocs.mapIndexed { i, (id, value) ->
                    payment.copy(
                        requestId = "${req.requestId}#${i + 1}",
                        targetId = id,
                        amount = value,
                        charges = 0,
                        discount = 0,
                        notes = "Excedente da parcela ${inst.label} (antecipação)"
                    )
                }
                PaymentPlan.Ready(listOf(first) + extra)
            }
        }
    }

    private fun planBill(req: PaymentRequest, data: FinanceData, today: LocalDate, now: Long): PaymentPlan {
        val occ = data.billOccurrences.firstOrNull { it.id == req.targetId }
            ?: return PaymentPlan.Invalid("Conta não encontrada.")
        val current = Settlements.of(occ.expectedAmount, data.payments.filter { it.target == PaymentTarget.BILL && it.targetId == occ.id }, occ.dueDate, today)
        if (current.remaining == 0L && req.charges == 0L) return PaymentPlan.Invalid("Esta conta já está paga.")
        val itemDue = (current.remaining + req.charges - req.discount).coerceAtLeast(0)
        val payment = base(req, now, null)
        if (req.amount <= itemDue) return PaymentPlan.Ready(listOf(payment))
        val excess = req.amount - itemDue
        return when (req.overpayMode) {
            OverpayMode.ASK, OverpayMode.APPLY_TO_NEXT -> PaymentPlan.NeedsDecision(itemDue, excess, false, excess)
            OverpayMode.LIMIT_TO_BALANCE -> PaymentPlan.Ready(listOf(payment.copy(amount = itemDue)))
            OverpayMode.REGISTER_AS_CHARGES -> PaymentPlan.Ready(listOf(payment.copy(charges = req.charges + excess)))
        }
    }

    /**
     * Quitação por acordo: paga-se [PaymentRequest.amount] e o restante do saldo devedor
     * é registrado como desconto concedido. A dívida passa a QUITADA.
     */
    private fun planAgreement(req: PaymentRequest, data: FinanceData, today: LocalDate, now: Long): PaymentPlan {
        val debt = data.debts.firstOrNull { it.id == req.targetId } ?: return PaymentPlan.Invalid("Dívida não encontrada.")
        if (debt.status != DebtStatus.ATIVA) return PaymentPlan.Invalid("Só é possível quitar por acordo uma dívida ativa.")
        val outstanding = FinanceEngine(data, today).summaryOf(debt.id)!!.outstanding
        if (outstanding == 0L) return PaymentPlan.Invalid("A dívida não possui saldo em aberto.")
        if (req.amount <= 0) return PaymentPlan.Invalid("Informe o valor pago no acordo.")
        val payment = base(req, now, debt.id)
        if (req.amount <= outstanding) {
            return PaymentPlan.Ready(listOf(payment.copy(charges = 0, discount = outstanding - req.amount)))
        }
        val excess = req.amount - outstanding
        return when (req.overpayMode) {
            OverpayMode.ASK, OverpayMode.APPLY_TO_NEXT -> PaymentPlan.NeedsDecision(outstanding, excess, false, excess)
            OverpayMode.LIMIT_TO_BALANCE -> PaymentPlan.Ready(listOf(payment.copy(amount = outstanding, charges = 0, discount = 0)))
            OverpayMode.REGISTER_AS_CHARGES -> PaymentPlan.Ready(listOf(payment.copy(charges = excess, discount = 0)))
        }
    }
}

/** Estado da dívida derivado dos pagamentos. Chamado após qualquer lançamento, edição ou estorno. */
object DebtStatusRules {
    fun recompute(debt: Debt, data: FinanceData, today: LocalDate): DebtStatus {
        if (debt.status == DebtStatus.RENEGOCIADA) return DebtStatus.RENEGOCIADA
        val hasAgreement = data.payments.any { !it.reversed && it.target == PaymentTarget.DEBT_AGREEMENT && it.targetId == debt.id }
        if (hasAgreement) return DebtStatus.QUITADA
        val asActive = data.copy(debts = data.debts.map { if (it.id == debt.id) it.copy(status = DebtStatus.ATIVA) else it })
        val summary = FinanceEngine(asActive, today).summaryOf(debt.id) ?: return debt.status
        return if (summary.installments.isNotEmpty() && summary.outstanding == 0L) DebtStatus.QUITADA else DebtStatus.ATIVA
    }
}

object PaymentEditRules {

    fun reverse(payment: Payment, reason: String, now: Long): Outcome<Payment> {
        if (payment.reversed) return Outcome.Error("Este pagamento já foi estornado.")
        if (reason.isBlank()) return Outcome.Error("Informe o motivo do estorno.")
        return Outcome.Ok(payment.copy(reversed = true, reversalReason = reason.trim(), reversedAt = now))
    }

    /**
     * Corrige um lançamento. O novo valor não pode ultrapassar o devido do item
     * (considerando os demais pagamentos); para valores maiores, estorne e lance novamente.
     */
    fun edit(
        original: Payment,
        amount: Long,
        charges: Long,
        discount: Long,
        date: LocalDate,
        method: String,
        notes: String,
        data: FinanceData,
        now: Long
    ): Outcome<Payment> {
        if (original.reversed) return Outcome.Error("Pagamentos estornados não podem ser editados.")
        PaymentPlanner.validateBasics(amount, charges, discount)?.let { return Outcome.Error(it) }
        val others = data.payments.filter { it.id != original.id && it.target == original.target && it.targetId == original.targetId && !it.reversed }
        val expected = when (original.target) {
            PaymentTarget.INSTALLMENT -> data.installments.firstOrNull { it.id == original.targetId }?.amount
            PaymentTarget.BILL -> data.billOccurrences.firstOrNull { it.id == original.targetId }?.expectedAmount
            PaymentTarget.DEBT_AGREEMENT -> null
        }
        if (expected != null) {
            val owed = expected + others.sumOf { it.charges } + charges - others.sumOf { it.discount } - discount
            val paid = others.sumOf { it.amount } + amount
            if (paid > owed.coerceAtLeast(0)) {
                return Outcome.Error("O valor corrigido ultrapassa o devido em ${Money.format(paid - owed)}. Informe o excedente como encargos ou estorne e lance novamente.")
            }
        }
        return Outcome.Ok(
            original.copy(amount = amount, charges = charges, discount = discount, date = date, method = method, notes = notes.trim(), editedAt = now)
        )
    }
}
