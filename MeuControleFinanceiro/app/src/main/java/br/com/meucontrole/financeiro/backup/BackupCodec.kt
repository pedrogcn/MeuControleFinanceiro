package br.com.meucontrole.financeiro.backup

import br.com.meucontrole.financeiro.domain.model.AppSettings
import br.com.meucontrole.financeiro.domain.model.BillOccurrence
import br.com.meucontrole.financeiro.domain.model.BillTemplate
import br.com.meucontrole.financeiro.domain.model.ChangeLogEntry
import br.com.meucontrole.financeiro.domain.model.Debt
import br.com.meucontrole.financeiro.domain.model.DebtStatus
import br.com.meucontrole.financeiro.domain.model.FinanceData
import br.com.meucontrole.financeiro.domain.model.IncomeFrequency
import br.com.meucontrole.financeiro.domain.model.IncomeKind
import br.com.meucontrole.financeiro.domain.model.IncomeOccurrence
import br.com.meucontrole.financeiro.domain.model.IncomeSource
import br.com.meucontrole.financeiro.domain.model.Installment
import br.com.meucontrole.financeiro.domain.model.Payment
import br.com.meucontrole.financeiro.domain.model.PaymentTarget
import br.com.meucontrole.financeiro.domain.model.Recurrence
import br.com.meucontrole.financeiro.domain.model.ThemeMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Formato de backup versionado:
 * {
 *   "format": "meu-controle-financeiro",
 *   "version": 1,
 *   "exportedAt": <epoch millis>,
 *   "checksum": "<sha-256 do objeto data serializado>",
 *   "data": { "debts": [...], "installments": [...], "payments": [...], ... , "settings": {...} }
 * }
 * Datas em ISO-8601 (yyyy-MM-dd). Valores monetários em centavos (inteiros).
 */
object BackupCodec {

    const val FORMAT = "meu-controle-financeiro"
    const val VERSION = 1

    private val json = Json { prettyPrint = false }

