package br.com.meucontrole.financeiro.domain.calc

import java.time.LocalDate
import java.time.YearMonth

object InstallmentPlanner {

    /**
     * Divide [totalCents] em [count] parcelas iguais. A diferença de centavos fica na ÚLTIMA parcela.
     * Ex.: 100,00 em 3 -> 33,33 / 33,33 / 33,34
     */
    fun splitEvenly(totalCents: Long, count: Int): List<Long> {
        require(count > 0) { "Quantidade de parcelas deve ser maior que zero" }
        require(totalCents >= 0) { "Valor não pode ser negativo" }
        val base = totalCents / count
        val last = totalCents - base * (count - 1)
        return List(count) { index -> if (index == count - 1) last else base }
    }

    /**
     * Datas de vencimento mensais. A primeira é exatamente [firstDueDate]; as seguintes usam [dueDay],
     * ajustado ao último dia do mês quando o mês é mais curto (ex.: dia 31 em fevereiro -> 28/29).
     */
    fun schedule(firstDueDate: LocalDate, count: Int, dueDay: Int): List<LocalDate> {
        require(count > 0)
        val day = dueDay.coerceIn(1, 31)
        val firstMonth = YearMonth.from(firstDueDate)
        return List(count) { i ->
            if (i == 0) firstDueDate
            else {
                val ym = firstMonth.plusMonths(i.toLong())
                ym.atDay(minOf(day, ym.lengthOfMonth()))
            }
        }
    }

    /**
     * Distribui [amount] sequencialmente sobre itens com saldo restante.
     * Retorna as alocações (id -> valor) e a sobra que não coube em nenhum item.
     */
    fun allocate(amount: Long, remainingByItem: List<Pair<Long, Long>>): Pair<List<Pair<Long, Long>>, Long> {
        var left = amount
        val out = mutableListOf<Pair<Long, Long>>()
        for ((id, remaining) in remainingByItem) {
            if (left <= 0) break
            if (remaining <= 0) continue
            val portion = minOf(left, remaining)
            out += id to portion
            left -= portion
        }
        return out to left
    }
}
