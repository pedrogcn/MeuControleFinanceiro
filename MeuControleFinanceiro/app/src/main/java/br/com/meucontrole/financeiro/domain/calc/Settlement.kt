package br.com.meucontrole.financeiro.domain.calc

import br.com.meucontrole.financeiro.domain.model.Payment
import java.time.LocalDate

enum class ItemStatus(val label: String) {
    PENDENTE("Pendente"),
    PARCIAL("Parcialmente paga"),
    PAGA("Paga"),
    VENCIDA("Vencida")
}

/**
 * Situação de um item a pagar (parcela ou conta) após aplicar seus pagamentos.
 * devido = previsto + encargos cobrados - descontos concedidos
 * restante = max(0, devido - pago)
 */
data class Settlement(
    val expected: Long,
    val paid: Long,
    val charges: Long,
    val discount: Long,
    val owed: Long,
    val remaining: Long,
    val excess: Long,
    val status: ItemStatus,
    val lastPaymentDate: LocalDate?
) {
    val isSettled: Boolean get() = remaining == 0L
}

object Settlements {

    fun of(expected: Long, payments: List<Payment>, dueDate: LocalDate, today: LocalDate): Settlement {
        val active = payments.filter { !it.reversed }
        val paid = active.sumOf { it.amount }
        val charges = active.sumOf { it.charges }
        val discount = active.sumOf { it.discount }
        val owed = (expected + charges - discount).coerceAtLeast(0)
        val remaining = (owed - paid).coerceAtLeast(0)
        val excess = (paid - owed).coerceAtLeast(0)
        val status = when {
            remaining == 0L -> ItemStatus.PAGA
            dueDate.isBefore(today) -> ItemStatus.VENCIDA
            paid > 0 -> ItemStatus.PARCIAL
            else -> ItemStatus.PENDENTE
        }
        return Settlement(
            expected = expected,
            paid = paid,
            charges = charges,
            discount = discount,
            owed = owed,
            remaining = remaining,
            excess = excess,
            status = status,
            lastPaymentDate = active.maxOfOrNull { it.date }
        )
    }
}
