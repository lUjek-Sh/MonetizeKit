package ua.lujek.monetizekit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticAppOpenDecisionTest {
    @Test
    fun `cold start waits only while a splash gate is attached`() {
        assertEquals(
            AutomaticAppOpenDecision.WAIT_FOR_PRELOAD,
            automaticAppOpenDecision(
                trigger = AutomaticAppOpenTrigger.COLD_START,
                coldStartSplashScreenBound = true,
                activityIsResumed = true,
                hasPreloadedCreative = false,
            ),
        )
        assertEquals(
            AutomaticAppOpenDecision.CANCEL_COLD_START,
            automaticAppOpenDecision(
                trigger = AutomaticAppOpenTrigger.COLD_START,
                coldStartSplashScreenBound = false,
                activityIsResumed = false,
                hasPreloadedCreative = false,
            ),
        )
    }

    @Test
    fun `cold start can show a preloaded creative while splash hides content`() {
        assertEquals(
            AutomaticAppOpenDecision.SHOW,
            automaticAppOpenDecision(
                trigger = AutomaticAppOpenTrigger.COLD_START,
                coldStartSplashScreenBound = true,
                activityIsResumed = true,
                hasPreloadedCreative = true,
            ),
        )
    }

    @Test
    fun `foreground return never waits for an ad after resume`() {
        assertEquals(
            AutomaticAppOpenDecision.SKIP_FOREGROUND_RETURN,
            automaticAppOpenDecision(
                trigger = AutomaticAppOpenTrigger.FOREGROUND_RETURN,
                coldStartSplashScreenBound = false,
                activityIsResumed = false,
                hasPreloadedCreative = false,
            ),
        )
        assertEquals(
            AutomaticAppOpenDecision.SKIP_FOREGROUND_RETURN,
            automaticAppOpenDecision(
                trigger = AutomaticAppOpenTrigger.FOREGROUND_RETURN,
                coldStartSplashScreenBound = false,
                activityIsResumed = true,
                hasPreloadedCreative = true,
            ),
        )
    }

    @Test
    fun `foreground return shows only a creative cached before resume`() {
        assertEquals(
            AutomaticAppOpenDecision.SHOW,
            automaticAppOpenDecision(
                trigger = AutomaticAppOpenTrigger.FOREGROUND_RETURN,
                coldStartSplashScreenBound = false,
                activityIsResumed = false,
                hasPreloadedCreative = true,
            ),
        )
    }

    @Test
    fun `released cold-start gate rejects late preload callbacks`() {
        assertEquals(
            true,
            canContinueAutomaticAppOpen(
                trigger = AutomaticAppOpenTrigger.COLD_START,
                coldStartGateState = AppOpenColdStartGateState.WAITING_FOR_AD,
            ),
        )
        assertEquals(
            false,
            canContinueAutomaticAppOpen(
                trigger = AutomaticAppOpenTrigger.COLD_START,
                coldStartGateState = AppOpenColdStartGateState.RELEASED,
            ),
        )
        assertEquals(
            false,
            canContinueAutomaticAppOpen(
                trigger = AutomaticAppOpenTrigger.COLD_START,
                coldStartGateState = AppOpenColdStartGateState.SHOWING_AD,
            ),
        )
    }

    @Test
    fun `cold-start timeout starts only while splash gate waits for an ad`() {
        assertEquals(true, shouldStartColdStartTimeout(AppOpenColdStartGateState.WAITING_FOR_AD))
        assertEquals(false, shouldStartColdStartTimeout(AppOpenColdStartGateState.NOT_REQUIRED))
        assertEquals(false, shouldStartColdStartTimeout(AppOpenColdStartGateState.SHOWING_AD))
        assertEquals(false, shouldStartColdStartTimeout(AppOpenColdStartGateState.RELEASED))
    }
}
