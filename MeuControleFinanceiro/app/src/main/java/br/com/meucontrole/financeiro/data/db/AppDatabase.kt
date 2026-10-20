package br.com.meucontrole.financeiro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DebtEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class,
        IncomeSourceEntity::class,
        IncomeOccurrenceEntity::class,
        BillTemplateEntity::class,
        BillOccurrenceEntity::class,
        ChangeLogEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "meu_controle_financeiro.db")
                // Nunca usar fallbackToDestructiveMigration: dados financeiros não podem ser apagados em atualizações.
                .build()
                .also { instance = it }
        }
    }
}
