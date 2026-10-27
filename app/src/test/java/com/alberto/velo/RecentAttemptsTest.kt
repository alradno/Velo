package com.alberto.velo
import org.junit.Assert.*
import org.junit.Test

class RecentAttemptsTest {
    @Test fun queuedAttemptsAreVisibleBeforeDiskWrite() { val recent=RecentAttempts();recent.record("+33612345678",1000);assertEquals(1000L,recent.latest("+33612345678",2000));recent.record("+33612345678",2001);assertEquals(2001L,recent.latest("+33612345678",2002)) }
    @Test fun cacheIsBoundedAndNeverGroupsPrivateNumbers() { val recent=RecentAttempts(capacity=2);recent.record("",1000);assertNull(recent.latest("",1001));recent.record("a",1000);recent.record("b",1000);recent.record("c",1000);assertNull(recent.latest("a",1001));assertEquals(1000L,recent.latest("c",1001)) }
    @Test fun staleAndFutureTimestampsFallBackToPersistentHistory() { val recent=RecentAttempts(lifetime=100);recent.record("a",1000);assertNull(recent.latest("a",1101));recent.record("a",1000);assertNull(recent.latest("a",999)) }
}
