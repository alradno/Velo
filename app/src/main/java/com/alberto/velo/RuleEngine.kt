package com.alberto.velo

import java.time.ZonedDateTime
import java.util.UUID

enum class Action(val label: String) { ALLOW("Permitir"), REJECT("Rechazar"), IGNORE("Ignorar"), SILENCE("Silenciar") }
enum class Match(val label: String) { EXACT("Número exacto"), PREFIX("Empieza por"), CONTAINS("Contiene"), SUFFIX("Termina en") }
enum class Identity(val label: String) { VISIBLE("Visible"), PRIVATE("Privado / oculto"), UNAVAILABLE("No disponible") }
data class NumberRule(val id: String = UUID.randomUUID().toString(), val value: String, val canonical: String = value, val match: Match = Match.EXACT, val allow: Boolean = false, val folder: String = "General", val note: String = "", val enabled: Boolean = true, val action: Action? = null, val profiles: Set<String> = emptySet())
data class Folder(val name: String = "General", val enabled: Boolean = true)
data class TimeWindow(val id: String = UUID.randomUUID().toString(), val days: Set<Int> = (1..7).toSet(), val start: Int = 0, val end: Int = 0, val enabled: Boolean = true) {
    fun active(now: ZonedDateTime): Boolean {
        if (!enabled) return false
        val today = now.dayOfWeek.value
        val previous = if (today == 1) 7 else today - 1
        val minute = now.hour * 60 + now.minute
        return when {
            start == end -> today in days
            start < end -> today in days && minute >= start && minute < end
            else -> (today in days && minute >= start) || (previous in days && minute < end)
        }
    }
}
data class Profile(val id: String = UUID.randomUUID().toString(), val name: String = "Personal", val privateCalls: Boolean = true, val unknownCalls: Boolean = true, val unavailableCalls: Boolean = true, val allCalls: Boolean = false, val voipCalls: Boolean = false, val action: Action = Action.REJECT, val scheduled: Boolean = false, val windows: List<TimeWindow> = emptyList(), val repeated: Boolean = false, val repeatMinutes: Int = 3, val allowList: Boolean = true)
data class Settings(val enabled: Boolean = false, val pauseUntil: Long = 0, val selectedProfile: String = "personal", val notifications: Boolean = true, val logAllowed: Boolean = true, val skipSystemNotification: Boolean = true, val theme: String = "dark", val fontScale: Float = 1f, val animations: Boolean = true, val region: String = "ES", val dailyBackup: Boolean = false, val keepBackups: Int = 7, val retentionDays: Int = 0)
data class State(val settings: Settings = Settings(), val profiles: List<Profile> = listOf(Profile(id = "personal")), val rules: List<NumberRule> = emptyList(), val folders: List<Folder> = listOf(Folder())) {
    val profile get() = profiles.find { it.id == settings.selectedProfile } ?: profiles.first()
    fun withRule(rule: NumberRule): State {
        val index = rules.indexOfFirst { it.id == rule.id }
        return copy(rules = if (index < 0) rules + rule else rules.mapIndexed { i, current -> if (i == index) rule else current })
    }
}
data class CallInput(val number: String = "", val canonical: String = number, val identity: Identity = Identity.VISIBLE, val inContacts: Boolean? = null, val voip: Boolean = false, val lastAttempt: Long? = null, val emergency: Boolean = false)
data class Decision(val action: Action, val reason: String, val ruleId: String? = null, val needsContacts: Boolean = false)
data class CallEvent(val id: String = UUID.randomUUID().toString(), val time: Long = System.currentTimeMillis(), val number: String, val identity: Identity, val action: Action, val reason: String, val source: String = "Filtrado", val elapsedMs: Long = 0)

