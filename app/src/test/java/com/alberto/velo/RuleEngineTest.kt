package com.alberto.velo

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class RuleEngineTest {
    private val monday = ZonedDateTime.parse("2026-10-26T23:00:00+01:00[Europe/Madrid]")
    private val basic = State(settings = Settings(enabled = true))
    private val unknown = CallInput("612345678", "+34612345678", inContacts = false)
    private fun decide(s: State = basic, i: CallInput = unknown, now: ZonedDateTime = monday) = RuleEngine.decide(s, i, now)
    private fun profile(p: Profile) = basic.copy(profiles = listOf(p.copy(id = "personal")))
    @Test fun disabledAllows() { assertEquals(Action.ALLOW, decide(basic.copy(settings = Settings(enabled = false))).action) }
    @Test fun pauseAllowsAndExpires() { val paused = basic.copy(settings = basic.settings.copy(pauseUntil = monday.toInstant().toEpochMilli() + 60_000)); assertEquals(Action.ALLOW, decide(paused).action); assertEquals(Action.REJECT, decide(paused, now = monday.plusMinutes(1)).action) }
    @Test fun emergencyAlwaysAllowed() { assertEquals(Action.ALLOW, decide(i = unknown.copy(emergency = true)).action) }
    @Test fun unknownRejected() { assertEquals(Action.REJECT, decide().action) }
    @Test fun contactAllowed() { assertEquals(Action.ALLOW, decide(i = unknown.copy(inContacts = true)).action) }
    @Test fun missingContactPermissionIsNotUnknown() { assertEquals(Action.ALLOW, decide(i = unknown.copy(inContacts = null)).action) }
    @Test fun privateRejected() { assertEquals(Action.REJECT, decide(i = CallInput(identity = Identity.PRIVATE)).action) }
    @Test fun unavailableCanBeIndependentOfPrivate() { val s = profile(basic.profile.copy(privateCalls = false, unavailableCalls = true)); assertEquals(Action.ALLOW, decide(s, CallInput(identity = Identity.PRIVATE)).action); assertEquals(Action.REJECT, decide(s, CallInput(identity = Identity.UNAVAILABLE)).action) }
    @Test fun whitelistWinsAgainstAllCallsAndBlacklist() { val s = profile(basic.profile.copy(allCalls = true)).copy(rules = listOf(NumberRule(value = "612345678"), NumberRule(value = "612345678", allow = true))); assertEquals(Action.ALLOW, decide(s).action) }
    @Test fun disabledWhitelistDoesNotPass() { assertEquals(Action.REJECT, decide(basic.copy(rules = listOf(NumberRule(value = unknown.number, allow = true, enabled = false)))).action) }
    @Test fun profileCanDisableWhitelist() { val s = profile(basic.profile.copy(allowList = false)).copy(rules = listOf(NumberRule(value = unknown.number, allow = true))); assertEquals(Action.REJECT, decide(s).action) }
    @Test fun ruleAppliesOnlyToSelectedProfiles() { val s = profile(basic.profile.copy(unknownCalls = false)).copy(rules = listOf(NumberRule(value = unknown.number, profiles = setOf("office")))); assertEquals(Action.ALLOW, decide(s).action); assertEquals(Action.REJECT, decide(s.copy(rules = s.rules.map { it.copy(profiles = setOf("personal")) })).action) }
    @Test fun folderPauseDisablesBothLists() { val s = basic.copy(folders = listOf(Folder("General", false)), rules = listOf(NumberRule(value = unknown.number, allow = true))); assertEquals(Action.REJECT, decide(s).action) }
    @Test fun explicitBlacklistCanBlockContact() { val s = basic.copy(rules = listOf(NumberRule(value = unknown.number))); assertEquals(Action.REJECT, decide(s, unknown.copy(inContacts = true)).action) }
    @Test fun localAndInternationalMatchWithCanonical() { val r = NumberRule(value = "612345678", canonical = "+34612345678"); assertTrue(RuleEngine.matches(r, unknown.copy(number = "+34 612 345 678"))) }
    @Test fun internationalPrefixHandles00() { assertTrue(RuleEngine.matches(NumberRule(value = "0034", match = Match.PREFIX), unknown)) }
    @Test fun localPrefixMatchesLocalForm() { assertTrue(RuleEngine.matches(NumberRule(value = "612", match = Match.PREFIX), unknown)) }
    @Test fun containsAndSuffix() { assertTrue(RuleEngine.matches(NumberRule(value = "345", match = Match.CONTAINS), unknown)); assertTrue(RuleEngine.matches(NumberRule(value = "678", match = Match.SUFFIX), unknown)) }
    @Test fun containsPreservesLeadingZerosInPattern() {
        val rule = NumberRule(value = "0012", match = Match.CONTAINS)
        assertTrue(RuleEngine.matches(rule, CallInput("610012678")))
        assertFalse(RuleEngine.matches(rule, CallInput("6112678")))
    }
    @Test fun doubleZeroPatternsRemainUsable() {
        assertTrue(RuleEngine.matches(NumberRule(value = "00", match = Match.CONTAINS), CallInput("6100678")))
        assertTrue(RuleEngine.matches(NumberRule(value = "00", match = Match.SUFFIX), CallInput("6123400")))
        assertFalse(RuleEngine.matches(NumberRule(value = "00", match = Match.SUFFIX), CallInput("612340")))
    }
    @Test fun editingRuleDoesNotChangeDecisionPriority() {
        val first = NumberRule(value = "612", match = Match.PREFIX, action = Action.SILENCE)
        val second = NumberRule(value = "6123", match = Match.PREFIX, action = Action.REJECT)
        val before = basic.copy(rules = listOf(first, second))
        val after = before.withRule(first.copy(note = "Nota actualizada"))
        assertEquals(first.id, after.rules.first().id)
        assertEquals(Action.SILENCE, decide(after).action)
        assertEquals(2, after.rules.size)
    }
    @Test fun exactDoesNotMatchSuffix() { assertFalse(RuleEngine.matches(NumberRule(value = "345678"), unknown)) }
    @Test fun emptyPatternDoesNotMatch() { assertFalse(RuleEngine.matches(NumberRule(value = "+ --", match = Match.PREFIX), unknown)) }
    @Test fun privateCannotMatchNumberWhitelist() { assertFalse(RuleEngine.matches(NumberRule(value = "612", match = Match.PREFIX, allow = true), unknown.copy(identity = Identity.PRIVATE))) }
    @Test fun perRuleActionOverridesProfile() { assertEquals(Action.SILENCE, decide(basic.copy(rules = listOf(NumberRule(value = unknown.number, action = Action.SILENCE)))).action) }
    @Test fun profileIgnoreAction() { assertEquals(Action.IGNORE, decide(profile(basic.profile.copy(action = Action.IGNORE))).action) }
    @Test fun voipBlocksOnlyWhenEnabledAndReported() { val s = profile(basic.profile.copy(unknownCalls = false, voipCalls = true)); assertEquals(Action.REJECT, decide(s, unknown.copy(voip = true)).action); assertEquals(Action.ALLOW, decide(s).action) }
    @Test fun repeatedVisibleCallerPasses() { val s = profile(basic.profile.copy(repeated = true)); assertEquals(Action.ALLOW, decide(s, unknown.copy(lastAttempt = monday.toInstant().toEpochMilli() - 60_000)).action) }
    @Test fun expiredOrFutureRepeatDoesNotPass() { val s = profile(basic.profile.copy(repeated = true)); assertEquals(Action.REJECT, decide(s, unknown.copy(lastAttempt = monday.toInstant().toEpochMilli() - 181_000)).action); assertEquals(Action.REJECT, decide(s, unknown.copy(lastAttempt = monday.toInstant().toEpochMilli() + 1000)).action) }
    @Test fun privateRepeatDoesNotPass() { assertEquals(Action.REJECT, decide(profile(basic.profile.copy(repeated = true)), CallInput(identity = Identity.PRIVATE, lastAttempt = monday.toInstant().toEpochMilli() - 60_000)).action) }
    @Test fun scheduleWithoutWindowsAllows() { assertEquals(Action.ALLOW, decide(profile(basic.profile.copy(scheduled = true))).action) }
    @Test fun scheduleChecksAllWindows() { val s = profile(basic.profile.copy(scheduled = true, windows = listOf(TimeWindow(days = setOf(1), start = 8 * 60, end = 10 * 60), TimeWindow(days = setOf(1), start = 22 * 60, end = 23 * 60 + 30)))); assertEquals(Action.REJECT, decide(s).action) }
    @Test fun overnightUsesPreviousDay() { val w = TimeWindow(days = setOf(1), start = 22 * 60, end = 8 * 60); assertTrue(w.active(monday)); assertTrue(w.active(monday.plusHours(8))); assertFalse(w.active(monday.plusHours(9))); assertFalse(w.active(monday.minusHours(16))) }
    @Test fun sundayRollsIntoMonday() { val w = TimeWindow(days = setOf(7), start = 22 * 60, end = 8 * 60); assertTrue(w.active(monday.withHour(7))); assertFalse(w.active(monday.withHour(8))) }
    @Test fun equalTimesMeansWholeSelectedDay() { val w = TimeWindow(days = setOf(1), start = 500, end = 500); assertTrue(w.active(monday.withHour(0))); assertTrue(w.active(monday)); assertFalse(w.active(monday.plusDays(1))) }
    @Test fun boundaryAndDisabledWindow() { val w = TimeWindow(days = setOf(1), start = 9 * 60, end = 10 * 60); assertTrue(w.active(monday.withHour(9).withMinute(0))); assertFalse(w.active(monday.withHour(10).withMinute(0))); assertFalse(w.copy(enabled = false).active(monday.withHour(9))) }
    @Test fun daylightSavingUsesLocalClock() { val w = TimeWindow(days = setOf(7), start = 60, end = 180); assertTrue(w.active(ZonedDateTime.parse("2026-10-25T02:30:00+02:00[Europe/Madrid]"))); assertTrue(w.active(ZonedDateTime.parse("2026-10-25T02:30:00+01:00[Europe/Madrid]"))) }
}
