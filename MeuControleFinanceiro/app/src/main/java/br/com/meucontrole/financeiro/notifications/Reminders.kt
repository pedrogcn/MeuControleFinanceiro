package br.com.meucontrole.financeiro.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.meucontrole.financeiro.MainActivity
import br.com.meucontrole.financeiro.MeuControleApp
import br.com.meucontrole.financeiro.R
import br.com.meucontrole.financeiro.domain.calc.DueItem
import br.com.meucontrole.financeiro.domain.calc.DueKind
import br.com.meucontrole.financeiro.domain.calc.FinanceEngine
import br.com.meucontrole.financeiro.domain.calc.ItemStatus
import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.Money
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Regras puras: quais lembretes enviar hoje. */
object ReminderRules {
    data class Reminder(val item: DueItem, val isToday: Boolean)

    fun remindersFor(data: FinanceData, today: LocalDate, settings: AppSettings): List<Reminder> {
        val engine = FinanceEngine(data, today)
        return engine.itemsBetween(today, today.plusDays(1), includeIncome = false)
            .filter { it.kind != DueKind.INCOME && it.status != ItemStatus.PAGA && it.remaining > 0 }
            .mapNotNull { item ->
                val isToday = item.date == today
                when {
                    isToday && settings.remindSameDay -> Reminder(item, true)
                    !isToday && settings.remindDayBefore -> Reminder(item, false)
                    else -> null
                }
            }
    }
}

object NotificationHelper {
    const val CHANNEL_ID = "vencimentos"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Vencimentos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Lembretes locais de contas e parcelas"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, reminders: List<ReminderRules.Reminder>) {
        if (reminders.isEmpty() || !canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val today = reminders.filter { it.isToday }
        val tomorrow = reminders.filter { !it.isToday }
        val title = when {
            today.isNotEmpty() && tomorrow.isNotEmpty() -> "${today.size} vencem hoje, ${tomorrow.size} amanhã"
            today.isNotEmpty() -> if (today.size == 1) "Vence hoje: ${today[0].item.title}" else "${today.size} vencimentos hoje"
            else -> if (tomorrow.size == 1) "Vence amanhã: ${tomorrow[0].item.title}" else "${tomorrow.size} vencimentos amanhã"
        }
        val lines = reminders.map { r ->
            "${if (r.isToday) "Hoje" else "Amanhã"}: ${r.item.title} (${r.item.subtitle}) — ${Money.format(r.item.remaining)}"
        }
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (_: SecurityException) {
            // Permissão revogada entre a verificação e o envio: ignorar.
        }
    }
}

/** Executa uma vez por dia (aprox.), sem internet, lendo apenas o banco local. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MeuControleApp
        val repo = app.repository
        val data = repo.snapshot()
        repo.ensureOccurrences()
        val reminders = ReminderRules.remindersFor(repo.snapshot(), LocalDate.now(), data.settings)
        NotificationHelper.show(applicationContext, reminders)
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK = "lembretes_vencimentos"

    /**
     * Agenda o trabalho diário próximo de [hour]. O Android pode adiar a execução
     * (economia de bateria, modo soneca), por isso o horário é aproximado.
     */
    fun schedule(context: Context, hour: Int, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
