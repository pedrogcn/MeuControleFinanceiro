package br.com.meucontrole.financeiro.domain.calc

import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.Money
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val date: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val month: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale)
    private val shortMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM/yy", locale)

    fun date(d: LocalDate?): String = d?.format(date) ?: "—"
    fun month(m: YearMonth): String = m.format(month).replaceFirstChar { it.titlecase(locale) }
    fun shortMonth(m: YearMonth): String = m.format(shortMonth).replace(".", "").replaceFirstChar { it.titlecase(locale) }
}

data class MonthlyReport(
    val month: YearMonth,
    val summary: MonthSummary,
    val categories: List<CategoryTotal>,
    val history: List<MonthFlow>,
    val debts: List<DebtSummary>,
    val overdue: List<DueItem>,
    val paidInstallmentsInMonth: Int,
    val remainingInstallments: Int
)

object ReportBuilder {

    fun monthly(data: FinanceData, month: YearMonth, today: LocalDate, historyMonths: Int = 6): MonthlyReport {
        val engine = FinanceEngine(data, today)
        val summary = engine.monthSummary(month)
        val months = (historyMonths - 1 downTo 0).map { month.minusMonths(it.toLong()) }
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val paidInMonth = data.payments.count {
            !it.reversed && it.target == PaymentTarget.INSTALLMENT && !it.date.isBefore(start) && !it.date.isAfter(end)
        }
        return MonthlyReport(
            month = month,
            summary = summary,
            categories = engine.categoriesForMonth(month),
            history = engine.history(months),
            debts = engine.debtSummaries.filter { it.debt.status != DebtStatus.RENEGOCIADA },
            overdue = engine.overdueItems,
            paidInstallmentsInMonth = paidInMonth,
            remainingInstallments = engine.debtSummaries.sumOf { it.remainingCount }
        )
    }

    private fun esc(s: String): String {
        val needs = s.contains(';') || s.contains('"') || s.contains('\n')
        val v = s.replace("\"", "\"\"")
        return if (needs) "\"$v\"" else v
    }

    private fun row(vararg cells: String) = cells.joinToString(";") { esc(it) }

    /** CSV (separador ';', padrão Excel pt-BR) do relatório mensal. */
    fun monthlyCsv(r: MonthlyReport): String = buildString {
        val s = r.summary
        appendLine(row("Relatório", Fmt.month(r.month)))
        appendLine()
        appendLine(row("Indicador", "Valor (R$)"))
        appendLine(row("Receitas previstas", Money.formatPlain(s.salaryExpected + s.otherIncomeExpected)))
        appendLine(row("Total recebido", Money.formatPlain(s.incomeReceived)))
        appendLine(row("Total pago (contas + dívidas)", Money.formatPlain(s.totalPaidInMonth)))
        appendLine(row("Pago em contas", Money.formatPlain(s.billsPaid)))
        appendLine(row("Pago em dívidas", Money.formatPlain(s.installmentsPaid)))
        appendLine(row("Saldo devedor atualizado", Money.formatPlain(s.debtOutstandingTotal)))
        appendLine(row("Contas e parcelas vencidas", Money.formatPlain(s.overdueTotal)))
        appendLine(row("Parcelas pagas no mês", r.paidInstallmentsInMonth.toString()))
        appendLine(row("Parcelas restantes", r.remainingInstallments.toString()))
        appendLine(row("Saldo realizado no mês", Money.formatPlain(s.budget.realizedPeriod)))
        appendLine(row("Saldo projetado ao fim do mês", Money.formatPlain(s.budget.projected)))
        appendLine()
        appendLine(row("Categoria", "Previsto (R$)", "Pago (R$)"))
        r.categories.forEach { appendLine(row(it.category, Money.formatPlain(it.expected), Money.formatPlain(it.paid))) }
        appendLine()
        appendLine(row("Mês", "Recebido (R$)", "Pago (R$)", "Resultado (R$)", "Saldo acumulado (R$)"))
        r.history.forEach {
            appendLine(row(Fmt.shortMonth(it.month), Money.formatPlain(it.income), Money.formatPlain(it.expenses), Money.formatPlain(it.net), Money.formatPlain(it.cumulative)))
        }
        appendLine()
        appendLine(row("Dívida", "Status", "Pago (R$)", "Saldo (R$)", "Parcelas pagas", "Restantes", "Previsão de quitação"))
        r.debts.forEach {
            appendLine(row(it.debt.name, it.debt.status.label, Money.formatPlain(it.totalPaid), Money.formatPlain(it.outstanding),
                it.paidCount.toString(), it.remainingCount.toString(), Fmt.date(it.forecastPayoff)))
        }
        appendLine()
        appendLine(row("Vencidos", "Vencimento", "Restante (R$)"))
        r.overdue.forEach { appendLine(row("${it.title} - ${it.subtitle}", Fmt.date(it.date), Money.formatPlain(it.remaining))) }
    }

    /** CSV de dívidas, parcelas e pagamentos (inclui estornos, marcados). */
    fun debtsCsv(data: FinanceData, today: LocalDate): String = buildString {
        val engine = FinanceEngine(data, today)
        appendLine(row("Dívida", "Credor", "Categoria", "Status", "Parcela", "Vencimento", "Previsto (R$)", "Pago (R$)",
            "Encargos (R$)", "Descontos (R$)", "Restante (R$)", "Situação"))
        engine.debtSummaries.forEach { s ->
            s.installments.forEach { v ->
                appendLine(row(s.debt.name, s.debt.creditor, s.debt.category, s.debt.status.label, v.installment.label,
                    Fmt.date(v.installment.dueDate), Money.formatPlain(v.installment.amount), Money.formatPlain(v.settlement.paid),
                    Money.formatPlain(v.settlement.charges), Money.formatPlain(v.settlement.discount),
                    Money.formatPlain(if (s.debt.status == DebtStatus.ATIVA) v.settlement.remaining else 0), v.settlement.status.label))
            }
        }
        appendLine()
        appendLine(row("Pagamento", "Data", "Dívida/Conta", "Item", "Valor (R$)", "Encargos (R$)", "Descontos (R$)", "Forma", "Estornado", "Motivo do estorno", "Observações"))
        data.payments.sortedBy { it.date }.forEach { p ->
            val (owner, item) = when (p.target) {
                PaymentTarget.INSTALLMENT -> {
                    val inst = data.installments.firstOrNull { it.id == p.targetId }
                    (data.debts.firstOrNull { it.id == inst?.debtId }?.name ?: "?") to "Parcela ${inst?.label ?: "?"}"
                }
                PaymentTarget.DEBT_AGREEMENT -> (data.debts.firstOrNull { it.id == p.targetId }?.name ?: "?") to "Acordo de quitação"
                PaymentTarget.BILL -> {
                    val occ = data.billOccurrences.firstOrNull { it.id == p.targetId }
                    (data.billTemplates.firstOrNull { it.id == occ?.templateId }?.name ?: "?") to "Conta ${Fmt.date(occ?.dueDate)}"
                }
            }
            appendLine(row(p.id.toString(), Fmt.date(p.date), owner, item, Money.formatPlain(p.amount), Money.formatPlain(p.charges),
                Money.formatPlain(p.discount), p.method, if (p.reversed) "Sim" else "Não", p.reversalReason ?: "", p.notes))
        }
    }
}
