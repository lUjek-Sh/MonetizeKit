package ua.lujek.monetizekit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ua.lujek.monetizekit.compose.AdsBanner
import ua.lujek.monetizekit.compose.AdsNative

@Composable
fun SampleScreen(
    state: SampleAdsUiState,
    onAction: (SampleAdsAction) -> Unit,
    showInlineAds: Boolean = true,
) {
    val colors = LocalSampleColors.current
    val typography = LocalSampleTypography.current
    val spacing = LocalSampleSpacing.current
    val shapes = LocalSampleShapes.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
            BasicText(
                text = "MonetizeKit debug sample",
                style = typography.title.copy(color = colors.title),
                modifier = Modifier.semantics { testTag = "sample_title" },
            )
            BasicText(
                text = "The debug build uses official Google test IDs. No production ad request is made by this sample.",
                style = typography.body.copy(color = colors.body),
            )
        }

        StatusCard(title = "SDK state", value = state.sdkStatus)
        StatusCard(title = "Privacy state", value = state.privacyStatus)
        StatusCard(
            title = "Selected privacy profile",
            value = "${state.privacyProfile.label}: ${state.privacyProfile.description}",
        )
        StatusCard(title = "Network test setup", value = state.networkTestSetup)
        StatusCard(title = "Last action", value = state.lastAction)
        StatusCard(title = "Last event", value = state.lastEvent)

            if (state.adsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                BasicText(
                    text = "Inspector and reload",
                    style = typography.subtitle.copy(color = colors.title),
                )
                BasicText(
                    text = "Ad Inspector diagnoses Google Mobile Ads and AdMob Mediation. Direct Meta and Unity requests are tested below.",
                    style = typography.caption.copy(color = colors.body),
                )
                SampleActionButton("Open Ad Inspector", state.isSdkReady) {
                    onAction(SampleAdsAction.OpenAdInspector)
                }
                SampleActionButton("Reload configured inline ads", state.isSdkReady) {
                    onAction(SampleAdsAction.ReloadInlineAds)
                }
                SampleActionButton("Preload every configured fullscreen ad", state.isSdkReady) {
                    onAction(SampleAdsAction.PreloadAllFullscreen)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                BasicText(
                    text = "AdMob fullscreen controls",
                    style = typography.subtitle.copy(color = colors.title),
                )
                SampleActionButton("Show interstitial", state.isSdkReady) {
                    onAction(SampleAdsAction.ShowAdMobInterstitial)
                }
                SampleActionButton("Show rewarded", state.isSdkReady) {
                    onAction(SampleAdsAction.ShowAdMobRewarded)
                }
                SampleActionButton("Show rewarded interstitial", state.isSdkReady) {
                    onAction(SampleAdsAction.ShowAdMobRewardedInterstitial)
                }
                SampleActionButton("Show App Open", state.isSdkReady) {
                    onAction(SampleAdsAction.ShowAdMobAppOpen)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                BasicText(
                    text = "Direct network fullscreen controls",
                    style = typography.subtitle.copy(color = colors.title),
                )
                SampleActionButton(
                    label = "Show Meta interstitial",
                    enabled = state.isSdkReady && SampleDebugConfiguration.meta?.interstitial != null,
                ) {
                    onAction(SampleAdsAction.ShowMetaInterstitial)
                }
                SampleActionButton(
                    label = "Show Meta rewarded",
                    enabled = state.isSdkReady && SampleDebugConfiguration.meta?.rewarded != null,
                ) {
                    onAction(SampleAdsAction.ShowMetaRewarded)
                }
                SampleActionButton(
                    label = "Show Unity interstitial",
                    enabled = state.isSdkReady && SampleDebugConfiguration.unity?.interstitial != null,
                ) {
                    onAction(SampleAdsAction.ShowUnityInterstitial)
                }
                SampleActionButton(
                    label = "Show Unity rewarded",
                    enabled = state.isSdkReady && SampleDebugConfiguration.unity?.rewarded != null,
                ) {
                    onAction(SampleAdsAction.ShowUnityRewarded)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                BasicText(
                    text = "Privacy flow controls",
                    style = typography.subtitle.copy(color = colors.title),
                )
                SampleActionButton("Run configured consent flow", true) {
                    onAction(SampleAdsAction.GatherConsent)
                }
                SampleActionButton("Privacy options", true) {
                    onAction(SampleAdsAction.OpenPrivacyOptions)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                BasicText(
                    text = "Privacy test profile (applies after restart)",
                    style = typography.subtitle.copy(color = colors.title),
                )
                BasicText(
                    text = "To force UMP EEA or US GPP: run Standard UMP once, copy the " +
                        "addTestDeviceHashedId(...) value from Logcat, paste it here, select a profile, " +
                        "then reset and restart. Configure a published UMP message for the App ID in " +
                        "sample_debug_ids.xml.",
                    style = typography.caption.copy(color = colors.body),
                )
                BasicText(
                    text = "UMP test-device hash",
                    style = typography.caption.copy(color = colors.body),
                )
                BasicTextField(
                    value = state.umpTestDeviceHash,
                    onValueChange = { value ->
                        onAction(SampleAdsAction.UpdateUmpTestDeviceHash(value))
                    },
                    singleLine = true,
                    textStyle = typography.body.copy(color = colors.title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "ump_test_device_hash" }
                        .clip(shapes.button)
                        .background(colors.canvas)
                        .border(width = spacing.border, color = colors.outline, shape = shapes.button)
                        .padding(horizontal = spacing.card, vertical = spacing.buttonVertical),
                )
                SamplePrivacyProfile.entries.forEach { profile ->
                    SampleActionButton(
                        label = if (profile == state.privacyProfile) {
                            "Selected: ${profile.label}"
                        } else {
                            "Use ${profile.label}"
                        },
                        enabled = profile != state.privacyProfile,
                    ) {
                        onAction(SampleAdsAction.SelectPrivacyProfile(profile))
                    }
                }
                SampleActionButton("Reset UMP, apply profile, and restart debug app", true) {
                    onAction(SampleAdsAction.ResetUmpAndRestartWithPrivacyProfile)
                }
            }

            if (showInlineAds) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                    BasicText(
                        text = "Reserved inline placements",
                        style = typography.subtitle.copy(color = colors.title),
                    )
                    BasicText(
                        text = "The blank areas keep their height while banner and native ads load or fail.",
                        style = typography.caption.copy(color = colors.body),
                    )
                    key(state.inlineReloadKey) {
                        InlineAdTestSlots()
                    }
                }
            } else {
                StatusCard(
                    title = "Release variant",
                    value = "Ads are intentionally disabled. Install the debug variant to exercise official test ads.",
                )
            }
        }
    }

    SampleExternalCmpDialog()
}
}

