package br.com.meucontrole.financeiro.domain.usecase

import br.com.meucontrole.financeiro.domain.calc.InstallmentPlanner
import br.com.meucontrole.financeiro.domain.model.Debt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.Installment
import java.time.LocalDate

sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Error(val message: String) : Outcome<Nothing>()
}

data class ManualInstallment(val amount: Long, val dueDate: LocalDate)

enum class DebtEntryMode { ESTIMADA, MANUAL }

/** Rascunho vindo do formulário. Tudo em centavos. */
data class DebtDraft(
    val name: String,
    val creditor: String,
    val category: String,
    val originalAmount: Long,
    val downPayment: Long,
    val contractDate: LocalDate,
    /** Valor total contratado (inclui entrada). Na modalidade MANUAL é recalculado: entrada + soma das parcelas. */
    val totalContracted: Long,
    val mode: DebtEntryMode,
    val installmentCount: Int,
    val firstDueDate: LocalDate,
    val dueDay: Int,
    val manualInstallments: List<ManualInstallment> = emptyList(),
    val interestRateInfo: String = "",
    val registeredCharges: Long = 0,
    val negotiatedDiscount: Long = 0,
    val notes: String = "",
    val renegotiatedFromId: Long? = null
)

object DebtFactory {

    /** Valida e monta a dívida e suas parcelas. Os ids são definidos pelo banco. */
    fun build(d: DebtDraft, now: Long): Outcome<Pair<Debt, List<Installment>>> {
        if (d.name.isBlank()) return Outcome.Error("Informe o nome da dívida.")
        if (d.originalAmount < 0 || d.downPayment < 0 || d.totalContracted < 0) return Outcome.Error("Valores não podem ser negativos.")
        if (d.registeredCharges < 0 || d.negotiatedDiscount < 0) return Outcome.Error("Encargos e descontos não podem ser negativos.")
        if (d.dueDay !in 1..31) return Outcome.Error("Dia de vencimento deve estar entre 1 e 31.")

        val installments: List<Installment>
        val total: Long
        when (d.mode) {
            DebtEntryMode.ESTIMADA -> {
                if (d.installmentCount !in 1..600) return Outcome.Error("Quantidade de parcelas deve estar entre 1 e 600.")
                if (d.downPayment > d.totalContracted) return Outcome.Error("A entrada não pode ser maior que o valor total contratado.")
                val toFinance = d.totalContracted - d.downPayment
                if (toFinance <= 0) return Outcome.Error("Não há saldo a parcelar (total contratado menos entrada).")
                val values = InstallmentPlanner.splitEvenly(toFinance, d.installmentCount)
                val dates = InstallmentPlanner.schedule(d.firstDueDate, d.installmentCount, d.dueDay)
                installments = values.mapIndexed { i, v ->
                    Installment(debtId = 0, number = i + 1, totalCount = d.installmentCount, amount = v, dueDate = dates[i])
                }
                total = d.totalContracted
            }
            DebtEntryMode.MANUAL -> {
                val list = d.manualInstallments
                if (list.isEmpty()) return Outcome.Error("Adicione ao menos uma parcela.")
                if (list.size > 600) return Outcome.Error("Máximo de 600 parcelas.")
                if (list.any { it.amount <= 0 }) return Outcome.Error("Toda parcela precisa ter valor maior que zero.")
                val sorted = list.sortedBy { it.dueDate }
                installments = sorted.mapIndexed { i, m ->
                    Installment(debtId = 0, number = i + 1, totalCount = sorted.size, amount = m.amount, dueDate = m.dueDate)
                }
                total = d.downPayment + sorted.sumOf { it.amount }
            }
        }
        val debt = Debt(
            name = d.name.trim(),
            creditor = d.creditor.trim(),
            category = d.category.ifBlank { "Outros" },
            originalAmount = d.originalAmount,
            downPayment = d.downPayment,
            contractDate = d.contractDate,
            totalContracted = total,
            installmentCount = installments.size,
            installmentAmount = installments.first().amount,
            firstDueDate = installments.first().dueDate,
            dueDay = d.dueDay,
            interestRateInfo = d.interestRateInfo.trim(),
            registeredCharges = d.registeredCharges,
            negotiatedDiscount = d.negotiatedDiscount,
            notes = d.notes.trim(),
            status = DebtStatus.ATIVA,
            renegotiatedFromId = d.renegotiatedFromId,
            createdAt = now
        )
        return Outcome.Ok(debt to installments)
    }
}
