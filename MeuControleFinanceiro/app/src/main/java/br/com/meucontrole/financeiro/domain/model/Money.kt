package br.com.meucontrole.financeiro.domain.model

import kotlin.math.abs

/**
 * Todos os valores monetários do app são armazenados e calculados em CENTAVOS (Long).
 * Nunca usamos Double/Float para dinheiro. Esta classe só formata e interpreta textos.
 */
object Money {

    /** 123456 -> "R$ 1.234,56"; -100 -> "-R$ 1,00" */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        return "${sign}R$ ${formatPlain(abs(cents), groupThousands = true)}"
    }

    /** 123456 -> "1234,56" (ou "1.234,56" se groupThousands). Sem símbolo de moeda. */
    fun formatPlain(cents: Long, groupThousands: Boolean = false): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        val reais = (value / 100).toString()
        val centavos = (value % 100).toString().padStart(2, '0')
        val grouped = if (groupThousands) groupDigits(reais) else reais
        return "$sign$grouped,$centavos"
    }

    private fun groupDigits(digits: String): String {
        val sb = StringBuilder()
        val firstGroup = digits.length % 3
        digits.forEachIndexed { index, c ->
            if (index != 0 && (index - firstGroup) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Interpreta valores digitados no padrão brasileiro.
     * Aceita: "1.234,56", "1234,56", "1234,5", "1234", "R$ 10,00", "10.50" (ponto decimal com 1-2 casas).
     * Retorna null se o texto for inválido. Não aceita negativos (use regras de negócio para isso).
     */
    fun parse(input: String): Long? {
        val s = input.trim().replace("R$", "").replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        if (s.startsWith("-")) return null
        val decimalSep: Char? = when {
            s.contains(',') -> ','
            s.count { it == '.' } == 1 && s.substringAfter('.').length in 1..2 -> '.'
            else -> null
        }
        val intPart: String
        val fracPart: String
        if (decimalSep == ',') {
            if (s.count { it == ',' } > 1) return null
            intPart = s.substringBefore(',').replace(".", "")
            fracPart = s.substringAfter(',')
        } else if (decimalSep == '.') {
            intPart = s.substringBefore('.')
            fracPart = s.substringAfter('.')
        } else {
            intPart = s.replace(".", "")
            fracPart = ""
        }
        if (intPart.isEmpty() && fracPart.isEmpty()) return null
        if (!intPart.all { it.isDigit() } || !fracPart.all { it.isDigit() }) return null
        if (fracPart.length > 2) return null
        if (intPart.length > 13) return null // evita overflow e valores absurdos
        val reais = if (intPart.isEmpty()) 0L else intPart.toLong()
        val cents = fracPart.padEnd(2, '0').toLong()
        return reais * 100 + cents
    }

    /** Porcentagem segura (0..1) para barras de progresso. */
    fun ratio(part: Long, total: Long): Float {
        if (total <= 0) return 0f
        return (part.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}
