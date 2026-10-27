package com.alberto.velo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryMathTest {
    @Test fun permanentHasNoAgeCutoff() { assertNull(HistoryMath.cutoff(0, 1_800_000_000_000L)) }
    @Test fun finiteRetentionUsesLongArithmetic() { assertEquals(1_800_000_000_000L - 365L * 86_400_000L, HistoryMath.cutoff(365, 1_800_000_000_000L)) }
    @Test fun permanentSettingSurvivesBackupRoundTrip() { val s = State(settings = Settings(retentionDays = 0)); assertEquals(0, Codec.decode(Codec.encode(s)).settings.retentionDays) }
    @Test fun finiteBackupRemainsFinite() { val s = State(settings = Settings(retentionDays = 30)); assertEquals(30, Codec.decode(Codec.encode(s)).settings.retentionDays) }
    @Test fun missingRetentionDefaultsToPermanent() { val json = Codec.encode(State()); json.getJSONObject("settings").remove("retentionDays"); assertEquals(0, Codec.decode(json).settings.retentionDays) }
    @Test fun weekUsesLocalCalendarAcrossDst() {
        val zone = ZoneId.of("Europe/Madrid"); val now = ZonedDateTime.of(2026, 3, 30, 13, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 3, 24).atStartOfDay(zone).toInstant().toEpochMilli(), HistoryPeriod.WEEK.start(now))
        assertNotEquals(now.toInstant().toEpochMilli() - 7 * 86_400_000L, HistoryPeriod.WEEK.start(now))
    }
    @Test fun todayBeginsAtLocalMidnight() { val now = ZonedDateTime.parse("2026-10-26T21:11:00+01:00[Europe/Madrid]"); assertEquals(now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli(), HistoryPeriod.TODAY.start(now)) }
    @Test fun allIncludesOldRecords() { assertEquals(0L, HistoryPeriod.ALL.start(ZonedDateTime.now())) }
    @Test fun emptyDatesHaveZeroBars() { val start = LocalDate.of(2026, 10, 1); val bars = HistoryMath.buckets(emptyList(), start, start.plusDays(6)); assertEquals(7, bars.size); assertTrue(bars.all { it.total == 0L && it.filtered == 0L }) }
    @Test fun yearsOfDataFitWithoutDroppingCounts() {
        val start = LocalDate.of(2020, 1, 1); val end = LocalDate.of(2026, 10, 26)
        val data = listOf(DailyCalls(start, 5, 3), DailyCalls(end, 4, 2), DailyCalls(start.minusDays(1), 999, 999))
        val bars = HistoryMath.buckets(data, start, end)
        assertTrue(bars.size <= 14); assertEquals(9L, bars.sumOf { it.total }); assertEquals(5L, bars.sumOf { it.filtered }); assertEquals(start, bars.first().start); assertEquals(end, bars.last().end)
        assertTrue(bars.zipWithNext().all { (a, b) -> a.end.plusDays(1) == b.start })
    }
    @Test fun reasonsAreGroupedWithoutMixingRuleNotes() { assertEquals(FilterCause.RULE, FilterCause.of("Regla · Cliente A")); assertEquals(FilterCause.RULE, FilterCause.of("Regla · +33612345678")); assertEquals(FilterCause.UNKNOWN, FilterCause.of("No está en contactos")); assertEquals(FilterCause.PRIVATE, FilterCause.of("Número privado")) }
    @Test fun filteredIncludesSilenceButKeepsMethodsSeparate() { val stats = CallStatistics(total = 10, actions = mapOf(Action.ALLOW to 4, Action.REJECT to 3, Action.IGNORE to 2, Action.SILENCE to 1)); assertEquals(6, stats.filtered.toInt()); assertEquals(60, stats.percentage) }
    @Test fun emptyStatisticsHaveNoPercentageOrFakeCalls() { assertEquals(0, CallStatistics().percentage); assertEquals(0L, CallStatistics().filtered) }
}
