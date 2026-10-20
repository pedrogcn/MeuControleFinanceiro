package br.com.meucontrole.financeiro.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import br.com.meucontrole.financeiro.domain.calc.Fmt
import br.com.meucontrole.financeiro.domain.calc.MonthlyReport
import br.com.meucontrole.financeiro.domain.model.Money
import java.io.IOException

object FileExport {

    /** Grava texto UTF-8 (com BOM para CSV abrir corretamente no Excel). */
    @Throws(IOException::class)
    fun writeText(context: Context, uri: Uri, text: String, withBom: Boolean = false) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            if (withBom) out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Não foi possível abrir o arquivo de destino.")
    }

    @Throws(IOException::class)
    fun readText(context: Context, uri: Uri, maxBytes: Int = 50 * 1024 * 1024): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Não foi possível ler o arquivo.")
        if (bytes.size > maxBytes) throw IOException("Arquivo grande demais para ser um backup válido.")
        return String(bytes, Charsets.UTF_8).removePrefix("﻿")
    }
}

/** Gera um PDF A4 simples do relatório mensal, usando apenas APIs do Android (offline). */
object PdfReport {

    private const val W = 595
    private const val H = 842
    private const val M = 40f

    @Throws(IOException::class)
    fun write(context: Context, uri: Uri, r: MonthlyReport) {
        val doc = PdfDocument()
        try {
            val writer = Writer(doc)
            val s = r.summary
            writer.title("Meu Controle Financeiro")
            writer.subtitle("Relatório de ${Fmt.month(r.month)}")
            writer.space()
            writer.header("Resumo")
            writer.row("Receitas previstas", Money.format(s.salaryExpected + s.otherIncomeExpected))
            writer.row("Total recebido", Money.format(s.incomeReceived))
            writer.row("Total pago (contas + dívidas)", Money.format(s.totalPaidInMonth))
            writer.row("  Pago em contas", Money.format(s.billsPaid))
            writer.row("  Pago em dívidas", Money.format(s.installmentsPaid))
            writer.row("Saldo devedor atualizado", Money.format(s.debtOutstandingTotal))
            writer.row("Contas e parcelas vencidas", Money.format(s.overdueTotal))
            writer.row("Parcelas pagas no mês / restantes", "${r.paidInstallmentsInMonth} / ${r.remainingInstallments}")
            writer.row("Saldo realizado no mês", Money.format(s.budget.realizedPeriod))
            writer.row("Saldo projetado ao fim do mês", Money.format(s.budget.projected))
            writer.space()
            writer.header("Despesas por categoria (previsto / pago)")
            if (r.categories.isEmpty()) writer.text("Sem despesas no mês.")
            r.categories.forEach { writer.row(it.category, "${Money.format(it.expected)} / ${Money.format(it.paid)}") }
            writer.space()
            writer.header("Receitas x despesas (realizado)")
            r.history.forEach {
                writer.row(Fmt.shortMonth(it.month), "+${Money.format(it.income)}  −${Money.format(it.expenses)}  saldo ${Money.format(it.cumulative)}")
            }
            writer.space()
            writer.header("Dívidas e previsão de quitação")
            if (r.debts.isEmpty()) writer.text("Nenhuma dívida cadastrada.")
            r.debts.forEach {
                writer.row(
                    "${it.debt.name} (${it.debt.status.label})",
                    "saldo ${Money.format(it.outstanding)} • ${it.paidCount}/${it.installments.size} • ${Fmt.date(it.forecastPayoff)}"
                )
            }
            if (r.overdue.isNotEmpty()) {
                writer.space()
                writer.header("Vencidos")
                r.overdue.forEach { writer.row("${it.title} — ${it.subtitle}", "${Fmt.date(it.date)} • ${Money.format(it.remaining)}") }
            }
            writer.space()
            writer.small("Gerado localmente pelo app. Valores realizados consideram apenas lançamentos efetivos; projeções são estimativas.")
            writer.finish()

            context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) }
                ?: throw IOException("Não foi possível abrir o arquivo de destino.")
        } finally {
            doc.close()
        }
    }

    private class Writer(private val doc: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val body = Paint().apply { textSize = 10.5f; isAntiAlias = true }
        private val bold = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        private val titleP = Paint(bold).apply { textSize = 18f }
        private val subP = Paint(body).apply { textSize = 13f }
        private val smallP = Paint(body).apply { textSize = 8.5f; color = 0xFF666666.toInt() }
        private val line = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.7f }

        init { newPage() }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
        }

        private fun ensure(h: Float) { if (y + h > H - M) newPage() }
        private val canvas get() = page!!.canvas

        fun title(t: String) { ensure(24f); y += 18f; canvas.drawText(t, M, y, titleP); y += 6f }
        fun subtitle(t: String) { ensure(18f); y += 14f; canvas.drawText(t, M, y, subP); y += 4f }
        fun header(t: String) {
            ensure(28f); y += 16f; canvas.drawText(t, M, y, bold); y += 4f
            canvas.drawLine(M, y, W - M, y, line); y += 2f
        }
        fun text(t: String) { ensure(14f); y += 13f; canvas.drawText(fit(t, body, W - 2 * M), M, y, body) }
        fun small(t: String) { ensure(12f); y += 11f; canvas.drawText(fit(t, smallP, W - 2 * M), M, y, smallP) }
        fun space() { y += 6f }

        fun row(label: String, value: String) {
            ensure(14f); y += 13f
            val vw = body.measureText(value)
            canvas.drawText(fit(label, body, W - 2 * M - vw - 12f), M, y, body)
            canvas.drawText(value, W - M - vw, y, body)
        }

        private fun fit(t: String, p: Paint, max: Float): String {
            if (p.measureText(t) <= max) return t
            var s = t
            while (s.length > 1 && p.measureText("$s…") > max) s = s.dropLast(1)
            return "$s…"
        }

        fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }
}
