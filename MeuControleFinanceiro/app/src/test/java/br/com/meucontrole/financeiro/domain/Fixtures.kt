package br.com.meucontrole.financeiro.domain

import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.domain.usecase.DebtDraft
import br.com.meucontrole.financeiro.domain.usecase.DebtEntryMode
import br.com.meucontrole.financeiro.domain.usecase.DebtFactory
import br.com.meucontrole.financeiro.domain.usecase.Outcome
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlan
import br.com.meucontrole.financeiro.domain.usecase.PaymentPlanner
import br.com.meucontrole.financeiro.domain.usecase.PaymentRequest
import br.com.meucontrole.financeiro.domain.usecase.DebtStatusRules
import java.time.LocalDate
import java.util.UUID

/** Simula o banco em memória: atribui IDs e aplica planos como o repositório faria. */
object Fixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 19)

    fun draft(
        total: Long,
        count: Int,
        down: Long = 0,
        first: LocalDate = LocalDate.of(2026, 1, 10),
        name: String = "Dívida teste"
    ) = DebtDraft(
        name = name, creditor = "Banco", category = "Empréstimo", originalAmount = total,
        downPayment = down, contractDate = LocalDate.of(2025, 12, 1), totalContracted = total,
        mode = DebtEntryMode.ESTIMADA, installmentCount = count, firstDueDate = first, dueDay = first.dayOfMonth
    )

    fun addDebt(data: FinanceData, draft: DebtDraft): Pair<FinanceData, Long> {
        val (debt, inst) = (DebtFactory.build(draft, 0) as Outcome.Ok).value
        val debtId = (data.debts.maxOfOrNull { it.id } ?: 0) + 1
        var instId = (data.installments.maxOfOrNull { it.id } ?: 0)
        val newInst = inst.map { it.copy(id = ++instId, debtId = debtId) }
        return data.copy(debts = data.debts + debt.copy(id = debtId), installments = data.installments + newInst) to debtId
    }

    /** Aplica um pagamento pelo planejador e recalcula o status da dívida (como o repositório). */
    fun pay(data: FinanceData, req: PaymentRequest, today: LocalDate = TODAY): FinanceData {
        val plan = PaymentPlanner.plan(req, data, today, 0)
        check(plan is PaymentPlan.Ready) { "Plano não pronto: $plan" }
        return applyPayments(data, plan.payments, today)
    }

    fun applyPayments(data: FinanceData, payments: List<Payment>, today: LocalDate = TODAY): FinanceData {
        var nextId = (data.payments.maxOfOrNull { it.id } ?: 0)
        val withIds = payments.map { it.copy(id = ++nextId) }
        return recomputeStatuses(data.copy(payments = data.payments + withIds), today)
    }

    fun recomputeStatuses(data: FinanceData, today: LocalDate = TODAY): FinanceData =
        data.copy(debts = data.debts.map { it.copy(status = DebtStatusRules.recompute(it, data, today)) })

    fun installmentReq(id: Long, amount: Long, date: LocalDate = TODAY, requestId: String = UUID.randomUUID().toString()) =
        PaymentRequest(requestId = requestId, target = PaymentTarget.INSTALLMENT, targetId = id, amount = amount, date = date)

    fun installmentsOf(data: FinanceData, debtId: Long) = data.installments.filter { it.debtId == debtId }.sortedBy { it.number }
}
