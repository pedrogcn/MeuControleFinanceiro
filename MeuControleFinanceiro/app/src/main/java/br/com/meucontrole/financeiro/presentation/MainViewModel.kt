package br.com.meucontrole.financeiro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.meucontrole.financeiro.MeuControleApp
import br.com.meucontrole.financeiro.backup.RestoreBackupUseCase
import br.com.meucontrole.financeiro.data.SecurityStore
import br.com.meucontrole.financeiro.data.repository.FinanceRepository
import br.com.meucontrole.financeiro.data.repository.OpResult
import br.com.meucontrole.financeiro.domain.calc.FinanceEngine
import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.usecase.PaymentRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Estado derivado: dados + data de hoje + motor de cálculo pronto para as telas. */
data class UiData(val data: FinanceData, val today: LocalDate, val engine: FinanceEngine)

class MainViewModel(
    val repository: FinanceRepository,
    val security: SecurityStore
) : ViewModel() {

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    /** null enquanto o banco ainda está carregando. */
    val ui: StateFlow<UiData?> = combine(repository.data, _today) { data, today ->
        UiData(data, today, FinanceEngine(data, today))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val restore = RestoreBackupUseCase(repository)

    init {
        viewModelScope.launch { repository.ensureOccurrences() }
    }

    /** Chamado ao voltar ao app: atualiza "hoje" (vencidos) e gera ocorrências do novo período. */
    fun refresh() {
        _today.value = LocalDate.now()
        viewModelScope.launch { repository.ensureOccurrences(_month.value.atEndOfMonth()) }
    }

    fun setMonth(m: YearMonth) {
        _month.value = m
        viewModelScope.launch { repository.ensureOccurrences(m.atEndOfMonth()) }
    }

    fun message(text: String) { _messages.tryEmit(text) }

    /**
     * Executa uma operação de escrita. Enquanto uma operação estiver em andamento, novas chamadas
     * são ignoradas (impede duplo toque). Falhas e sucessos viram mensagens para o usuário.
     */
    fun run(showSuccess: Boolean = true, block: suspend () -> OpResult, onResult: (OpResult) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: Exception) {
                OpResult.Failure("Não foi possível concluir: ${e.message ?: e.javaClass.simpleName}")
            }
            when (result) {
                is OpResult.Success -> if (showSuccess) _messages.emit(result.message)
                is OpResult.Failure -> _messages.emit(result.message)
                is OpResult.NeedsDecision -> Unit
            }
            _busy.value = false
            onResult(result)
        }
    }

    fun pay(req: PaymentRequest, onResult: (OpResult) -> Unit) = run(block = { repository.registerPayment(req) }, onResult = onResult)

    fun saveSettings(s: AppSettings, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveSettings(s)
            onDone()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MeuControleApp
                MainViewModel(app.repository, app.security)
            }
        }
    }
}
