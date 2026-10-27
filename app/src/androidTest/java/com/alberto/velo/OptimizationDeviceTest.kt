package com.alberto.velo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OptimizationDeviceTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix="opt-${UUID.randomUUID()}"
    private val directory=File(base.filesDir,prefix).apply { mkdirs() }
    private val context=object: ContextWrapper(base) {
        override fun getApplicationContext(): Context=this
        override fun getFilesDir(): File=directory
        override fun getSharedPreferences(name: String?,mode: Int): SharedPreferences=base.getSharedPreferences("$prefix-$name",mode)
    }
    private val now=System.currentTimeMillis()
    private fun event(id:String,time:Long=now,number:String="+33612345678")=CallEvent(id,time,number,Identity.VISIBLE,Action.REJECT,"Regla · $number")
    @After fun cleanup(){HistoryDatabase.close(context);base.deleteSharedPreferences("$prefix-velo");directory.deleteRecursively()}
    @Test fun summariesUpgradeVersionOneWithoutChangingEvents() {
        SQLiteDatabase.openOrCreateDatabase(File(directory,"velo-history.db"),null).use { db ->
            db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY,time INTEGER,number TEXT,number_key TEXT,identity TEXT,action TEXT,reason TEXT,cause TEXT,source TEXT,elapsed_ms INTEGER)")
            db.execSQL("CREATE INDEX events_time ON events(time DESC,id DESC)");db.execSQL("CREATE INDEX events_number_time ON events(number_key,time DESC)")
            db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT)");db.execSQL("INSERT INTO metadata VALUES('legacy_imported','1')")
            db.execSQL("INSERT INTO events VALUES('preserve',?,'+33612345678','+33612345678','VISIBLE','REJECT','Regla · ejemplo','RULE','Filtrado',5)",arrayOf(now))
            db.version=1
        }
        val store=Store(context);assertEquals(1L,store.eventCount());assertEquals("preserve",store.events().single().id);assertEquals(1L,store.statistics(0,now+1).filtered)
    }
    @Test fun summariesAndCacheFollowInsertDeleteAndSameCountReplacement() {
        val store=Store(context);store.record(event("a"));assertEquals(1L,store.statistics(0,now+1).total)
        HistoryDatabase.get(context).writableDatabase.delete("events","id=?",arrayOf("a"))
        store.record(event("b",number="+33622345678"))
        val stats=store.statistics(0,now+1);assertEquals(1L,stats.total);assertEquals("+33622345678",stats.topNumbers.single().number)
        store.clearEvents();assertEquals(0L,store.statistics(0,now+1).total)
    }
    @Test fun expiryRemovesDerivedCountsAndSearchValues() {
        val store=Store(context);store.record(event("old",now-800L*86400000,"+33622345678"))
        val db=HistoryDatabase.get(context);db.record(event("current"),"FR",7,now)
        val stats=store.statistics(0,now+1);assertEquals(1L,stats.total);assertEquals(1L,stats.uniqueVisibleFiltered);assertEquals(0,store.historyPage(search="+33622345678").events.size)
    }
    @Test fun pagesCanReloadDroppedDataInBothDirectionsWithTies() {
        val store=Store(context);repeat(350) { store.record(event(it.toString().padStart(4,'0'),now+it/3)) }
        val first=store.historyPage();val second=store.historyPage(before=first.next);val returned=store.historyPage(after=second.previous)
        assertEquals(first.events.map { it.id },returned.events.map { it.id });assertNull(returned.previous)
        assertTrue(first.events.none { a -> second.events.any { it.id==a.id } })
    }
    @Test fun canceledNativeSearchStopsWithoutReturningStaleResults() {
        val store=Store(context);store.record(event("a"));val signal=CancellationSignal().apply { cancel() }
        assertTrue(runCatching { store.historyPage(search="missing",signal=signal) }.exceptionOrNull() is android.os.OperationCanceledException)
    }
    @Test fun readSnapshotDoesNotBlockIncomingHistoryWritesOnAndroid17() {
        if(Build.VERSION.SDK_INT<35) return
        val store=Store(context);store.record(event("before"));val db=HistoryDatabase.get(context).readableDatabase
        val pool=Executors.newSingleThreadExecutor()
        db.beginTransactionReadOnly()
        try {
            assertEquals(1L,HistorySummaries.counter(db,"events"))
            pool.submit { store.record(event("during")) }.get(2,TimeUnit.SECONDS)
            assertEquals(1L,HistorySummaries.counter(db,"events"))
        } finally { db.endTransaction();pool.shutdownNow() }
        assertEquals(2L,store.eventCount())
    }
    @Test fun slowHistoryWriterDoesNotHoldTheConfigurationLock() {
        val store=Store(context);store.record(event("before"));val db=HistoryDatabase.get(context).writableDatabase;val pool=Executors.newFixedThreadPool(2)
        db.beginTransactionNonExclusive()
        try {
            val writing=pool.submit { store.record(event("queued")) }
            Thread.sleep(30)
            assertEquals(0,pool.submit<Int> { store.load().settings.retentionDays }.get(1,TimeUnit.SECONDS))
            assertFalse(writing.isDone)
        } finally { db.endTransaction();pool.shutdown();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS)) }
    }
    @Test fun invalidDerivedTimezoneCanBeRebuiltWithoutLosingHistory() {
        val store=Store(context);store.record(event("a"));HistoryDatabase.get(context).writableDatabase.execSQL("UPDATE metadata SET value='invalid' WHERE key='summary_zone'")
        HistoryDatabase.close(context)
        assertEquals(1L,Store(context).statistics(0,now+1).total);assertEquals("a",Store(context).events().single().id)
    }
}
