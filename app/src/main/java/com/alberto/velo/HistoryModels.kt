package com.alberto.velo

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class HistoryPeriod(val label: String, val days: Int?) {
    TODAY("Hoy", 1), WEEK("7 días", 7), MONTH("30 días", 30), QUARTER("90 días", 90), YEAR("1 año", 365), ALL("Todo", null);
    fun start(now: ZonedDateTime): Long = days?.let { now.toLocalDate().minusDays(it - 1L).atStartOfDay(now.zone).toInstant().toEpochMilli() } ?: 0L
}

enum class FilterCause(val label: String) {
    RULE("Reglas de números"), PRIVATE("Números privados"), UNKNOWN("Fuera de contactos"), UNAVAILABLE("Identidad no disponible"), ALL("Bloqueo total"), VOIP("Durante VoIP"), OTHER("Otros motivos");
    companion object {
        fun of(reason: String): FilterCause = when {
            reason.startsWith("Regla ·") -> RULE
            reason == "Número privado" -> PRIVATE
            reason == "No está en contactos" -> UNKNOWN
            reason == "Identidad no disponible" -> UNAVAILABLE
            reason == "Todas las llamadas" -> ALL
            reason == "Conversación VoIP activa" -> VOIP
            else -> OTHER
        }
    }
}

data class EventCursor(val time: Long, val id: String)
data class HistoryPage(val events: List<CallEvent>, val next: EventCursor?, val previous: EventCursor? = null)
data class DailyCalls(val date: LocalDate, val total: Long, val filtered: Long)
data class TrendBucket(val start: LocalDate, val end: LocalDate, val total: Long, val filtered: Long)
data class NumberFrequency(val number: String, val count: Long)
data class CallStatistics(
    val total: Long = 0, val actions: Map<Action, Long> = emptyMap(),
    val causes: Map<FilterCause, Long> = emptyMap(), val privateFiltered: Long = 0,
    val uniqueVisibleFiltered: Long = 0, val daily: List<DailyCalls> = emptyList(),
    val hours: List<Long> = List(24) { 0L }, val weekdays: List<Long> = List(7) { 0L },
    val topNumbers: List<NumberFrequency> = emptyList(), val firstTime: Long? = null
) {
    val filtered: Long get() = total - (actions[Action.ALLOW] ?: 0L)
    val percentage: Int get() = if (total == 0L) 0 else ((filtered.toDouble() / total) * 100).toInt()
}

object HistoryMath {
    // Retention zero means no automatic expiry, and there is no entry-count limit.
    fun cutoff(days: Int, now: Long): Long? = if (days == 0) null else now - days.toLong() * 86_400_000L

    fun buckets(daily: List<DailyCalls>, start: LocalDate, end: LocalDate, maxBuckets: Int = 14): List<TrendBucket> {
        require(maxBuckets > 0)
        if (end < start) return emptyList()
        val days = ChronoUnit.DAYS.between(start, end) + 1
        val width = ceil(days.toDouble() / maxBuckets).toLong().coerceAtLeast(1)
        val count = ((days + width - 1) / width).toInt()
        val totals = LongArray(count); val filtered = LongArray(count)
        daily.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.forEach {
            val index = (ChronoUnit.DAYS.between(start, it.date) / width).toInt()
            totals[index] += it.total; filtered[index] += it.filtered
        }
        return (0 until count).map { i ->
            val from = start.plusDays(i * width)
            TrendBucket(from, minOf(end, from.plusDays(width - 1)), totals[i], filtered[i])
        }
    }
}
