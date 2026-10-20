package br.com.meucontrole.financeiro.backup

import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.PaymentTarget

data class MergeResult(
    /** Registros novos, já com IDs que não colidem com os atuais. */
    val toInsert: FinanceData,
    /** Itens ignorados por já existirem (duplicados). */
    val duplicates: List<String>,
    /** Diferenças relevantes que o usuário deve conhecer (não bloqueiam). */
    val conflicts: List<String>
) {
    val insertedCount: Int
        get() = toInsert.debts.size + toInsert.installments.size + toInsert.payments.size +
            toInsert.incomeSources.size + toInsert.incomeOccurrences.size +
            toInsert.billTemplates.size + toInsert.billOccurrences.size
}

/**
 * Mescla um backup aos dados atuais sem apagar nada.
 * Duplicados são detectados por chaves naturais (não pelos IDs, que podem coincidir por acaso):
 * - Dívida: nome + credor + data de contratação + total contratado
 * - Fonte de renda: nome + tipo + início + líquido
 * - Conta: nome + categoria + início + recorrência
 * - Pagamento: identificador da operação (requestId)
 * - Ocorrências: (modelo, data)
 */
object BackupMerger {

    fun merge(current: FinanceData, incoming: FinanceData): MergeResult {
        val duplicates = mutableListOf<String>()
        val conflicts = mutableListOf<String>()

        var nextDebt = (current.debts.maxOfOrNull { it.id } ?: 0) + 1
        var nextInst = (current.installments.maxOfOrNull { it.id } ?: 0) + 1
        var nextPay = (current.payments.maxOfOrNull { it.id } ?: 0) + 1
        var nextSrc = (current.incomeSources.maxOfOrNull { it.id } ?: 0) + 1
        var nextInc = (current.incomeOccurrences.maxOfOrNull { it.id } ?: 0) + 1
        var nextTpl = (current.billTemplates.maxOfOrNull { it.id } ?: 0) + 1
        var nextOcc = (current.billOccurrences.maxOfOrNull { it.id } ?: 0) + 1
        var nextLog = (current.changeLog.maxOfOrNull { it.id } ?: 0) + 1

        // --- Dívidas ---
        fun debtKey(name: String, creditor: String, date: Any, total: Long) = "${name.trim().lowercase()}|${creditor.trim().lowercase()}|$date|$total"
        val existingDebtByKey = current.debts.associateBy { debtKey(it.name, it.creditor, it.contractDate, it.totalContracted) }
        val debtIdMap = mutableMapOf<Long, Long>()
        val newDebts = incoming.debts.mapNotNull { d ->
            val existing = existingDebtByKey[debtKey(d.name, d.creditor, d.contractDate, d.totalContracted)]
            if (existing != null) {
                duplicates += "Dívida \"${d.name}\" já existe"
                if (existing.status != d.status) conflicts += "Dívida \"${d.name}\": status atual ${existing.status.label}, no backup ${d.status.label} (mantido o atual)"
                null
            } else {
                val id = nextDebt++
                debtIdMap[d.id] = id
                d.copy(id = id)
            }
        }.map { d -> d.copy(renegotiatedFromId = d.renegotiatedFromId?.let { debtIdMap[it] ?: findExistingDebtId(it, incoming, existingDebtByKey, ::debtKey) }) }

        val instIdMap = mutableMapOf<Long, Long>()
        val newInst = incoming.installments.filter { it.debtId in debtIdMap }.map { i ->
            val id = nextInst++
            instIdMap[i.id] = id
            i.copy(id = id, debtId = debtIdMap.getValue(i.debtId))
        }

        // --- Contas ---
        fun tplKey(n: String, c: String, s: Any, r: Any) = "${n.trim().lowercase()}|${c.lowercase()}|$s|$r"
        val existingTpl = current.billTemplates.associateBy { tplKey(it.name, it.category, it.startDate, it.recurrence) }
        val tplIdMap = mutableMapOf<Long, Long>()
        val newTpl = incoming.billTemplates.mapNotNull { t ->
            val ex = existingTpl[tplKey(t.name, t.category, t.startDate, t.recurrence)]
            if (ex != null) {
                tplIdMap[t.id] = ex.id
                duplicates += "Conta \"${t.name}\" já existe"
                if (ex.expectedAmount != t.expectedAmount) conflicts += "Conta \"${t.name}\": valor previsto diferente no backup (mantido o atual)"
                null
            } else {
                val id = nextTpl++
                tplIdMap[t.id] = id
                t.copy(id = id)
            }
        }
        val existingOccKeys = current.billOccurrences.map { it.templateId to it.dueDate }.toHashSet()
        val existingOccByKey = current.billOccurrences.associateBy { it.templateId to it.dueDate }
        val occIdMap = mutableMapOf<Long, Long>()
        val newOcc = incoming.billOccurrences.mapNotNull { o ->
            val tpl = tplIdMap[o.templateId] ?: return@mapNotNull null
            val key = tpl to o.dueDate
            if (!existingOccKeys.add(key)) {
                existingOccByKey[key]?.let { occIdMap[o.id] = it.id }
                null
            } else {
                val id = nextOcc++
                occIdMap[o.id] = id
                o.copy(id = id, templateId = tpl)
            }
        }

        // --- Renda ---
        fun srcKey(n: String, k: Any, s: Any, v: Long) = "${n.trim().lowercase()}|$k|$s|$v"
        val existingSrc = current.incomeSources.associateBy { srcKey(it.name, it.kind, it.startDate, it.netAmount) }
        val srcIdMap = mutableMapOf<Long, Long>()
        val newSrc = incoming.incomeSources.mapNotNull { s ->
            val ex = existingSrc[srcKey(s.name, s.kind, s.startDate, s.netAmount)]
            if (ex != null) {
                srcIdMap[s.id] = ex.id
                duplicates += "Fonte de renda \"${s.name}\" já existe"
                null
            } else {
                val id = nextSrc++
                srcIdMap[s.id] = id
                s.copy(id = id)
            }
        }
        val existingIncKeys = current.incomeOccurrences.map { it.sourceId to it.date }.toHashSet()
        val newInc = incoming.incomeOccurrences.mapNotNull { o ->
            val src = srcIdMap[o.sourceId] ?: return@mapNotNull null
            if (!existingIncKeys.add(src to o.date)) null else o.copy(id = nextInc++, sourceId = src)
        }

        // --- Pagamentos ---
        val existingReq = current.payments.map { it.requestId }.toHashSet()
        var dupPayments = 0
        val newPay = incoming.payments.mapNotNull { p ->
            if (!existingReq.add(p.requestId)) { dupPayments++; return@mapNotNull null }
            val target = when (p.target) {
                PaymentTarget.INSTALLMENT -> instIdMap[p.targetId]
                PaymentTarget.BILL -> occIdMap[p.targetId]
                PaymentTarget.DEBT_AGREEMENT -> debtIdMap[p.targetId]
            } ?: return@mapNotNull null.also { dupPayments++ }
            p.copy(id = nextPay++, targetId = target, debtId = p.debtId?.let { debtIdMap[it] })
        }
        if (dupPayments > 0) duplicates += "$dupPayments pagamento(s) já existentes ou ligados a itens duplicados foram ignorados"

        val newLog = incoming.changeLog.map { it.copy(id = nextLog++) }

        if (current.settings.openingBalance != incoming.settings.openingBalance || current.settings.openingDate != incoming.settings.openingDate) {
            conflicts += "Saldo inicial diferente no backup (mantido o atual)"
        }

        return MergeResult(
            toInsert = FinanceData(newDebts, newInst, newPay, newSrc, newInc, newTpl, newOcc, newLog, current.settings),
            duplicates = duplicates,
            conflicts = conflicts
        )
    }

    private fun findExistingDebtId(
        oldId: Long,
        incoming: FinanceData,
        existingByKey: Map<String, br.com.meucontrole.financeiro.domain.model.Debt>,
        key: (String, String, Any, Long) -> String
    ): Long? {
        val d = incoming.debts.firstOrNull { it.id == oldId } ?: return null
        return existingByKey[key(d.name, d.creditor, d.contractDate, d.totalContracted)]?.id
    }
}
