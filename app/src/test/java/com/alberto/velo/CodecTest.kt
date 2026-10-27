package com.alberto.velo
import org.junit.Assert.*
import org.junit.Test

class CodecTest {
    @Test fun fullRoundTrip() { val p = Profile(id = "personal", name = "Sueño", action = Action.IGNORE, scheduled = true, windows = listOf(TimeWindow(days = setOf(1, 7), start = 1320, end = 480)), repeated = true); val s = State(settings = Settings(enabled = true, region = "FR", dailyBackup = true, fontScale = 1.2f), profiles = listOf(p), folders = listOf(Folder(), Folder("Oficina", false)), rules = listOf(NumberRule(value = "+33123", match = Match.PREFIX, folder = "Oficina", note = "Nota\n\"cita\"", action = Action.SILENCE))); assertEquals(s, Codec.decode(Codec.encode(s))) }
    @Test(expected = IllegalArgumentException::class) fun unknownVersionIsRejected() { Codec.decode(Codec.encode(State()).put("version", 7)) }
    @Test(expected = IllegalArgumentException::class) fun emptyProfilesRejected() { Codec.decode(Codec.encode(State()).put("profiles", org.json.JSONArray())) }
    @Test(expected = IllegalArgumentException::class) fun orphanProfileRejected() { val json = Codec.encode(State()); json.getJSONObject("settings").put("selectedProfile", "missing"); Codec.decode(json) }
    @Test(expected = IllegalArgumentException::class) fun emptyRuleRejected() { Codec.decode(Codec.encode(State(rules = listOf(NumberRule(value = ""))))) }
    @Test(expected = IllegalArgumentException::class) fun orphanFolderRejected() { Codec.decode(Codec.encode(State(rules = listOf(NumberRule(value = "123", folder = "missing"))))) }
    @Test(expected = IllegalArgumentException::class) fun invalidTimeRejected() { Codec.decode(Codec.encode(State(profiles = listOf(Profile(id = "personal", windows = listOf(TimeWindow(start = -1))))))) }
    @Test fun csvRoundTripPreservesCommasQuotesAndNewlines() { val rule = NumberRule(value = "+34612", match = Match.PREFIX, allow = true, folder = "Trabajo, amigos", note = "Texto \"citado\"\nSegunda línea", enabled = false); val imported = NumberCsv.import(NumberCsv.export(listOf(rule))) { it }; assertEquals(rule.copy(id = imported[0].id), imported[0]) }
    @Test fun textNumbersImportedOnePerLine() { val imported = NumberCsv.import("+34612345678\r\n0033612345678\n") { it }; assertEquals(2, imported.size); assertEquals("0033612345678", imported[1].value) }
    @Test fun bomAndSimpleHeaderAccepted() { assertEquals("6123", NumberCsv.import("\uFEFFnumber\n6123") { it }.single().value) }
    @Test(expected = IllegalArgumentException::class) fun invalidCsvDoesNotSilentlyBecomeNumber() { NumberCsv.import("number,match\nabc,EXACT") { it } }
    @Test(expected = IllegalArgumentException::class) fun unclosedQuotesRejected() { NumberCsv.parse("\"number,EXACT") }
    @Test fun eventsRoundTrip() { val e = CallEvent(number = "", identity = Identity.PRIVATE, action = Action.REJECT, reason = "Privado"); assertEquals(e, Codec.events(org.json.JSONArray(listOf(Codec.event(e))).toString()).single()) }
}