object RuleEngine {
    private data class RankedRule(val index: Int, val rule: NumberRule, val needle: String, val normalized: String)
    private class RuleIndex(rules: List<RankedRule>) {
        private val canonical = mutableMapOf<String,RankedRule>()
        private val raw = mutableMapOf<String,RankedRule>()
        private val patterns = rules.filter { it.rule.match != Match.EXACT }
        init { rules.filter { it.rule.match == Match.EXACT }.forEach { r -> canonical.putIfAbsent(normalized(r.rule.canonical),r); raw.putIfAbsent(normalized(r.rule.value),r) } }
        fun find(input: CallInput, rawDigits: String, canonicalDigits: String, rawNormalized: String, canonicalNormalized: String): NumberRule? {
            if (input.identity != Identity.VISIBLE) return null
            var match = listOfNotNull(canonical[canonicalNormalized],raw[rawNormalized]).minByOrNull { it.index }
            for (r in patterns) {
                if (match != null && r.index >= match.index) break
                val applies = when(r.rule.match) {
                    Match.PREFIX -> rawDigits.startsWith(r.needle) || rawNormalized.startsWith(r.normalized) || canonicalNormalized.startsWith(r.normalized)
                    Match.CONTAINS -> rawDigits.contains(r.needle) || canonicalDigits.contains(r.needle)
                    Match.SUFFIX -> rawDigits.endsWith(r.needle) || canonicalDigits.endsWith(r.needle)
                    else -> false
                }
                if (applies) { match = r; break }
            }
            return match?.rule
        }
    }
    private data class Prepared(val allow: RuleIndex, val block: RuleIndex)
    private var preparedState: State? = null
    private var prepared: Prepared? = null
    @Synchronized private fun prepare(state: State): Prepared {
        if (preparedState === state) return prepared!!
        val disabled = state.folders.filter { !it.enabled }.map { it.name }.toSet()
        val profileId = state.profile.id
        val rules = state.rules.mapIndexedNotNull { index, r ->
            if (!r.enabled || r.folder in disabled || (r.profiles.isNotEmpty() && profileId !in r.profiles)) null
            else digits(r.value).takeIf { it.isNotEmpty() }?.let { RankedRule(index,r,it,normalized(r.value).ifEmpty { it }) }
        }
        return Prepared(RuleIndex(rules.filter { it.rule.allow }),RuleIndex(rules.filter { !it.rule.allow })).also { preparedState = state; prepared = it }
    }
    fun digits(value: String): String = value.filter { it in '0'..'9' }
    fun normalized(value: String): String { val trimmed = value.trim(); val d = digits(trimmed); return if (trimmed.startsWith("00")) d.drop(2) else d }
    fun matches(rule: NumberRule, input: CallInput): Boolean {
        if (input.identity != Identity.VISIBLE) return false
        val needle = digits(rule.value)
        val raw = digits(input.number)
        val canonical = digits(input.canonical)
        if (needle.isEmpty()) return false
        return when (rule.match) {
            Match.EXACT -> normalized(rule.canonical) == normalized(input.canonical) || normalized(rule.value) == normalized(input.number)
            Match.PREFIX -> {
                val prefix = normalized(rule.value).ifEmpty { needle }
                raw.startsWith(needle) || normalized(input.number).startsWith(prefix) || normalized(input.canonical).startsWith(prefix)
            }
            Match.CONTAINS -> raw.contains(needle) || canonical.contains(needle)
            Match.SUFFIX -> raw.endsWith(needle) || canonical.endsWith(needle)
        }
    }
    fun decide(state: State, input: CallInput, now: ZonedDateTime = ZonedDateTime.now()): Decision {
        val settings = state.settings
        val profile = state.profile
        if (input.emergency) return Decision(Action.ALLOW, "Llamada de emergencia")
        if (!settings.enabled) return Decision(Action.ALLOW, "Protección desactivada")
        if (settings.pauseUntil > now.toInstant().toEpochMilli()) return Decision(Action.ALLOW, "Pausa temporal")
        if (profile.scheduled && profile.windows.none { it.active(now) }) return Decision(Action.ALLOW, "Fuera del horario")
        val index = prepare(state)
        val rawDigits = digits(input.number); val canonicalDigits = digits(input.canonical)
        val rawNormalized = normalized(input.number); val canonicalNormalized = normalized(input.canonical)
        if (profile.allowList) index.allow.find(input,rawDigits,canonicalDigits,rawNormalized,canonicalNormalized)?.let { return Decision(Action.ALLOW, "Lista de permitidos · ${it.note.ifBlank { it.value }}", it.id) }
        if (profile.repeated && input.identity == Identity.VISIBLE && input.lastAttempt != null) {
            val elapsed = now.toInstant().toEpochMilli() - input.lastAttempt
            if (elapsed in 1..(profile.repeatMinutes * 60_000L)) return Decision(Action.ALLOW, "Excepción por llamada repetida")
        }
        index.block.find(input,rawDigits,canonicalDigits,rawNormalized,canonicalNormalized)?.let { return Decision(it.action ?: profile.action, "Regla · ${it.note.ifBlank { it.value }}", it.id) }
        if (profile.allCalls) return Decision(profile.action, "Todas las llamadas")
        if (profile.privateCalls && input.identity == Identity.PRIVATE) return Decision(profile.action, "Número privado")
        if (profile.unavailableCalls && input.identity == Identity.UNAVAILABLE) return Decision(profile.action, "Identidad no disponible")
        if (profile.voipCalls && input.voip) return Decision(profile.action, "Conversación VoIP activa")
        if (profile.unknownCalls && input.identity == Identity.VISIBLE && input.inContacts == false) return Decision(profile.action, "No está en contactos")
        if (profile.unknownCalls && input.identity == Identity.VISIBLE && input.inContacts == null) return Decision(Action.ALLOW, "Contactos sin verificar; se permite por seguridad", needsContacts = true)
        return Decision(Action.ALLOW, "Sin coincidencias")
    }
}

