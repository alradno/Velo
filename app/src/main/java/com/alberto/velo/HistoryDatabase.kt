package com.alberto.velo

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.CancellationSignal
import android.telephony.PhoneNumberUtils
import java.io.File
import java.io.Writer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal class HistoryDatabase private constructor(context: Context, path: String) : SQLiteOpenHelper(context, path, null, 2) {
    private val zoneLock = Any()
    @Volatile private var summaryZone: String? = null
    @Volatile private var migrated = false
    private data class StatsKey(val start: Long, val end: Long, val revision: Long, val zone: String)
    private val statsCache = object : LinkedHashMap<StatsKey, CallStatistics>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<StatsKey, CallStatistics>?) = size > 6
    }
    private val columns = arrayOf("id", "time", "number", "identity", "action", "reason", "source", "elapsed_ms")
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY NOT NULL, time INTEGER NOT NULL, number TEXT NOT NULL, number_key TEXT NOT NULL, identity TEXT NOT NULL, action TEXT NOT NULL, reason TEXT NOT NULL, cause TEXT NOT NULL, source TEXT NOT NULL, elapsed_ms INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX events_time ON events(time DESC, id DESC)")
        db.execSQL("CREATE INDEX events_number_time ON events(number_key, time DESC)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        HistorySummaries.create(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) HistorySummaries.create(db) }

    @Synchronized fun migrate(prefs: SharedPreferences, region: String) {
        if (migrated) return
        val db = writableDatabase
        val done = db.rawQuery("SELECT value FROM metadata WHERE key='legacy_imported'", null).use { it.moveToFirst() }
        if (!done) {
            // Decode before modifying anything. A corrupt legacy record must never become an empty history.
            val legacy = Codec.events(prefs.getString("events", "[]") ?: "[]")
            db.beginTransaction()
            try {
                legacy.forEach { insert(db, it, key(it.number, region)) }
                db.execSQL("INSERT INTO metadata(key,value) VALUES('legacy_imported','1')")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        // If the process died after committing SQLite, retrying never duplicates the old entries.
        if (prefs.contains("events")) check(prefs.edit().remove("events").commit())
        summaryZone = db.rawQuery("SELECT value FROM metadata WHERE key='summary_zone'", null).use { it.moveToFirst(); it.getString(0) }
        migrated = true
    }

    private fun insert(db: SQLiteDatabase, e: CallEvent, numberKey: String) {
        val values = ContentValues().apply {
            put("id", e.id); put("time", e.time); put("number", e.number); put("number_key", numberKey)
            put("identity", e.identity.name); put("action", e.action.name); put("reason", e.reason)
            put("cause", FilterCause.of(e.reason).name); put("source", e.source); put("elapsed_ms", e.elapsedMs)
        }
        check(db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L ||
            db.rawQuery("SELECT 1 FROM events WHERE id=?", arrayOf(e.id)).use { it.moveToFirst() })
    }
    fun record(e: CallEvent, region: String, days: Int, now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (summaryZone != ZoneId.systemDefault().id) {
                db.execSQL("UPDATE metadata SET value='invalid' WHERE key='summary_zone'")
                summaryZone = null
            }
            insert(db, e, key(e.number, region))
            HistoryMath.cutoff(days, now)?.let { db.delete("events", "time < ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun clear() {
        val db = writableDatabase; db.beginTransaction()
        try { HistorySummaries.dropTriggers(db); db.delete("events",null,null); HistorySummaries.rebuild(db); db.setTransactionSuccessful() }
        finally { db.endTransaction() }
        synchronized(statsCache) { statsCache.clear() }
    }
    fun count(): Long = HistorySummaries.counter(readableDatabase,"events")
    fun filteredSince(start: Long): Long {
        ensureZone()
        val date = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
        return if (date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() == start)
            scalar("SELECT COALESCE(SUM(n),0) FROM summary_groups WHERE day>=? AND action!='ALLOW'", arrayOf(date.toString()))
        else scalar("SELECT COUNT(*) FROM events WHERE time>=? AND action!='ALLOW'", arrayOf(start.toString()))
    }
    fun lastAttempt(number: String, region: String): Long? = readableDatabase.rawQuery("SELECT time FROM events WHERE number_key=? AND identity='VISIBLE' ORDER BY time DESC LIMIT 1", arrayOf(key(number, region))).use { if (it.moveToFirst()) it.getLong(0) else null }
    private fun scalar(sql: String, args: Array<String>? = null): Long = readableDatabase.rawQuery(sql, args).use { it.moveToFirst(); it.getLong(0) }
    private fun key(number: String, region: String) = PhoneNumberUtils.formatNumberToE164(number, region) ?: RuleEngine.normalized(number)

    fun page(onlyFiltered: Boolean = false, search: String = "", start: Long = 0L, before: EventCursor? = null, limit: Int = 100, after: EventCursor? = null, signal: CancellationSignal? = null): HistoryPage {
        require(limit in 1..500)
        require(before == null || after == null)
        val clauses = mutableListOf("time>=?"); val args = mutableListOf(start.toString())
        if (onlyFiltered) clauses += "action!='ALLOW'"
        if (search.isNotBlank()) {
            // Search the distinct values first, rather than evaluating three substrings per event.
            // Rare matches use the number/reason indexes; frequent matches scan the time index until a page fills.
            val needle = search.trim()
            val matches = readableDatabase.rawQuery("SELECT value,n FROM search_values WHERE instr(lower(value),lower(?))>0 LIMIT 65",arrayOf(needle),signal).use { c -> buildList { while(c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }
            val privateLabel = "privado oculto".contains(needle,ignoreCase=true)
            if (matches.isEmpty() && !privateLabel) return HistoryPage(emptyList(),null)
            if (!privateLabel && matches.size <= 64 && matches.sumOf { it.second } <= 5000) {
                val placeholders = List(matches.size) { "?" }.joinToString(",")
                clauses += "id IN (SELECT id FROM events WHERE number IN ($placeholders) UNION SELECT id FROM events WHERE number_key IN ($placeholders) UNION SELECT id FROM events WHERE reason IN ($placeholders))"
                repeat(3) { args += matches.map { it.first } }
            } else {
                clauses += "(instr(lower(number),lower(?))>0 OR instr(lower(reason),lower(?))>0 OR instr(lower(number_key),lower(?))>0 OR (identity='PRIVATE' AND instr('privado oculto',lower(?))>0))"
                repeat(4) { args += needle }
            }
        }
        before?.let { clauses += "(time,id)<(?,?)"; args += listOf(it.time.toString(),it.id) }
        after?.let { clauses += "(time,id)>(?,?)"; args += listOf(it.time.toString(),it.id) }
        val order = if (after == null) "time DESC,id DESC" else "time ASC,id ASC"
        val rows = readableDatabase.query(false,"events", columns, clauses.joinToString(" AND "), args.toTypedArray(), null, null, order, (limit + 1).toString(), signal).use { c -> buildList { while (c.moveToNext()) add(event(c)) } }
        val entries = if (after == null) rows.take(limit) else rows.take(limit).reversed()
        val next = entries.lastOrNull()?.takeIf { after != null || rows.size > limit }?.let { EventCursor(it.time,it.id) }
        val previous = entries.firstOrNull()?.takeIf { if(after != null) rows.size > limit else before != null }?.let { EventCursor(it.time,it.id) }
        return HistoryPage(entries,next,previous)
    }
    private fun event(c: Cursor) = CallEvent(c.getString(0),c.getLong(1),c.getString(2),Identity.valueOf(c.getString(3)),Action.valueOf(c.getString(4)),c.getString(5),c.getString(6),c.getLong(7))

    private fun ensureZone() {
        val zone = ZoneId.systemDefault().id
        if (summaryZone == zone) return
        synchronized(zoneLock) {
            if (summaryZone != zone) {
                val db = writableDatabase; db.beginTransaction()
                try { HistorySummaries.rebuild(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
                summaryZone = zone
                synchronized(statsCache) { statsCache.clear() }
            }
        }
    }
    fun statistics(start: Long, end: Long, signal: CancellationSignal? = null): CallStatistics {
        require(end > start)
        ensureZone()
        val db = readableDatabase
        // On Android 15+, a genuine read-only WAL transaction never reserves the writer connection.
        if (Build.VERSION.SDK_INT >= 35) db.beginTransactionReadOnly() else db.beginTransactionNonExclusive()
        try {
            signal?.throwIfCanceled()
            val revision = HistorySummaries.counter(db,"revision")
            val last = db.rawQuery("SELECT time FROM events ORDER BY time DESC,id DESC LIMIT 1", null, signal).use { if(it.moveToFirst()) it.getLong(0) else 0L }
            val cacheKey = StatsKey(start,minOf(end,last + 1),revision,ZoneId.systemDefault().id)
            synchronized(statsCache) { statsCache[cacheKey] }?.let { return it }
            val result = HistorySummaries.statistics(db,start,end,signal)
            synchronized(statsCache) { statsCache[cacheKey] = result }
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    fun export(writer: Writer) {
        writer.write("id,date,number,identity,action,reason,evaluation_ms\r\n")
        readableDatabase.query("events", columns, null, null, null, null, "time DESC,id DESC").use { c ->
            while (c.moveToNext()) {
                val e = event(c)
                val values = listOf(e.id, Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(), e.number, e.identity.name, e.action.name, e.reason, e.elapsedMs.toString())
                writer.write(values.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\r\n")
            }
        }
    }

    companion object {
        private val instances = mutableMapOf<String, HistoryDatabase>()
        private fun path(context: Context) = File(context.filesDir, "velo-history.db").absolutePath
        @Synchronized fun get(context: Context): HistoryDatabase = instances.getOrPut(path(context)) { HistoryDatabase(context.applicationContext, path(context)) }
        @Synchronized fun close(context: Context) { instances.remove(path(context))?.close() }
    }
}
