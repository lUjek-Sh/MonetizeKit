package ua.lujek.monetizekit.core.test

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import ua.lujek.monetizekit.core.*

@RunWith(AndroidJUnit4::class)
class GdprConsentManagerInstrumentedTest {
    @Test
    fun external_consent_provider_owns_gather_and_privacy_options_flow() {
        val provider = RecordingConsentProvider()
        val configuration = GdprConfigurationBuilder().apply {
            externalConsent(provider)
        }.build()

        ActivityScenario.launch(InstrumentedTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager = GdprConsentManager(
                    context = activity,
                    configuration = configuration,
                    umpProvider = FailingConsentProvider,
                )

                val gathered = runBlocking { manager.gather(activity) }
                val privacyOptions = runBlocking { manager.showPrivacyOptions(activity) }

                assertEquals(provider.gatheredSnapshot, gathered)
                assertEquals(provider.privacyOptionsSnapshot, privacyOptions)
                assertEquals(1, provider.gatherCalls)
                assertEquals(1, provider.privacyOptionsCalls)
                assertSame(activity, provider.lastActivity)
            }
        }
    }

    @Test
    fun ump_provider_receives_debug_geography_and_owns_privacy_options_and_reset() {
        val configuration = GdprConfigurationBuilder().apply {
            debug(enabled = true) {
                geography = GdprDebugGeography.EEA
                testDeviceHashedId("UMP_TEST_DEVICE")
            }
            ageRestrictedTreatment(underAgeOfConsent = true)
        }.build()
        val gateway = RecordingUmpGateway()

        ActivityScenario.launch(InstrumentedTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager = GdprConsentManager(
                    context = activity,
                    configuration = configuration,
                    umpProvider = UmpPrivacyConsentProvider(activity, configuration, gateway),
                )

                assertEquals(gateway.gatheredSnapshot, runBlocking { manager.gather(activity) })
                assertEquals(gateway.privacyOptionsSnapshot, runBlocking { manager.showPrivacyOptions(activity) })
                manager.resetUmpConsentForTesting()

                assertSame(activity, gateway.lastActivity)
                assertSame(configuration, gateway.lastConfiguration)
                assertEquals(1, gateway.gatherCalls)
                assertEquals(1, gateway.privacyOptionsCalls)
                assertEquals(1, gateway.resetCalls)
            }
        }
    }
}

private class RecordingConsentProvider : PrivacyConsentProvider {
    val gatheredSnapshot = ConsentSnapshot(
        canRequestAds = true,
        isPrivacyOptionsRequired = true,
    )
    val privacyOptionsSnapshot = ConsentSnapshot(
        canRequestAds = true,
        isPrivacyOptionsRequired = false,
    )
    var gatherCalls = 0
    var privacyOptionsCalls = 0
    var lastActivity: Activity? = null

    override suspend fun gatherConsent(activity: Activity): ConsentSnapshot {
        gatherCalls += 1
        lastActivity = activity
        return gatheredSnapshot
    }

    override suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot {
        privacyOptionsCalls += 1
        lastActivity = activity
        return privacyOptionsSnapshot
    }
}

private object FailingConsentProvider : PrivacyConsentProvider {
    override suspend fun gatherConsent(activity: Activity): ConsentSnapshot =
        error("The UMP provider must not be called when an external provider is configured.")

    override suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot =
        error("The UMP provider must not be called when an external provider is configured.")
}

private class RecordingUmpGateway : UmpConsentGateway {
    val gatheredSnapshot = ConsentSnapshot(
        canRequestAds = true,
        isPrivacyOptionsRequired = true,
    )
    val privacyOptionsSnapshot = ConsentSnapshot(
        canRequestAds = true,
        isPrivacyOptionsRequired = false,
    )
    var gatherCalls = 0
    var privacyOptionsCalls = 0
    var resetCalls = 0
    var lastActivity: Activity? = null
    var lastConfiguration: GdprConfiguration? = null

    override suspend fun gatherConsent(
        activity: Activity,
        configuration: GdprConfiguration,
    ): ConsentSnapshot {
        gatherCalls += 1
        lastActivity = activity
        lastConfiguration = configuration
        return gatheredSnapshot
    }

    override suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot {
        privacyOptionsCalls += 1
        lastActivity = activity
        return privacyOptionsSnapshot
    }

    override fun resetForTesting() {
        resetCalls += 1
    }
}
