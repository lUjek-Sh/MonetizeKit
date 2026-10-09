package ua.lujek.monetizekit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
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
        StatusCard(title = "Last action", value = state.lastAction)
        StatusCard(title = "Last event", value = state.lastEvent)

        if (state.adsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
                BasicText(
                    text = "Fullscreen controls",
                    style = typography.subtitle.copy(color = colors.title),
                )
                SampleActionButton("Preload fullscreen ads", state.isSdkReady) {
                    onAction(SampleAdsAction.PRELOAD_FULLSCREEN)
                }
                SampleActionButton("Show interstitial", state.isSdkReady) {
                    onAction(SampleAdsAction.SHOW_INTERSTITIAL)
                }
                SampleActionButton("Show rewarded", state.isSdkReady) {
                    onAction(SampleAdsAction.SHOW_REWARDED)
                }
                SampleActionButton("Privacy options", true) {
                    onAction(SampleAdsAction.OPEN_PRIVACY_OPTIONS)
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
                    AdsBanner(
                        placement = DebugSamplePlacements.banner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = "banner_slot" },
                    )
                    AdsNative(
                        placement = DebugSamplePlacements.native,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = "native_slot" },
                    )
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
