package com.alberto.velo

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class OptimizedRulesTest {
    private fun state(rules: List<NumberRule>) = State(settings=Settings(enabled=true),profiles=listOf(Profile(id="personal",privateCalls=false,unknownCalls=false,unavailableCalls=false)),rules=rules)
    @Test fun compiledIndexKeepsReferenceMatchingAndOrdering() {
        val random = Random(771)
        repeat(40) {
            val rules = (0..120).map { i -> val value=if(i%3==0) "+33612${random.nextInt(100).toString().padStart(6,'0')}" else listOf("00","12","6","0012","+33")[random.nextInt(5)]; NumberRule(value=value,match=Match.values()[random.nextInt(4)],allow=random.nextInt(5)==0,action=Action.values().filter { it!=Action.ALLOW }[random.nextInt(3)]) }
            val s = state(rules)
            repeat(30) {
                val input=CallInput(number="+33612${random.nextInt(100).toString().padStart(6,'0')}")
                val expected = rules.firstOrNull { it.allow && RuleEngine.matches(it,input) } ?: rules.firstOrNull { !it.allow && RuleEngine.matches(it,input) }
                val result=RuleEngine.decide(s,input)
                assertEquals(expected?.id,result.ruleId)
                assertEquals(if(expected==null||expected.allow) Action.ALLOW else expected.action,result.action)
            }
        }
    }
    @Test fun prefix00DoesNotMatchEveryNumber() { val s=state(listOf(NumberRule(value="00",match=Match.PREFIX))); assertEquals(Action.ALLOW,RuleEngine.decide(s,CallInput(number="+33612345678")).action); assertEquals(Action.REJECT,RuleEngine.decide(s,CallInput(number="0033612345678")).action) }
    @Test fun disabledOrChangedConfigurationInvalidatesCompiledRules() { val rule=NumberRule(value="123"); val s=state(listOf(rule)); assertEquals(Action.REJECT,RuleEngine.decide(s,CallInput(number="123")).action); assertEquals(Action.ALLOW,RuleEngine.decide(s.copy(rules=listOf(rule.copy(enabled=false))),CallInput(number="123")).action) }
    @Test fun contactsAreRequestedOnlyWhenTheyCanChangeTheDecision() {
        val s=State(settings=Settings(enabled=true),rules=listOf(NumberRule(value="123")))
        assertFalse(RuleEngine.decide(s,CallInput(number="123")).needsContacts)
        assertTrue(RuleEngine.decide(s,CallInput(number="456")).needsContacts)
        assertFalse(RuleEngine.decide(s,CallInput(identity=Identity.PRIVATE)).needsContacts)
        assertFalse(RuleEngine.decide(s.copy(settings=Settings()),CallInput(number="456")).needsContacts)
    }
    @Test fun earlyPatternsRetainPriorityOverLaterExactRules() { val first=NumberRule(value="12",match=Match.PREFIX,action=Action.SILENCE); val exact=NumberRule(value="123",action=Action.REJECT); assertEquals(first.id,RuleEngine.decide(state(listOf(first,exact)),CallInput(number="123")).ruleId); assertEquals(exact.id,RuleEngine.decide(state(listOf(exact,first)),CallInput(number="123")).ruleId) }
}
