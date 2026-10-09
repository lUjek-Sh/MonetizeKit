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
