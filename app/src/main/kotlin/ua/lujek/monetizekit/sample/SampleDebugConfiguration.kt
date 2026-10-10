package ua.lujek.monetizekit.sample

import android.content.Context
import androidx.core.content.edit
import ua.lujek.monetizekit.core.GdprDebugGeography

internal data class MetaDebugPlacementIds(
    val banner: String?,
    val native: String?,
    val interstitial: String?,
    val rewarded: String?,
    val testDeviceHash: String?,
) {
    val isConfigured: Boolean
        get() = listOf(banner, native, interstitial, rewarded).any { it != null }
}

internal data class UnityDebugPlacementIds(
    val gameId: String?,
    val banner: String?,
    val interstitial: String?,
    val rewarded: String?,
) {
    val isConfigured: Boolean
        get() = gameId != null && listOf(banner, interstitial, rewarded).any { it != null }
}

/** Debug-only configuration populated by the constants in the debug ID objects. */
internal object SampleDebugConfiguration {
    val meta = MetaDebugPlacementIds(
        banner = DebugMetaIds.BANNER.nullIfBlank(),
        native = DebugMetaIds.NATIVE.nullIfBlank(),
        interstitial = DebugMetaIds.INTERSTITIAL.nullIfBlank(),
        rewarded = DebugMetaIds.REWARDED.nullIfBlank(),
        testDeviceHash = DebugMetaIds.TEST_DEVICE_HASH.nullIfBlank(),
    ).takeIf(MetaDebugPlacementIds::isConfigured)

    val unity = UnityDebugPlacementIds(
        gameId = DebugUnityIds.GAME_ID.nullIfBlank(),
        banner = DebugUnityIds.BANNER.nullIfBlank(),
        interstitial = DebugUnityIds.INTERSTITIAL.nullIfBlank(),
        rewarded = DebugUnityIds.REWARDED.nullIfBlank(),
    ).takeIf(UnityDebugPlacementIds::isConfigured)

    val networkSummary: String = buildList {
        add("AdMob demo")
        if (meta != null) add("Meta direct") else add("Meta not configured")
        if (unity != null) add("Unity direct") else add("Unity not configured")
    }.joinToString()
}

/** Debug-only storage for the UMP hash shown by Logcat after `requestConsentInfoUpdate()`. */
internal object SamplePrivacyTestSettingsStore {
    private const val preferencesName = "monetizekit_sample_debug"
    private const val umpTestDeviceHashKey = "ump_test_device_hash"

    @Volatile
    private var currentUmpTestDeviceHash: String? = DebugUmpIds.TEST_DEVICE_HASH.nullIfBlank()

    fun readUmpTestDeviceHash(context: Context): String? = context
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        .getString(umpTestDeviceHashKey, null)
        ?.nullIfBlank()
        ?.also { currentUmpTestDeviceHash = it }
        ?: currentUmpTestDeviceHash

    fun currentUmpTestDeviceHash(): String? = currentUmpTestDeviceHash

    fun saveUmpTestDeviceHash(context: Context, value: String): String? {
        val normalizedValue = value.nullIfBlank()
        currentUmpTestDeviceHash = normalizedValue
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit {
            if (normalizedValue == null) remove(umpTestDeviceHashKey)
            else putString(umpTestDeviceHashKey, normalizedValue)
        }
        return normalizedValue
    }
}

enum class SamplePrivacyProfile(
    val label: String,
    val description: String,
    val umpDebugGeography: GdprDebugGeography? = null,
) {
    UMP_DEFAULT(
        label = "Standard UMP",
        description = "Uses the published UMP message for the device's actual geography.",
    ),
    UMP_EEA(
        label = "UMP EEA",
        description = "Forces the UMP EEA test geography for a registered UMP test device.",
        umpDebugGeography = GdprDebugGeography.EEA,
    ),
    COPPA(
        label = "COPPA",
        description = "Sends Google child-directed treatment before requests.",
    ),
    TFUA(
        label = "TFUA",
        description = "Tags the user as under the age of consent before UMP and ad requests.",
    ),
    US_RESTRICTED_DATA_PROCESSING(
        label = "US restricted data processing",
        description = "Writes the Google restricted-data-processing signal before requests.",
    ),
    US_GLOBAL_PRIVACY_PLATFORM(
        label = "US GPP",
        description = "Expects UMP to provide the required GPP signals before requests.",
        umpDebugGeography = GdprDebugGeography.REGULATED_US_STATE,
    ),
    EXTERNAL_CMP(
        label = "External CMP contract",
        description = "Uses the debug external-provider flow; it is not a certified production CMP.",
    ),
}

internal object SamplePrivacyProfileStore {
    private const val preferencesName = "monetizekit_sample_debug"
    private const val profileKey = "privacy_profile"
    @Volatile
    private var currentProfile: SamplePrivacyProfile = SamplePrivacyProfile.UMP_DEFAULT

    fun read(context: Context): SamplePrivacyProfile {
        val savedName = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(profileKey, null)
        return (
            SamplePrivacyProfile.entries.firstOrNull { it.name == savedName }
                ?: SamplePrivacyProfile.UMP_DEFAULT
            ).also { currentProfile = it }
    }

    fun current(): SamplePrivacyProfile = currentProfile

    fun save(context: Context, profile: SamplePrivacyProfile) {
        currentProfile = profile
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit {
            putString(profileKey, profile.name)
        }
    }
}

private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotEmpty)
