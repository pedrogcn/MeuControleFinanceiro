package br.com.meucontrole.financeiro.backup

import br.com.meucontrole.financeiro.domain.model.FinanceData

/** Abstração do armazenamento usada pela restauração (implementada pelo repositório Room). */
interface BackupStore {
    suspend fun snapshot(): FinanceData
    /** Substitui TODOS os dados em uma única transação. */
    suspend fun replaceAll(data: FinanceData)
    /** Insere dados já com IDs novos (resultado de [BackupMerger]) em uma única transação. */
    suspend fun insertMerged(data: FinanceData)
}

enum class RestoreMode { SUBSTITUIR, MESCLAR }

data class RestorePreview(
    val incoming: FinanceData,
    val exportedAt: Long,
    val counts: Map<String, Int>,
    val merge: MergeResult
)

/**
 * Fluxo seguro de restauração:
 * 1) [preview] lê e VALIDA o arquivo, sem tocar nos dados atuais.
 * 2) A interface mostra contagens, duplicados e conflitos e pede confirmação.
 * 3) [apply] grava em transação única. Se algo falhar, nada é alterado.
 */
class RestoreBackupUseCase(private val store: BackupStore) {

    sealed class PreviewResult {
        data class Valid(val preview: RestorePreview) : PreviewResult()
        data class Invalid(val errors: List<String>) : PreviewResult()
    }

    suspend fun preview(text: String): PreviewResult {
        return when (val r = BackupCodec.decode(text)) {
            is BackupCodec.Result.Failure -> PreviewResult.Invalid(r.errors)
            is BackupCodec.Result.Success -> {
                val current = store.snapshot()
                val merge = BackupMerger.merge(current, r.data)
                PreviewResult.Valid(
                    RestorePreview(
                        incoming = r.data,
                        exportedAt = r.exportedAt,
                        counts = mapOf(
                            "Dívidas" to r.data.debts.size,
                            "Parcelas" to r.data.installments.size,
                            "Pagamentos" to r.data.payments.size,
                            "Fontes de renda" to r.data.incomeSources.size,
                            "Recebimentos" to r.data.incomeOccurrences.size,
                            "Contas" to r.data.billTemplates.size,
                            "Ocorrências de contas" to r.data.billOccurrences.size
                        ),
                        merge = merge
                    )
                )
            }
        }
    }

    suspend fun apply(preview: RestorePreview, mode: RestoreMode) {
        when (mode) {
            RestoreMode.SUBSTITUIR -> store.replaceAll(preview.incoming)
            RestoreMode.MESCLAR -> store.insertMerged(preview.merge.toInsert)
        }
    }
}
