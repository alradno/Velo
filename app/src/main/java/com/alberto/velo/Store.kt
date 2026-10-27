package com.alberto.velo

import android.app.Application
import android.content.Context
import android.net.Uri
import android.telephony.PhoneNumberUtils
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class VeloApp : Application() { override fun onCreate() { super.onCreate(); Backups.schedule(this) } }

object Codec {
    fun window(w: TimeWindow) = JSONObject().put("id", w.id).put("days", JSONArray(w.days.sorted())).put("start", w.start).put("end", w.end).put("enabled", w.enabled)
    fun profile(p: Profile) = JSONObject().put("id", p.id).put("name", p.name).put("private", p.privateCalls).put("unknown", p.unknownCalls).put("unavailable", p.unavailableCalls).put("all", p.allCalls).put("voip", p.voipCalls).put("action", p.action.name).put("scheduled", p.scheduled).put("windows", JSONArray(p.windows.map(::window))).put("repeated", p.repeated).put("repeatMinutes", p.repeatMinutes).put("allowList", p.allowList)
    fun rule(r: NumberRule) = JSONObject().put("id", r.id).put("value", r.value).put("canonical", r.canonical).put("match", r.match.name).put("allow", r.allow).put("folder", r.folder).put("note", r.note).put("enabled", r.enabled).put("action", r.action?.name ?: "").put("profiles", JSONArray(r.profiles.toList()))
    fun encode(s: State): JSONObject {
        val p = s.settings
        return JSONObject().put("format", "velo").put("version", 1).put("settings", JSONObject().put("enabled", p.enabled).put("pauseUntil", p.pauseUntil).put("selectedProfile", p.selectedProfile).put("notifications", p.notifications).put("logAllowed", p.logAllowed).put("skipSystemNotification", p.skipSystemNotification).put("theme", p.theme).put("fontScale", p.fontScale).put("animations", p.animations).put("region", p.region).put("dailyBackup", p.dailyBackup).put("keepBackups", p.keepBackups).put("retentionDays", p.retentionDays))
            .put("profiles", JSONArray(s.profiles.map(::profile))).put("rules", JSONArray(s.rules.map(::rule))).put("folders", JSONArray(s.folders.map { JSONObject().put("name", it.name).put("enabled", it.enabled) }))
    }
    private fun objects(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
    fun decode(o: JSONObject): State {
        require(o.optString("format") == "velo" && o.optInt("version") == 1) { "Formato de copia no compatible" }
        val p = o.getJSONObject("settings")
        val settings = Settings(p.optBoolean("enabled"), p.optLong("pauseUntil"), p.optString("selectedProfile", "personal"), p.optBoolean("notifications", true), p.optBoolean("logAllowed", true), p.optBoolean("skipSystemNotification", true), p.optString("theme", "dark").also { require(it in listOf("dark", "light", "system")) }, p.optDouble("fontScale", 1.0).toFloat().coerceIn(.85f, 1.4f), p.optBoolean("animations", true), p.optString("region", "ES").also { require(it.matches(Regex("[A-Z]{2}"))) }, p.optBoolean("dailyBackup"), p.optInt("keepBackups", 7).coerceIn(1, 30), p.optInt("retentionDays", 0).coerceIn(0, 365))
        val profiles = objects(o.getJSONArray("profiles")).map { x ->
            val windows = objects(x.optJSONArray("windows")).map { w ->
                val days = w.getJSONArray("days"); val set = (0 until days.length()).map { days.getInt(it).also { d -> require(d in 1..7) } }.toSet()
                require(set.isNotEmpty())
                TimeWindow(w.getString("id"), set, w.getInt("start").also { require(it in 0..1439) }, w.getInt("end").also { require(it in 0..1439) }, w.optBoolean("enabled", true))
            }
            require(windows.size <= 100)
            Profile(x.getString("id"), x.getString("name").take(80), x.optBoolean("private"), x.optBoolean("unknown"), x.optBoolean("unavailable"), x.optBoolean("all"), x.optBoolean("voip"), Action.valueOf(x.getString("action")), x.optBoolean("scheduled"), windows, x.optBoolean("repeated"), x.optInt("repeatMinutes", 3).coerceIn(1, 30), x.optBoolean("allowList", true))
        }
        require(profiles.isNotEmpty() && profiles.size <= 50 && profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.all { it.name.isNotBlank() && it.action != Action.ALLOW })
        require(profiles.any { it.id == settings.selectedProfile })
        val folders = objects(o.getJSONArray("folders")).map { Folder(it.getString("name").also { n -> require(n.isNotBlank() && n.length <= 80) }, it.optBoolean("enabled", true)) }
        require(folders.size in 1..200 && folders.map { it.name }.distinct().size == folders.size && folders.any { it.name == "General" })
        val rules = objects(o.getJSONArray("rules")).map { x ->
            val value = x.getString("value"); require(RuleEngine.digits(value).isNotEmpty() && value.length <= 64)
            val scope = x.optJSONArray("profiles") ?: JSONArray()
            val ids = (0 until scope.length()).map { scope.getString(it) }.toSet()
            require(ids.all { id -> profiles.any { it.id == id } })
            NumberRule(x.getString("id"), value, x.optString("canonical", value), Match.valueOf(x.getString("match")), x.optBoolean("allow"), x.optString("folder", "General"), x.optString("note").take(500), x.optBoolean("enabled", true), x.optString("action").takeIf { it.isNotEmpty() }?.let(Action::valueOf), ids)
        }
        require(rules.size <= 5000 && rules.map { it.id }.distinct().size == rules.size && rules.all { r -> folders.any { it.name == r.folder } })
        require(rules.none { !it.allow && it.action == Action.ALLOW })
        return State(settings, profiles, rules, folders)
    }
    fun event(e: CallEvent) = JSONObject().put("id", e.id).put("time", e.time).put("number", e.number).put("identity", e.identity.name).put("action", e.action.name).put("reason", e.reason).put("source", e.source).put("elapsedMs", e.elapsedMs)
    fun events(text: String): List<CallEvent> = objects(JSONArray(text)).map { CallEvent(it.getString("id"), it.getLong("time"), it.getString("number"), Identity.valueOf(it.getString("identity")), Action.valueOf(it.getString("action")), it.getString("reason"), it.optString("source", "Filtrado"), it.optLong("elapsedMs")) }
}

class Store(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("velo", Context.MODE_PRIVATE)
    init {
        synchronized(lock) {
            // Personal upgrade: enable permanent history without changing the user's other preferences.
            if (!prefs.getBoolean("permanentHistoryUpgrade", false)) {
                val editor = prefs.edit()
                prefs.getString("state", null)?.let { text ->
                    val state = JSONObject(text)
                    state.getJSONObject("settings").put("retentionDays", 0)
                    editor.putString("state", state.toString())
                }
                check(editor.putBoolean("permanentHistoryUpgrade", true).commit())
            }
        }
    }
    private val history by lazy { HistoryDatabase.get(this.context).also { it.migrate(prefs, load().settings.region) } }
    private val stateKey = this.context.filesDir.absolutePath
    fun load(): State = synchronized(lock) {
        val text = prefs.getString("state",null)
        val cached = states[stateKey]
        if (cached != null && cached.first == text) cached.second
        else (text?.let { runCatching { Codec.decode(JSONObject(it)) }.getOrNull() } ?: State()).also { states[stateKey] = text to it }
    }
    fun save(s: State) = synchronized(lock) {
        val encoded = Codec.encode(s)
        Codec.decode(encoded) // Validate before replacing a valid configuration, including imports.
        check(prefs.edit().putString("state", encoded.toString()).commit()) { "No se pudieron guardar los ajustes" }
        states[stateKey] = encoded.toString() to s
    }
    fun events(): List<CallEvent> = history.page().events
    fun historyPage(onlyFiltered: Boolean = false, search: String = "", start: Long = 0, before: EventCursor? = null, limit: Int = 100, after: EventCursor? = null, signal: android.os.CancellationSignal? = null) = history.page(onlyFiltered, search, start, before, limit, after, signal)
    fun eventCount() = history.count()
    fun filteredSince(start: Long) = history.filteredSince(start)
    fun lastAttempt(number: String) = history.lastAttempt(number, load().settings.region)
    fun statistics(start: Long, end: Long, signal: android.os.CancellationSignal? = null) = history.statistics(start, end, signal)
    fun exportHistory(writer: java.io.Writer) = history.export(writer)
    fun record(e: CallEvent) {
        val state = load()
        if (e.action == Action.ALLOW && !state.settings.logAllowed) return
        history.record(e, state.settings.region, state.settings.retentionDays)
    }
    fun clearEvents() { history.clear() }
    fun export() = Codec.encode(load()).toString(2)
    fun restore(text: String): State { require(text.toByteArray().size <= 2_000_000) { "La copia supera 2 MB" }; val state = Codec.decode(JSONObject(text)); save(state); Backups.schedule(context); return state }
    fun canonical(value: String, region: String = load().settings.region): String = PhoneNumberUtils.formatNumberToE164(value, region) ?: value
    fun hasPin() = prefs.contains("pinHash")
    fun setPin(pin: String) {
        require(pin.matches(Regex("[0-9]{6,12}"))) { "Usa entre 6 y 12 dígitos" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("pinSalt", Base64.getEncoder().encodeToString(salt)).putString("pinHash", Base64.getEncoder().encodeToString(hash(pin, salt))).commit()
    }
    fun verifyPin(pin: String): Boolean = runCatching { MessageDigest.isEqual(Base64.getDecoder().decode(prefs.getString("pinHash", "")), hash(pin, Base64.getDecoder().decode(prefs.getString("pinSalt", "")))) }.getOrDefault(false)
    fun removePin() { prefs.edit().remove("pinSalt").remove("pinHash").commit() }
    private fun hash(pin: String, salt: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded
    companion object { private val lock = Any(); private val states = mutableMapOf<String,Pair<String?,State>>() }
}

object Backups {
    fun directory(context: Context) = File(context.filesDir, "backups").apply { mkdirs() }
    fun list(context: Context) = directory(context).listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun create(context: Context): File {
        val store = Store(context)
        val file = File(directory(context), "velo-${System.currentTimeMillis()}.json")
        val temp = File(directory(context), "backup.tmp")
        temp.writeText(store.export()); check(temp.renameTo(file))
        list(context).drop(store.load().settings.keepBackups).forEach { it.delete() }
        return file
    }
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (Store(context).load().settings.dailyBackup) manager.enqueueUniquePeriodicWork("velo-daily-backup", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build())
        else manager.cancelUniqueWork("velo-daily-backup")
    }
}
class BackupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = runCatching { if (Store(applicationContext).load().settings.dailyBackup) Backups.create(applicationContext); Result.success() }.getOrElse { Result.retry() }
}

object NumberCsv {
    fun export(rules: List<NumberRule>): String = "number,match,list,folder,note,enabled,action\r\n" + rules.joinToString("\r\n") { r -> listOf(r.value, r.match.name, if (r.allow) "allow" else "block", r.folder, r.note, r.enabled.toString(), r.action?.name ?: "").joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } }
    fun parse(text: String): List<List<String>> {
        require(text.toByteArray().size <= 2_000_000)
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row.add(cell.toString()); cell.clear() }
                (c == '\n' || c == '\r') && !quoted -> { if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++; row.add(cell.toString()); cell.clear(); if (row.any { it.isNotBlank() }) rows.add(row); row = mutableListOf() }
                else -> cell.append(c)
            }; i++
        }
        require(!quoted) { "CSV con comillas sin cerrar" }
        row.add(cell.toString()); if (row.any { it.isNotBlank() }) rows.add(row)
        require(rows.size <= 5001)
        return rows
    }
    fun import(text: String, canonical: (String) -> String): List<NumberRule> {
        val rows = parse(text.removePrefix("\uFEFF"))
        val header = rows.firstOrNull()?.map { it.lowercase().trim() } ?: return emptyList()
        val hasHeader = header.firstOrNull() in listOf("number", "numero", "número", "phone")
        return (if (hasHeader) rows.drop(1) else rows).map { row ->
            val number = row[0].trim(); require(number.length <= 64 && RuleEngine.digits(number).isNotEmpty()) { "Número inválido: $number" }
            val match = if (hasHeader && row.size > 1 && row[1].isNotBlank()) Match.valueOf(row[1].uppercase()) else Match.EXACT
            val allow = hasHeader && row.getOrNull(2)?.lowercase() == "allow"
            val action = if (hasHeader) row.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { Action.valueOf(it.uppercase()) } else null
            require(allow || action != Action.ALLOW)
            NumberRule(value = number, canonical = canonical(number), match = match, allow = allow, folder = if (hasHeader) row.getOrNull(3)?.takeIf { it.isNotBlank() }?.take(80) ?: "General" else "General", note = if (hasHeader) row.getOrNull(4)?.take(500) ?: "" else "", enabled = !hasHeader || row.getOrNull(5) != "false", action = action)
        }
    }
}

