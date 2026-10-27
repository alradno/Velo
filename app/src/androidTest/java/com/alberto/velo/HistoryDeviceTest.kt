package com.alberto.velo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.StringWriter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryDeviceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "history-test-${UUID.randomUUID()}"
    private val directory = File(base.filesDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = base.getSharedPreferences("$prefix-$name", mode)
        override fun getFilesDir(): File = directory
    }
    private val prefs get() = context.getSharedPreferences("velo", Context.MODE_PRIVATE)
    private val now = System.currentTimeMillis()
    private fun e(id: String, time: Long = now, action: Action = Action.REJECT, number: String = "+33612345678", identity: Identity = Identity.VISIBLE, reason: String = "Regla · ejemplo") = CallEvent(id, time, number, identity, action, reason, "Filtrado", 5)
    private fun seed(events: List<CallEvent>, state: State = State(settings = Settings(enabled = true, retentionDays = 90))) { prefs.edit().putString("state", Codec.encode(state).toString()).putString("events", JSONArray(events.map(Codec::event)).toString()).commit() }
    @After fun cleanup() { HistoryDatabase.close(context); base.deleteSharedPreferences("$prefix-velo"); directory.deleteRecursively() }

    @Test fun legacyMigrationIsLosslessIdempotentAndDoesNotResetConfiguration() {
        val old = listOf(e("old", now - 800L * 86_400_000), e("new"))
        val state = State(settings = Settings(enabled = true, region = "FR", retentionDays = 365, animations = false), rules = listOf(NumberRule(value = "+33123", note = "Conservar")))
        seed(old, state); prefs.edit().putString("pinHash", "preserve-hash").commit()
        val store = Store(context)
        assertEquals(state.copy(settings = state.settings.copy(retentionDays = 0)), store.load())
        assertEquals(old.toSet(), store.historyPage().events.toSet()); assertFalse(prefs.contains("events")); assertEquals("preserve-hash", prefs.getString("pinHash", null))
        HistoryDatabase.close(context)
        assertEquals(2L, Store(context).eventCount()); assertEquals(old.toSet(), Store(context).historyPage().events.toSet())
    }
    @Test fun corruptLegacyIsPreservedAndCanBeRetried() {
        seed(emptyList()); prefs.edit().putString("events", "{invalid").commit()
        assertTrue(runCatching { Store(context).eventCount() }.isFailure); assertEquals("{invalid", prefs.getString("events", null))
        prefs.edit().putString("events", JSONArray(listOf(Codec.event(e("recovered")))).toString()).commit()
        assertEquals(1L, Store(context).eventCount())
    }
    @Test fun permanentKeepsOver5000OldEntriesAndPagesWithoutLossOrDuplicates() {
        val old = (0..5005).map { e("id-${it.toString().padStart(5, '0')}", now - 800L * 86_400_000 + it / 3) }
        seed(old); val store = Store(context); store.record(e("latest"))
        assertEquals(5007L, store.eventCount())
        val ids = mutableSetOf<String>(); var cursor: EventCursor? = null
        do { val page = store.historyPage(before = cursor); page.events.forEach { assertTrue(ids.add(it.id)) }; cursor = page.next } while (cursor != null)
        assertEquals(5007, ids.size); assertTrue("id-00000" in ids)
    }
    @Test fun finiteRetentionKeepsBoundaryAndPermanentDoesNotPrune() {
        val db = HistoryDatabase.get(context); db.migrate(prefs, "FR")
        val cutoff = now - 7 * 86_400_000L
        db.record(e("expired", cutoff - 1), "FR", 0, now)
        db.record(e("boundary", cutoff), "FR", 0, now)
        assertEquals(2L, db.count())
        db.record(e("current"), "FR", 7, now)
        assertEquals(setOf("boundary", "current"), db.page().events.map { it.id }.toSet())
    }
    @Test fun userCanChooseFiniteRetentionAfterTheUpgrade() {
        seed(listOf(e("one")))
        val store = Store(context); assertEquals(0, store.load().settings.retentionDays)
        store.save(store.load().copy(settings = store.load().settings.copy(retentionDays = 7)))
        assertEquals(7, Store(context).load().settings.retentionDays)
    }
    @Test fun groupedStatisticsSeparateMethodsPrivateCallsAndVisibleCallers() {
        val events = listOf(e("private", identity = Identity.PRIVATE, number = "", reason = "Número privado"), e("private-allowed", action = Action.ALLOW, identity = Identity.PRIVATE, number = "", reason = "Protección desactivada"), e("exact-one"), e("exact-two", action = Action.IGNORE, number = "0033612345678"), e("unknown", action = Action.SILENCE, number = "+33622345678", reason = "No está en contactos"), e("allowed", action = Action.ALLOW, number = "+33632345678"))
        seed(events, State(settings = Settings(region = "FR"))); val store = Store(context); val stats = store.statistics(0, now + 1)
        assertEquals(6L, stats.total); assertEquals(4L, stats.filtered); assertEquals(1L, stats.privateFiltered); assertEquals(2L, stats.uniqueVisibleFiltered)
        assertEquals(2L, stats.causes[FilterCause.RULE]); assertEquals(1L, stats.causes[FilterCause.UNKNOWN]); assertEquals(1L, stats.causes[FilterCause.PRIVATE])
        assertEquals(NumberFrequency("+33612345678", 2), stats.topNumbers.first()); assertEquals(4L, stats.hours.sum()); assertEquals(4L, stats.weekdays.sum()); assertEquals(6L, stats.daily.sumOf { it.total })
        val local = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
        assertEquals(4L, stats.hours[local.hour]); assertEquals(4L, stats.weekdays[local.dayOfWeek.value - 1])
    }
    @Test fun periodBoundsAreInclusiveAtStartAndExclusiveAtEnd() {
        seed(listOf(e("before", now - 1), e("start", now), e("end", now + 1000)))
        assertEquals(1L, Store(context).statistics(now, now + 1000).total)
    }
    @Test fun literalSearchCannotBecomeSqlOrWildcards() {
        seed(listOf(e("special", reason = "Regla · 50%_ ' prueba"), e("ordinary", reason = "Número privado", number = "", identity = Identity.PRIVATE)))
        val store = Store(context)
        assertEquals(listOf("special"), store.historyPage(search = "%_").events.map { it.id })
        assertEquals(0, store.historyPage(search = "' OR 1=1 --").events.size)
        assertEquals(1, store.historyPage(search = "oculto").events.size)
    }
    @Test fun indexedRepeatLookupFindsCallsBeyondFirstPage() {
        val events = (0..150).map { e("other-$it", now + it, number = "+33622345678") } + e("target", now - 1)
        seed(events, State(settings = Settings(region = "FR"))); val store = Store(context)
        assertFalse(store.events().any { it.id == "target" }); assertEquals(now - 1, store.lastAttempt("06 12 34 56 78"))
    }
    @Test fun csvExportsAllFieldsAndEscapesNotes() {
        val event = e("quoted", reason = "Regla · \"cita\", dos\nlíneas")
        seed(listOf(event)); val out = StringWriter(); Store(context).exportHistory(out)
        val rows = NumberCsv.parse(out.toString()); assertEquals(2, rows.size); assertEquals(event.id, rows[1][0]); assertEquals(event.reason, rows[1][5]); assertEquals("REJECT", rows[1][4])
    }
    @Test fun clearDoesNotResurrectLegacyAfterReopening() {
        seed(listOf(e("one"))); Store(context).clearEvents(); HistoryDatabase.close(context)
        assertEquals(0L, Store(context).eventCount()); assertEquals(0L, Store(context).statistics(0, now + 1).total)
    }
}