@Composable
private fun InlineAdTestSlots() {
    val colors = LocalSampleColors.current
    val typography = LocalSampleTypography.current

    BasicText("AdMob native", style = typography.caption.copy(color = colors.body))
    AdsNative(
        placement = DebugSamplePlacements.adMobNative,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "native_slot" },
    )
    BasicText("AdMob banner", style = typography.caption.copy(color = colors.body))
    AdsBanner(
        placement = DebugSamplePlacements.adMobBanner,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "banner_slot" },
    )
    SampleDebugConfiguration.meta?.banner?.let {
        BasicText("Meta banner", style = typography.caption.copy(color = colors.body))
        AdsBanner(
            placement = DebugSamplePlacements.metaBanner,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SampleDebugConfiguration.meta?.native?.let {
        BasicText("Meta native", style = typography.caption.copy(color = colors.body))
        AdsNative(
            placement = DebugSamplePlacements.metaNative,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SampleDebugConfiguration.unity?.banner?.let {
        BasicText("Unity banner", style = typography.caption.copy(color = colors.body))
        AdsBanner(
            placement = DebugSamplePlacements.unityBanner,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SampleExternalCmpDialog() {
    val request = SampleExternalCmpDialogController.uiState.collectAsState().value ?: return
    val colors = LocalSampleColors.current
    val typography = LocalSampleTypography.current
    val spacing = LocalSampleSpacing.current
    val shapes = LocalSampleShapes.current
    val title = when (request.request) {
        SampleExternalCmpRequest.CONSENT -> "Debug external CMP"
        SampleExternalCmpRequest.PRIVACY_OPTIONS -> "Debug external CMP privacy options"
    }
    val description = when (request.request) {
        SampleExternalCmpRequest.CONSENT -> {
            "This validates MonetizeKit's external consent-provider contract. It is not a certified CMP."
        }
        SampleExternalCmpRequest.PRIVACY_OPTIONS -> {
            "Change the debug provider decision. Production apps must use their certified CMP here."
        }
    }

    Dialog(onDismissRequest = { SampleExternalCmpDialogController.complete(allowed = false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shapes.card)
                .background(colors.panel)
                .border(width = spacing.border, color = colors.outline, shape = shapes.card)
                .padding(spacing.card),
            verticalArrangement = Arrangement.spacedBy(spacing.item),
        ) {
            BasicText(title, style = typography.subtitle.copy(color = colors.title))
            BasicText(description, style = typography.body.copy(color = colors.body))
            SampleActionButton("Allow ads", true) {
                SampleExternalCmpDialogController.complete(allowed = true)
            }
            SampleActionButton("Decline ads", true) {
                SampleExternalCmpDialogController.complete(allowed = false)
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    val colors = LocalSampleColors.current
    val typography = LocalSampleTypography.current
    val spacing = LocalSampleSpacing.current
    val shapes = LocalSampleShapes.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.card)
            .background(colors.panel)
            .border(width = spacing.border, color = colors.outline, shape = shapes.card)
            .padding(spacing.card),
        verticalArrangement = Arrangement.spacedBy(spacing.item / 2),
    ) {
        BasicText(text = title, style = typography.caption.copy(color = colors.body))
        BasicText(text = value, style = typography.body.copy(color = colors.title))
    }
}

@Composable
private fun SampleActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalSampleColors.current
    val typography = LocalSampleTypography.current
    val spacing = LocalSampleSpacing.current
    val shapes = LocalSampleShapes.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.button)
            .background(if (enabled) colors.primary else colors.disabledControl)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = spacing.buttonVertical, horizontal = spacing.card),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = typography.button.copy(color = colors.primaryText, textAlign = TextAlign.Center),
        )
    }
}
