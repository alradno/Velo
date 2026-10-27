package com.alberto.velo

import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Rebuildable derived data. Original events remain authoritative and are never discarded. */
internal object HistorySummaries {
    private const val DAY = "date(time/1000,'unixepoch','localtime')"
    private const val HOUR = "CAST(strftime('%H',time/1000,'unixepoch','localtime') AS INTEGER)"
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE summary_groups(day TEXT,action TEXT,cause TEXT,identity TEXT,n INTEGER NOT NULL,PRIMARY KEY(day,action,cause,identity)) WITHOUT ROWID")
        db.execSQL("CREATE TABLE summary_hours(day TEXT,hour INTEGER,n INTEGER NOT NULL,PRIMARY KEY(day,hour)) WITHOUT ROWID")
        db.execSQL("CREATE TABLE summary_numbers(day TEXT,number_key TEXT,n INTEGER NOT NULL,PRIMARY KEY(day,number_key)) WITHOUT ROWID")
        db.execSQL("CREATE TABLE summary_number_totals(number_key TEXT PRIMARY KEY,n INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE INDEX summary_number_ranking ON summary_number_totals(n DESC,number_key)")
        db.execSQL("CREATE TABLE summary_counters(name TEXT PRIMARY KEY,n INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE TABLE search_values(value TEXT PRIMARY KEY,n INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE INDEX events_search_number ON events(number,time DESC,id DESC)")
        db.execSQL("CREATE INDEX events_search_reason ON events(reason,time DESC,id DESC)")
        db.execSQL("INSERT INTO summary_counters VALUES('events',0),('revision',0)")
        rebuild(db)
    }
    private fun triggers(db: SQLiteDatabase) {
        for ((name, alias, delta) in listOf(Triple("insert", "new", 1), Triple("delete", "old", -1))) {
            val day = DAY.replace("time/", "$alias.time/"); val hour = HOUR.replace("time/", "$alias.time/")
            val visible = "$alias.action!='ALLOW' AND $alias.identity='VISIBLE' AND $alias.number_key!=''"
            val prepare = if (delta > 0) """
                INSERT OR IGNORE INTO summary_groups VALUES($day,$alias.action,$alias.cause,$alias.identity,0);
                INSERT OR IGNORE INTO summary_hours SELECT $day,$hour,0 WHERE $alias.action!='ALLOW';
                INSERT OR IGNORE INTO summary_numbers SELECT $day,$alias.number_key,0 WHERE $visible;
                INSERT OR IGNORE INTO summary_number_totals SELECT $alias.number_key,0 WHERE $visible;
                INSERT OR IGNORE INTO search_values SELECT $alias.number,0 WHERE $alias.number!='';
                INSERT OR IGNORE INTO search_values SELECT $alias.number_key,0 WHERE $alias.number_key!='';
                INSERT OR IGNORE INTO search_values SELECT $alias.reason,0 WHERE $alias.reason!='';
            """ else ""
            val cleanup = if (delta < 0) """
                DELETE FROM summary_groups WHERE day=$day AND action=$alias.action AND cause=$alias.cause AND identity=$alias.identity AND n=0;
                DELETE FROM summary_hours WHERE day=$day AND hour=$hour AND n=0;
                DELETE FROM summary_numbers WHERE day=$day AND number_key=$alias.number_key AND n=0;
                DELETE FROM summary_number_totals WHERE number_key=$alias.number_key AND n=0;
                DELETE FROM search_values WHERE value IN ($alias.number,$alias.number_key,$alias.reason) AND n=0;
            """ else ""
            db.execSQL("""CREATE TRIGGER events_summary_$name AFTER ${name.uppercase()} ON events BEGIN
                UPDATE summary_counters SET n=n+$delta WHERE name='events';
                UPDATE summary_counters SET n=n+1 WHERE name='revision';
                $prepare
                UPDATE summary_groups SET n=n+$delta WHERE day=$day AND action=$alias.action AND cause=$alias.cause AND identity=$alias.identity;
                UPDATE summary_hours SET n=n+$delta WHERE day=$day AND hour=$hour AND $alias.action!='ALLOW';
                UPDATE summary_numbers SET n=n+$delta WHERE day=$day AND number_key=$alias.number_key AND $visible;
                UPDATE summary_number_totals SET n=n+$delta WHERE number_key=$alias.number_key AND $visible;
                UPDATE search_values SET n=n+$delta WHERE value=$alias.number AND $alias.number!='';
                UPDATE search_values SET n=n+$delta WHERE value=$alias.number_key AND $alias.number_key!='';
                UPDATE search_values SET n=n+$delta WHERE value=$alias.reason AND $alias.reason!='';
                $cleanup
            END""")
        }
    }
    fun dropTriggers(db: SQLiteDatabase) { db.execSQL("DROP TRIGGER IF EXISTS events_summary_insert"); db.execSQL("DROP TRIGGER IF EXISTS events_summary_delete") }
    fun rebuild(db: SQLiteDatabase) {
        dropTriggers(db)
        listOf("summary_groups", "summary_hours", "summary_numbers", "summary_number_totals", "search_values").forEach { db.execSQL("DELETE FROM $it") }
        db.execSQL("INSERT INTO summary_groups SELECT $DAY,action,cause,identity,COUNT(*) FROM events GROUP BY 1,2,3,4")
        db.execSQL("INSERT INTO summary_hours SELECT $DAY,$HOUR,COUNT(*) FROM events WHERE action!='ALLOW' GROUP BY 1,2")
        db.execSQL("INSERT INTO summary_numbers SELECT $DAY,number_key,COUNT(*) FROM events WHERE action!='ALLOW' AND identity='VISIBLE' AND number_key!='' GROUP BY 1,2")
        db.execSQL("INSERT INTO summary_number_totals SELECT number_key,COUNT(*) FROM events WHERE action!='ALLOW' AND identity='VISIBLE' AND number_key!='' GROUP BY 1")
        db.execSQL("INSERT INTO search_values SELECT value,COUNT(*) FROM (SELECT number AS value FROM events WHERE number!='' UNION ALL SELECT number_key FROM events WHERE number_key!='' UNION ALL SELECT reason FROM events WHERE reason!='') GROUP BY value")
        db.execSQL("UPDATE summary_counters SET n=(SELECT COUNT(*) FROM events) WHERE name='events'")
        db.execSQL("UPDATE summary_counters SET n=n+1 WHERE name='revision'")
        db.execSQL("INSERT OR REPLACE INTO metadata VALUES('summary_zone',?)", arrayOf(ZoneId.systemDefault().id))
        triggers(db)
    }
    fun counter(db: SQLiteDatabase, name: String): Long = db.rawQuery("SELECT n FROM summary_counters WHERE name=?", arrayOf(name)).use { it.moveToFirst(); it.getLong(0) }

    fun statistics(db: SQLiteDatabase, start: Long, end: Long, signal: CancellationSignal?): CallStatistics {
        val zone = ZoneId.systemDefault()
        fun query(sql: String, args: List<String> = emptyList()) = db.rawQuery(sql, args.toTypedArray(), signal)
        val first = query("SELECT time FROM events ORDER BY time ASC,id ASC LIMIT 1").use { if (it.moveToFirst()) it.getLong(0) else null } ?: return CallStatistics()
        val last = query("SELECT time FROM events ORDER BY time DESC,id DESC LIMIT 1").use { it.moveToFirst(); it.getLong(0) }
        fun date(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        fun midnight(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val startDate = date(start); val endDate = date(end)
        // Entire local days use summaries; only partial boundary days need original events.
        val from = if (start <= first) date(first) else if (start == midnight(startDate)) startDate else startDate.plusDays(1)
        val until = if (end > last) date(last).plusDays(1) else endDate
        val full = from < until
        val partial = mutableListOf<Pair<Long, Long>>()
        if (!full) partial += start to end
        else {
            if (start < midnight(from)) partial += start to minOf(end, midnight(from))
            if (end > midnight(until)) partial += maxOf(start, midnight(until)) to end
        }
        val actions = mutableMapOf<Action, Long>(); val causes = mutableMapOf<FilterCause, Long>()
        val daily = sortedMapOf<LocalDate, Pair<Long, Long>>(); val hours = MutableList(24) { 0L }; var privateCount = 0L
        fun add(day: String, actionText: String, causeText: String, identity: String, amount: Long) {
            signal?.throwIfCanceled()
            val action = Action.valueOf(actionText); actions[action] = (actions[action] ?: 0L) + amount
            val filtered = if (action != Action.ALLOW) amount else 0L
            if (filtered > 0) { val cause = FilterCause.valueOf(causeText); causes[cause] = (causes[cause] ?: 0L) + amount; if (identity == "PRIVATE") privateCount += amount }
            val d = LocalDate.parse(day); val old = daily[d] ?: (0L to 0L); daily[d] = old.first + amount to old.second + filtered
        }
        if (full) {
            query("SELECT day,action,cause,identity,n FROM summary_groups WHERE day>=? AND day<?", listOf(from.toString(), until.toString())).use { c -> while (c.moveToNext()) add(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)) }
            query("SELECT hour,SUM(n) FROM summary_hours WHERE day>=? AND day<? GROUP BY hour", listOf(from.toString(), until.toString())).use { c -> while(c.moveToNext()) hours[c.getInt(0)] += c.getLong(1) }
        }
        for ((low, high) in partial) {
            val args = listOf(low.toString(), high.toString())
            query("SELECT $DAY,action,cause,identity,COUNT(*) FROM events WHERE time>=? AND time<? GROUP BY 1,2,3,4", args).use { c -> while(c.moveToNext()) add(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)) }
            query("SELECT $HOUR,COUNT(*) FROM events WHERE time>=? AND time<? AND action!='ALLOW' GROUP BY 1", args).use { c -> while(c.moveToNext()) hours[c.getInt(0)] += c.getLong(1) }
        }
        val all = start <= first && end > last
        val numberArgs = mutableListOf<String>()
        val parts = mutableListOf<String>()
        if (full) { parts += "SELECT number_key,n FROM summary_numbers WHERE day>=? AND day<?"; numberArgs += listOf(from.toString(), until.toString()) }
        partial.forEach { (low, high) -> parts += "SELECT number_key,COUNT(*) AS n FROM events WHERE time>=? AND time<? AND action!='ALLOW' AND identity='VISIBLE' AND number_key!='' GROUP BY number_key"; numberArgs += listOf(low.toString(), high.toString()) }
        val numbersSql = if (all) "SELECT number_key,n FROM summary_number_totals" else "SELECT number_key,SUM(n) AS n FROM (${parts.joinToString(" UNION ALL ")}) GROUP BY number_key"
        val unique = query("SELECT COUNT(*) FROM ($numbersSql)", if(all) emptyList() else numberArgs).use { it.moveToFirst(); it.getLong(0) }
        val top = query("$numbersSql ORDER BY n DESC,number_key ASC LIMIT 5", if(all) emptyList() else numberArgs).use { c -> buildList { while(c.moveToNext()) add(NumberFrequency(c.getString(0),c.getLong(1))) } }
        val weekdays = MutableList(7) { 0L }
        daily.forEach { (day, values) -> weekdays[day.dayOfWeek.value - 1] += values.second }
        val firstInPeriod = query("SELECT time FROM events WHERE time>=? AND time<? ORDER BY time,id LIMIT 1", listOf(start.toString(),end.toString())).use { if(it.moveToFirst()) it.getLong(0) else null }
        return CallStatistics(actions.values.sum(), actions, causes, privateCount, unique, daily.map { DailyCalls(it.key,it.value.first,it.value.second) }, hours, weekdays, top, firstInPeriod)
    }
}
