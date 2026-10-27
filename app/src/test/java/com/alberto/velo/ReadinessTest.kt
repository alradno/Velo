package com.alberto.velo

import org.junit.Assert.*
import org.junit.Test

class ReadinessTest {
    private fun status(filter: Boolean=true,contacts: Boolean=true,notifications: Boolean=true,wanted: Boolean=true,
        battery: Boolean=true,restricted: Boolean=false,unused: CheckState=CheckState.READY,oppo: Boolean=false,reviewed: Boolean=false)=
        ReadinessPolicy.checks(filter,true,contacts,notifications,wanted,battery,restricted,unused,oppo,reviewed)
    @Test fun allVerifiedDoesNotWarn() {assertTrue(status().pending.isEmpty());assertTrue(status().loaded);assertNull(status().next(emptySet()))}
    @Test fun wizardRequestsRoleBeforeRuntimePermissionsAndBattery() {
        val state=status(filter=false,contacts=false,notifications=false,battery=false)
        assertEquals(SetupStep.FILTER,state.next(emptySet()))
        assertEquals(SetupStep.CONTACTS,state.next(setOf(SetupStep.FILTER)))
        assertEquals(SetupStep.NOTIFICATIONS,state.next(setOf(SetupStep.FILTER,SetupStep.CONTACTS)))
        assertEquals(SetupStep.BATTERY,state.next(setOf(SetupStep.FILTER,SetupStep.CONTACTS,SetupStep.NOTIFICATIONS)))
    }
    @Test fun decliningDoesNotLoopOrPretendPermissionWasGranted() {
        val state=status(filter=false,contacts=false)
        assertNull(state.next(setOf(SetupStep.FILTER,SetupStep.CONTACTS)))
        assertEquals(2,state.pending.size)
    }
    @Test fun explicitWizardRetryCanRequestPreviouslyDeclinedStep() {val state=status(filter=false);assertNull(state.next(setOf(SetupStep.FILTER)));assertEquals(SetupStep.FILTER,state.next(emptySet()))}
    @Test fun optOutOfVeloNotificationsIsRespected() {val state=status(notifications=false,wanted=false);assertTrue(state.pending.isEmpty());assertEquals(CheckState.NOT_NEEDED,state.checks.first{it.step==SetupStep.NOTIFICATIONS}.state)}
    @Test fun blockedNotificationsOrChannelNeedsAttention() {assertEquals(SetupStep.NOTIFICATIONS,status(notifications=false).pending.single().step)}
    @Test fun batteryAndBackgroundAreIndependentChecks() {assertEquals(SetupStep.BACKGROUND,status(battery=true,restricted=true).pending.single().step);assertEquals(SetupStep.BATTERY,status(battery=false).pending.single().step)}
    @Test fun unusedAppRestrictionsUnavailableDoesNotWarn() {assertTrue(status(unused=CheckState.NOT_NEEDED).pending.isEmpty())}
    @Test fun failedUnusedCheckIsNotTreatedAsSuccess() {val state=status(unused=CheckState.UNKNOWN);assertEquals(CheckState.UNKNOWN,state.pending.single().state);assertNull(state.next(emptySet()))}
    @Test fun manufacturerReviewIsNeverAutoRequestedOrAutoVerified() {val state=status(oppo=true);assertEquals(CheckState.MANUAL,state.pending.single().state);assertNull(state.next(emptySet()));assertEquals(CheckState.NOT_NEEDED,status(oppo=true,reviewed=true).checks.last().state)}
    @Test fun changedConfigurationChangesWarningFingerprint() {assertNotEquals(status(filter=false).fingerprint,status(contacts=false).fingerprint);assertNotEquals(status().fingerprint,status(unused=CheckState.UNKNOWN).fingerprint)}
    @Test fun nonColorOsDevicesDoNotShowOppoInstructions() {assertFalse(status().checks.any{it.step==SetupStep.OPPO})}
}
