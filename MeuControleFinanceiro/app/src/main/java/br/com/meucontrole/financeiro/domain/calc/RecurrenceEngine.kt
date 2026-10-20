package br.com.meucontrole.financeiro.domain.calc

import br.com.meucontrole.financeiro.domain.model.BillOccurrence
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.IncomeFrequency
import br.com.meucontrole.financeiro.domain.model.IncomeOccurrence
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Recurrence
import java.time.LocalDate
import java.time.YearMonth

/**
 * Geração determinística de datas recorrentes. A mesma entrada sempre gera as mesmas datas,
 * e a geração de ocorrências ignora datas já existentes — abrir o app várias vezes não duplica nada.
 */
object RecurrenceEngine {

    fun clampDay(ym: YearMonth, day: Int): LocalDate = ym.atDay(minOf(day.coerceAtLeast(1), ym.lengthOfMonth()))

    fun billDueDates(t: BillTemplate, from: LocalDate, to: LocalDate): List<LocalDate> {
        val end = listOfNotNull(to, t.endDate).minOrNull()!!
        val start = t.startDate
        if (end.isBefore(start)) return emptyList()
        val raw: List<LocalDate> = when (t.recurrence) {
            Recurrence.NENHUMA -> listOf(start)
            Recurrence.SEMANAL -> generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
            Recurrence.MENSAL -> monthly(start, end, start.dayOfMonth)
            Recurrence.ANUAL -> {
                val out = mutableListOf<LocalDate>()
                var year = start.year
                while (year <= end.year) {
                    val ym = YearMonth.of(year, start.month)
                    out += clampDay(ym, start.dayOfMonth)
                    year++
                }
                out
            }
        }
        return raw.filter { !it.isBefore(start) && !it.isBefore(from) && !it.isAfter(end) }
    }

    fun incomeDates(s: IncomeSource, from: LocalDate, to: LocalDate): List<LocalDate> {
        val end = listOfNotNull(to, s.endDate).minOrNull()!!
        val start = s.startDate
        if (end.isBefore(start)) return emptyList()
        val raw: List<LocalDate> = when (s.frequency) {
            IncomeFrequency.EVENTUAL -> listOf(start)
            IncomeFrequency.SEMANAL -> generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
            IncomeFrequency.MENSAL -> monthly(start, end, start.dayOfMonth)
            IncomeFrequency.QUINZENAL -> {
                // Duas datas por mês: o dia inicial e 15 dias depois (ou antes), ex.: 5 e 20; 15 e 30.
                val d = start.dayOfMonth
                val (d1, d2) = if (d <= 15) d to d + 15 else (d - 15) to d
                val out = mutableListOf<LocalDate>()
                var ym = YearMonth.from(start)
                val last = YearMonth.from(end)
                while (!ym.isAfter(last)) {
                    out += clampDay(ym, d1)
                    out += clampDay(ym, d2)
                    ym = ym.plusMonths(1)
                }
                out.distinct()
            }
        }
        return raw.filter { !it.isBefore(start) && !it.isBefore(from) && !it.isAfter(end) }
    }

    private fun monthly(start: LocalDate, end: LocalDate, day: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var ym = YearMonth.from(start)
        val last = YearMonth.from(end)
        while (!ym.isAfter(last)) {
            out += clampDay(ym, day)
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** Ocorrências de contas que ainda NÃO existem até [horizon]. Nunca retorna duplicatas. */
    fun missingBillOccurrences(
        templates: List<BillTemplate>,
        existing: List<BillOccurrence>,
        horizon: LocalDate
    ): List<BillOccurrence> {
        val keys = existing.map { it.templateId to it.dueDate }.toHashSet()
        val out = mutableListOf<BillOccurrence>()
        for (t in templates.filter { it.active }) {
            for (date in billDueDates(t, t.startDate, horizon)) {
                val key = t.id to date
                if (keys.add(key)) out += BillOccurrence(templateId = t.id, dueDate = date, expectedAmount = t.expectedAmount)
            }
        }
        return out
    }

    /** Ocorrências de receita que ainda NÃO existem até [horizon]. Nunca retorna duplicatas. */
    fun missingIncomeOccurrences(
        sources: List<IncomeSource>,
        existing: List<IncomeOccurrence>,
        horizon: LocalDate
    ): List<IncomeOccurrence> {
        val keys = existing.map { it.sourceId to it.date }.toHashSet()
        val out = mutableListOf<IncomeOccurrence>()
        for (s in sources.filter { it.active }) {
            for (date in incomeDates(s, s.startDate, horizon)) {
                if (keys.add(s.id to date)) out += IncomeOccurrence(sourceId = s.id, date = date, expectedAmount = s.netAmount)
            }
        }
        return out
    }

    /** Horizonte padrão de geração: fim do mês, dois meses à frente da data de referência. */
    fun defaultHorizon(reference: LocalDate): LocalDate = YearMonth.from(reference).plusMonths(2).atEndOfMonth()
}
