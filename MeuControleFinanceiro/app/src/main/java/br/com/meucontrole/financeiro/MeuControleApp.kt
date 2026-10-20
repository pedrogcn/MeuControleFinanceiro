package br.com.meucontrole.financeiro

import android.app.Application
import br.com.meucontrole.financeiro.data.SecurityStore
import br.com.meucontrole.financeiro.data.db.AppDatabase
import br.com.meucontrole.financeiro.data.repository.FinanceRepository
import br.com.meucontrole.financeiro.notifications.NotificationHelper
import br.com.meucontrole.financeiro.notifications.ReminderScheduler

/** Contêiner simples de dependências (sem frameworks externos). */
class MeuControleApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: FinanceRepository by lazy { FinanceRepository(database) }
    val security: SecurityStore by lazy { SecurityStore(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        ReminderScheduler.schedule(this, hour = 8, replace = false)
    }
}
