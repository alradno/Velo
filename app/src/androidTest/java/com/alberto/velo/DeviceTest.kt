package com.alberto.velo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "test-${UUID.randomUUID()}"
    private val directory = File(base.filesDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = base.getSharedPreferences("$prefix-$name", mode)
        override fun getFilesDir(): File = directory
    }
    @After fun cleanup() { base.deleteSharedPreferences("$prefix-velo"); directory.deleteRecursively() }
    @Test fun rejectResponseMatchesAndroidContract() { val r = Responses.build(Decision(Action.REJECT, "test"), Settings()); assertTrue(r.disallowCall); assertTrue(r.rejectCall); assertFalse(r.silenceCall); assertFalse(r.skipCallLog) }
    @Test fun ignoreResponseIsDistinctFromReject() { val r = Responses.build(Decision(Action.IGNORE, "test"), Settings()); assertTrue(r.disallowCall); assertFalse(r.rejectCall); assertFalse(r.silenceCall) }
    @Test fun silenceKeepsCallAllowed() { val r = Responses.build(Decision(Action.SILENCE, "test"), Settings()); assertFalse(r.disallowCall); assertFalse(r.rejectCall); assertTrue(r.silenceCall) }
    @Test fun allowHasNoBlockingFlags() { val r = Responses.build(Decision(Action.ALLOW, "test"), Settings()); assertFalse(r.disallowCall); assertFalse(r.rejectCall); assertFalse(r.silenceCall) }
    @Test fun preferencesAndBackupRoundTripOnAndroid17() { val store = Store(context); val state = State(settings = Settings(enabled = true), rules = listOf(NumberRule(value = "612345678", canonical = "+34612345678"))); store.save(state); assertEquals(state, Store(context).load()); val backup = Backups.create(context); store.save(State()); store.restore(backup.readText()); assertEquals(state, store.load()) }
    @Test fun importFailurePreservesExistingData() { val store = Store(context); val initial = State(rules = listOf(NumberRule(value = "123"))); store.save(initial); assertTrue(runCatching { store.restore("{invalid}") }.isFailure); assertEquals(initial, store.load()) }
    @Test fun backupHistoryLimitWorks() { val store = Store(context); store.save(State(settings = Settings(keepBackups = 1))); Backups.create(context); Thread.sleep(4); Backups.create(context); assertEquals(1, Backups.list(context).size) }
    @Test fun pinIsNotExportedAndSurvivesRestore() { val store = Store(context); store.setPin("647291"); assertTrue(store.hasPin()); assertTrue(store.verifyPin("647291")); assertFalse(store.verifyPin("000000")); val exported = store.export(); assertFalse(exported.contains("647291")); assertFalse(exported.contains("pinHash")); store.restore(exported); assertTrue(store.verifyPin("647291")); store.removePin(); assertFalse(store.hasPin()) }
    @Test fun phoneNormalizationMatchesLocalAndInternational() { val store = Store(context); assertEquals("+34612345678", store.canonical("612 345 678", "ES")); assertEquals("+33612345678", store.canonical("06 12 34 56 78", "FR")) }
    @Test fun logCanExcludeAllowedCallsButRetainBlockedCalls() { val store = Store(context); store.save(State(settings = Settings(logAllowed = false))); store.record(CallEvent(number = "123", identity = Identity.VISIBLE, action = Action.ALLOW, reason = "test")); store.record(CallEvent(number = "456", identity = Identity.VISIBLE, action = Action.REJECT, reason = "test")); assertEquals(1, store.events().size); assertEquals("456", store.events().single().number) }
}
