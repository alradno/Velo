package com.alberto.velo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PerformanceTest {
    @Test fun largeRuleSet() {
        val instrument=InstrumentationRegistry.getInstrumentation();val base=instrument.targetContext
        val prefix="rule-perf-${UUID.randomUUID()}";val directory=File(base.filesDir,prefix).apply { mkdirs() }
        val context=object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir():File=directory
            override fun getSharedPreferences(name:String?,mode:Int):SharedPreferences=base.getSharedPreferences("$prefix-$name",mode)
        }
        try {
            val store=Store(context);val rules=(0 until 5000).map { NumberRule(id="rule-$it",value="+346${it.toString().padStart(8,'0')}") }
            store.save(State(settings=Settings(enabled=true),rules=rules))
            val report=JSONObject().put("rules",5000)
            val first=SystemClock.elapsedRealtimeNanos();val input=CallInput(number=rules.last().value)
            assertEquals("rule-4999",RuleEngine.decide(store.load(),input).ruleId)
            report.put("firstDecisionMs",(SystemClock.elapsedRealtimeNanos()-first)/1_000_000.0)
            val reads=SystemClock.elapsedRealtimeNanos();repeat(1000) { assertEquals(5000,store.load().rules.size) };report.put("load1000Ms",(SystemClock.elapsedRealtimeNanos()-reads)/1_000_000.0)
            val decisions=SystemClock.elapsedRealtimeNanos();repeat(1000) { assertEquals("rule-4999",RuleEngine.decide(store.load(),input).ruleId) };report.put("decide1000Ms",(SystemClock.elapsedRealtimeNanos()-decisions)/1_000_000.0)
            report.put("correct",true)
            val bundle=Bundle();bundle.putString("rulesPerformance",report.toString());instrument.sendStatus(2,bundle)
        } finally { HistoryDatabase.close(context);base.deleteSharedPreferences("$prefix-velo");directory.deleteRecursively() }
    }
    @Test fun largeHistory() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val base = instrument.targetContext
        val prefix = "perf-${UUID.randomUUID()}"
        val directory = File(base.filesDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = base.getSharedPreferences("$prefix-$name", mode)
            override fun getFilesDir(): File = directory
        }
        val rows = InstrumentationRegistry.getArguments().getString("rows", "100000")!!.toInt().also { require(it in 1000..1_000_000) }
        val report = JSONObject().put("rows", rows).put("version", base.packageManager.getPackageInfo(base.packageName, 0).versionName)
        fun <T> measure(name: String, operation: () -> T): T {
            val start = SystemClock.elapsedRealtimeNanos(); val value = operation()
            report.put(name, (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
            return value
        }
        try {
            val helper = HistoryDatabase.get(context); helper.migrate(context.getSharedPreferences("velo", 0), "FR")
            val db = helper.writableDatabase
            val end = System.currentTimeMillis() - 1000
            val begin = end - 730L * 86_400_000
            measure("seedMs") {
                db.beginTransaction()
                try {
                    db.compileStatement("INSERT INTO events(id,time,number,number_key,identity,action,reason,cause,source,elapsed_ms) VALUES(?,?,?,?,?,?,?,?,?,?)").use { statement ->
                        repeat(rows) { i ->
                            val private = i % 5 == 0; val allow = i % 5 == 1
                            val number = if (private) "" else "+336${(i % 1000).toString().padStart(8, '0')}"
                            statement.bindString(1, "bench-${i.toString().padStart(8, '0')}"); statement.bindLong(2, begin + (end - begin) * i / rows)
                            statement.bindString(3, number); statement.bindString(4, number); statement.bindString(5, if (private) "PRIVATE" else "VISIBLE")
                            statement.bindString(6, if (allow) "ALLOW" else "REJECT"); statement.bindString(7, if (private) "Número privado" else "Regla · carga")
                            statement.bindString(8, if (private) "PRIVATE" else "RULE"); statement.bindString(9, "Benchmark aislado"); statement.bindLong(10, 5)
                            statement.executeInsert()
                        }
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            assertEquals(rows.toLong(), measure("countMs") { helper.count() })
            assertEquals(100, measure("firstPageMs") { helper.page() }.events.size)
            val cursor = EventCursor(begin + (end - begin) / 4, "bench-99999999")
            assertEquals(100, measure("deepPageMs") { helper.page(before = cursor) }.events.size)
            assertEquals(0, measure("noMatchSearchMs") { helper.page(search = "no-existe-zzzz") }.events.size)
            assertEquals(100, measure("numberSearchMs") { helper.page(search = "00000999") }.events.size)
            assertNotNull(measure("repeatLookupMs") { helper.lastAttempt("+33600000999", "FR") })
            val stats = measure("allStatisticsMs") { helper.statistics(0, end + 1000) }
            assertEquals(rows.toLong(), stats.total); assertEquals(rows.toLong() * 4 / 5, stats.filtered)
            assertEquals(rows.toLong(), measure("repeatStatisticsMs") { helper.statistics(0, end + 1001) }.total)
            assertEquals(2, stats.causes.size)
            report.put("correct", true).put("databaseBytes", directory.listFiles()?.sumOf { it.length() } ?: 0L)
            val bundle = Bundle(); bundle.putString("performance", report.toString()); instrument.sendStatus(2, bundle)
        } finally {
            HistoryDatabase.close(context); base.deleteSharedPreferences("$prefix-velo"); directory.deleteRecursively()
        }
    }
}