    fun encode(data: FinanceData, exportedAt: Long): String {
        val dataObj = dataToJson(data)
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", exportedAt)
            put("checksum", sha256(dataObj.toString()))
            put("data", dataObj)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // ------------------------------- Encode -------------------------------

    private fun d(date: LocalDate?): JsonElement = date?.let { JsonPrimitive(it.toString()) } ?: JsonNull
    private fun n(v: Long?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull
    private fun s(v: String?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull

    fun dataToJson(data: FinanceData): JsonObject = buildJsonObject {
        put("debts", buildJsonArray {
            data.debts.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("name", x.name); put("creditor", x.creditor); put("category", x.category)
                    put("originalAmount", x.originalAmount); put("downPayment", x.downPayment)
                    put("contractDate", d(x.contractDate)); put("totalContracted", x.totalContracted)
                    put("installmentCount", x.installmentCount); put("installmentAmount", x.installmentAmount)
                    put("firstDueDate", d(x.firstDueDate)); put("dueDay", x.dueDay)
                    put("interestRateInfo", x.interestRateInfo); put("registeredCharges", x.registeredCharges)
                    put("negotiatedDiscount", x.negotiatedDiscount); put("notes", x.notes)
                    put("status", x.status.name); put("renegotiatedFromId", n(x.renegotiatedFromId)); put("createdAt", x.createdAt)
                })
            }
        })
        put("installments", buildJsonArray {
            data.installments.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("debtId", x.debtId); put("number", x.number); put("totalCount", x.totalCount)
                    put("amount", x.amount); put("dueDate", d(x.dueDate)); put("notes", x.notes)
                })
            }
        })
        put("payments", buildJsonArray {
            data.payments.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("requestId", x.requestId); put("target", x.target.name); put("targetId", x.targetId)
                    put("debtId", n(x.debtId)); put("amount", x.amount); put("charges", x.charges); put("discount", x.discount)
                    put("date", d(x.date)); put("method", x.method); put("notes", x.notes); put("reversed", x.reversed)
                    put("reversalReason", s(x.reversalReason)); put("reversedAt", n(x.reversedAt))
                    put("createdAt", x.createdAt); put("editedAt", n(x.editedAt))
                })
            }
        })
        put("incomeSources", buildJsonArray {
            data.incomeSources.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("name", x.name); put("kind", x.kind.name); put("grossAmount", n(x.grossAmount))
                    put("netAmount", x.netAmount); put("frequency", x.frequency.name); put("startDate", d(x.startDate))
                    put("endDate", d(x.endDate)); put("active", x.active); put("notes", x.notes); put("createdAt", x.createdAt)
                })
            }
        })
        put("incomeOccurrences", buildJsonArray {
            data.incomeOccurrences.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("sourceId", x.sourceId); put("date", d(x.date)); put("expectedAmount", x.expectedAmount)
                    put("receivedAmount", n(x.receivedAmount)); put("receivedDate", d(x.receivedDate)); put("notes", x.notes)
                })
            }
        })
        put("billTemplates", buildJsonArray {
            data.billTemplates.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("name", x.name); put("category", x.category); put("expectedAmount", x.expectedAmount)
                    put("recurrence", x.recurrence.name); put("startDate", d(x.startDate)); put("endDate", d(x.endDate))
                    put("active", x.active); put("notes", x.notes); put("createdAt", x.createdAt)
                })
            }
        })
        put("billOccurrences", buildJsonArray {
            data.billOccurrences.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("templateId", x.templateId); put("dueDate", d(x.dueDate))
                    put("expectedAmount", x.expectedAmount); put("notes", x.notes); put("skipped", x.skipped)
                })
            }
        })
        put("changeLog", buildJsonArray {
            data.changeLog.forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("entityType", x.entityType); put("entityId", x.entityId)
                    put("timestamp", x.timestamp); put("description", x.description)
                })
            }
        })
        val st = data.settings
        put("settings", buildJsonObject {
            put("openingBalance", st.openingBalance); put("openingDate", d(st.openingDate))
            put("remindDayBefore", st.remindDayBefore); put("remindSameDay", st.remindSameDay)
            put("reminderHour", st.reminderHour); put("themeMode", st.themeMode.name)
        })
    }

    // ------------------------------- Decode -------------------------------

    sealed class Result {
        data class Success(val data: FinanceData, val exportedAt: Long, val version: Int) : Result()
        data class Failure(val errors: List<String>) : Result()
    }

    private class Reader(val errors: MutableList<String>) {
        fun obj(e: JsonElement?, ctx: String): JsonObject? =
            (e as? JsonObject) ?: run { errors += "$ctx: objeto ausente ou inválido"; null }

        fun arr(o: JsonObject, key: String): JsonArray =
            (o[key] as? JsonArray) ?: run { errors += "Lista '$key' ausente ou inválida"; JsonArray(emptyList()) }

        fun long(o: JsonObject, key: String, ctx: String, min: Long? = 0): Long {
            val v = (o[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (v == null) { errors += "$ctx: campo '$key' deve ser número inteiro"; return 0 }
            if (min != null && v < min) { errors += "$ctx: campo '$key' não pode ser negativo"; return 0 }
            return v
        }

        fun longOrNull(o: JsonObject, key: String, ctx: String): Long? {
            val e = o[key]
            if (e == null || e is JsonNull) return null
            val v = (e as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (v == null || v < 0) errors += "$ctx: campo '$key' inválido"
            return v
        }

        fun int(o: JsonObject, key: String, ctx: String, range: IntRange): Int {
            val v = (o[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (v == null || v !in range) { errors += "$ctx: campo '$key' fora do intervalo $range"; return range.first }
            return v
        }

        fun str(o: JsonObject, key: String, ctx: String, required: Boolean = false): String {
            val v = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            if (v == null) { if (required || o[key] != null && o[key] !is JsonNull) errors += "$ctx: campo '$key' deve ser texto"; return "" }
            if (required && v.isBlank()) errors += "$ctx: campo '$key' vazio"
            return v
        }

        fun strOrNull(o: JsonObject, key: String): String? =
            (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        fun bool(o: JsonObject, key: String, ctx: String): Boolean {
            val v = (o[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            if (v == null) { errors += "$ctx: campo '$key' deve ser verdadeiro/falso"; return false }
            return v
        }

        fun date(o: JsonObject, key: String, ctx: String): LocalDate {
            val raw = strOrNull(o, key)
            val parsed = raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (parsed == null) { errors += "$ctx: data '$key' inválida"; return LocalDate.of(2000, 1, 1) }
            return parsed
        }

        fun dateOrNull(o: JsonObject, key: String, ctx: String): LocalDate? {
            val e = o[key]
            if (e == null || e is JsonNull) return null
            val parsed = strOrNull(o, key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (parsed == null) errors += "$ctx: data '$key' inválida"
            return parsed
        }

        inline fun <reified E : Enum<E>> enum(o: JsonObject, key: String, ctx: String, default: E): E {
            val raw = strOrNull(o, key)
            val v = enumValues<E>().firstOrNull { it.name == raw }
            if (v == null) { errors += "$ctx: valor '$raw' inválido em '$key'"; return default }
            return v
        }
    }

    fun decode(text: String): Result {
        val errors = mutableListOf<String>()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return Result.Failure(listOf("O arquivo não é um JSON válido."))
        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != FORMAT) return Result.Failure(listOf("O arquivo não é um backup do Meu Controle Financeiro."))
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
            ?: return Result.Failure(listOf("Versão do backup ausente."))
        if (version > VERSION) return Result.Failure(listOf("Backup criado por uma versão mais nova do app (v$version). Atualize o aplicativo."))
        if (version < 1) return Result.Failure(listOf("Versão de backup inválida."))
        val dataObj = root["data"] as? JsonObject ?: return Result.Failure(listOf("Seção de dados ausente."))
        val checksum = (root["checksum"] as? JsonPrimitive)?.contentOrNull
        if (checksum == null || checksum != sha256(dataObj.toString())) {
            return Result.Failure(listOf("Falha na verificação de integridade (checksum). O arquivo pode estar corrompido ou ter sido alterado."))
        }
        val exportedAt = (root["exportedAt"] as? JsonPrimitive)?.longOrNull ?: 0L

        val r = Reader(errors)
        val debts = r.arr(dataObj, "debts").mapIndexedNotNull { i, e ->
            val c = "Dívida #${i + 1}"
            r.obj(e, c)?.let { o ->
                Debt(
                    id = r.long(o, "id", c, 1), name = r.str(o, "name", c, true), creditor = r.str(o, "creditor", c),
                    category = r.str(o, "category", c), originalAmount = r.long(o, "originalAmount", c),
                    downPayment = r.long(o, "downPayment", c), contractDate = r.date(o, "contractDate", c),
                    totalContracted = r.long(o, "totalContracted", c), installmentCount = r.int(o, "installmentCount", c, 0..600),
                    installmentAmount = r.long(o, "installmentAmount", c), firstDueDate = r.date(o, "firstDueDate", c),
                    dueDay = r.int(o, "dueDay", c, 1..31), interestRateInfo = r.str(o, "interestRateInfo", c),
                    registeredCharges = r.long(o, "registeredCharges", c), negotiatedDiscount = r.long(o, "negotiatedDiscount", c),
                    notes = r.str(o, "notes", c), status = r.enum(o, "status", c, DebtStatus.ATIVA),
                    renegotiatedFromId = r.longOrNull(o, "renegotiatedFromId", c), createdAt = r.long(o, "createdAt", c)
                )
            }
        }
        val installments = r.arr(dataObj, "installments").mapIndexedNotNull { i, e ->
            val c = "Parcela #${i + 1}"
            r.obj(e, c)?.let { o ->
                Installment(
                    id = r.long(o, "id", c, 1), debtId = r.long(o, "debtId", c, 1), number = r.int(o, "number", c, 1..600),
                    totalCount = r.int(o, "totalCount", c, 1..600), amount = r.long(o, "amount", c),
                    dueDate = r.date(o, "dueDate", c), notes = r.str(o, "notes", c)
                )
            }
        }
        val payments = r.arr(dataObj, "payments").mapIndexedNotNull { i, e ->
            val c = "Pagamento #${i + 1}"
            r.obj(e, c)?.let { o ->
                Payment(
                    id = r.long(o, "id", c, 1), requestId = r.str(o, "requestId", c, true),
                    target = r.enum(o, "target", c, PaymentTarget.INSTALLMENT), targetId = r.long(o, "targetId", c, 1),
                    debtId = r.longOrNull(o, "debtId", c), amount = r.long(o, "amount", c), charges = r.long(o, "charges", c),
                    discount = r.long(o, "discount", c), date = r.date(o, "date", c), method = r.str(o, "method", c),
                    notes = r.str(o, "notes", c), reversed = r.bool(o, "reversed", c), reversalReason = r.strOrNull(o, "reversalReason"),
                    reversedAt = r.longOrNull(o, "reversedAt", c), createdAt = r.long(o, "createdAt", c),
                    editedAt = r.longOrNull(o, "editedAt", c)
                )
            }
        }
        val sources = r.arr(dataObj, "incomeSources").mapIndexedNotNull { i, e ->
            val c = "Fonte de renda #${i + 1}"
            r.obj(e, c)?.let { o ->
                IncomeSource(
                    id = r.long(o, "id", c, 1), name = r.str(o, "name", c, true), kind = r.enum(o, "kind", c, IncomeKind.OUTRA),
                    grossAmount = r.longOrNull(o, "grossAmount", c), netAmount = r.long(o, "netAmount", c),
                    frequency = r.enum(o, "frequency", c, IncomeFrequency.MENSAL), startDate = r.date(o, "startDate", c),
                    endDate = r.dateOrNull(o, "endDate", c), active = r.bool(o, "active", c), notes = r.str(o, "notes", c),
                    createdAt = r.long(o, "createdAt", c)
                )
            }
        }
        val incomeOcc = r.arr(dataObj, "incomeOccurrences").mapIndexedNotNull { i, e ->
            val c = "Recebimento #${i + 1}"
            r.obj(e, c)?.let { o ->
                IncomeOccurrence(
                    id = r.long(o, "id", c, 1), sourceId = r.long(o, "sourceId", c, 1), date = r.date(o, "date", c),
                    expectedAmount = r.long(o, "expectedAmount", c), receivedAmount = r.longOrNull(o, "receivedAmount", c),
                    receivedDate = r.dateOrNull(o, "receivedDate", c), notes = r.str(o, "notes", c)
                )
            }
        }
        val templates = r.arr(dataObj, "billTemplates").mapIndexedNotNull { i, e ->
            val c = "Conta #${i + 1}"
            r.obj(e, c)?.let { o ->
                BillTemplate(
                    id = r.long(o, "id", c, 1), name = r.str(o, "name", c, true), category = r.str(o, "category", c),
                    expectedAmount = r.long(o, "expectedAmount", c), recurrence = r.enum(o, "recurrence", c, Recurrence.MENSAL),
                    startDate = r.date(o, "startDate", c), endDate = r.dateOrNull(o, "endDate", c),
                    active = r.bool(o, "active", c), notes = r.str(o, "notes", c), createdAt = r.long(o, "createdAt", c)
                )
            }
        }
        val billOcc = r.arr(dataObj, "billOccurrences").mapIndexedNotNull { i, e ->
            val c = "Ocorrência de conta #${i + 1}"
            r.obj(e, c)?.let { o ->
                BillOccurrence(
                    id = r.long(o, "id", c, 1), templateId = r.long(o, "templateId", c, 1), dueDate = r.date(o, "dueDate", c),
                    expectedAmount = r.long(o, "expectedAmount", c), notes = r.str(o, "notes", c), skipped = r.bool(o, "skipped", c)
                )
            }
        }
        val log = (dataObj["changeLog"] as? JsonArray ?: JsonArray(emptyList())).mapIndexedNotNull { i, e ->
            val c = "Histórico #${i + 1}"
            r.obj(e, c)?.let { o ->
                ChangeLogEntry(
                    id = r.long(o, "id", c, 1), entityType = r.str(o, "entityType", c), entityId = r.long(o, "entityId", c),
                    timestamp = r.long(o, "timestamp", c), description = r.str(o, "description", c)
                )
            }
        }
        val settings = (dataObj["settings"] as? JsonObject)?.let { o ->
            val c = "Configurações"
            AppSettings(
                openingBalance = r.long(o, "openingBalance", c, null),
                openingDate = r.dateOrNull(o, "openingDate", c),
                remindDayBefore = r.bool(o, "remindDayBefore", c),
                remindSameDay = r.bool(o, "remindSameDay", c),
                reminderHour = r.int(o, "reminderHour", c, 0..23),
                themeMode = r.enum(o, "themeMode", c, ThemeMode.SISTEMA)
            )
        } ?: AppSettings()

        val data = FinanceData(debts, installments, payments, sources, incomeOcc, templates, billOcc, log, settings)
        errors += IntegrityChecker.check(data)
        return if (errors.isEmpty()) Result.Success(data, exportedAt, version) else Result.Failure(errors.distinct().take(30))
    }
}

/** Verificações de integridade referencial e de unicidade. */
object IntegrityChecker {
    fun check(data: FinanceData): List<String> {
        val errors = mutableListOf<String>()
        fun dupIds(name: String, ids: List<Long>) {
            if (ids.size != ids.toSet().size) errors += "IDs duplicados em $name"
        }
        dupIds("dívidas", data.debts.map { it.id })
        dupIds("parcelas", data.installments.map { it.id })
        dupIds("pagamentos", data.payments.map { it.id })
        dupIds("fontes de renda", data.incomeSources.map { it.id })
        dupIds("recebimentos", data.incomeOccurrences.map { it.id })
        dupIds("contas", data.billTemplates.map { it.id })
        dupIds("ocorrências de contas", data.billOccurrences.map { it.id })
        if (data.payments.map { it.requestId }.toSet().size != data.payments.size) errors += "Pagamentos com identificador de operação repetido"

        val debtIds = data.debts.map { it.id }.toSet()
        val instIds = data.installments.map { it.id }.toSet()
        val billIds = data.billOccurrences.map { it.id }.toSet()
        val tplIds = data.billTemplates.map { it.id }.toSet()
        val srcIds = data.incomeSources.map { it.id }.toSet()

        data.installments.filter { it.debtId !in debtIds }.take(3).forEach { errors += "Parcela ${it.id} referencia dívida inexistente" }
        data.billOccurrences.filter { it.templateId !in tplIds }.take(3).forEach { errors += "Ocorrência ${it.id} referencia conta inexistente" }
        data.incomeOccurrences.filter { it.sourceId !in srcIds }.take(3).forEach { errors += "Recebimento ${it.id} referencia fonte inexistente" }
        data.payments.forEach { p ->
            val ok = when (p.target) {
                PaymentTarget.INSTALLMENT -> p.targetId in instIds
                PaymentTarget.BILL -> p.targetId in billIds
                PaymentTarget.DEBT_AGREEMENT -> p.targetId in debtIds
            }
            if (!ok) errors += "Pagamento ${p.id} referencia item inexistente"
        }
        if (data.billOccurrences.map { it.templateId to it.dueDate }.let { it.size != it.toSet().size }) errors += "Ocorrências de contas duplicadas"
        if (data.incomeOccurrences.map { it.sourceId to it.date }.let { it.size != it.toSet().size }) errors += "Recebimentos duplicados"
        data.debts.forEach { d ->
            if (d.renegotiatedFromId != null && d.renegotiatedFromId !in debtIds) errors += "Dívida ${d.id} referencia renegociação inexistente"
        }
        return errors.take(30)
    }
}
