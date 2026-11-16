package com.financeapp.ui.kash

import androidx.compose.ui.Modifier
import com.posthog.android.replay.PostHogMaskModifier

/**
 * Lets one fixed label show in PostHog session replay. Everything else stays
 * masked ([com.financeapp.remote.ProductAnalytics.replayConfig]).
 *
 * Only on a single Text whose words Kash writes itself: a screen title, a
 * button, a filter like "This month". Never on text that can hold money,
 * a name, an account, a category or nickname the user typed, or SMS text.
 * Never on a Row, Column or Box: the unmask covers everything inside it.
 *
 * `ProductAnalyticsTest` fails if this is used outside the approved files.
 */
fun Modifier.replayLabel(show: Boolean = true): Modifier =
    if (show) with(PostHogMaskModifier) { this@replayLabel.postHogUnmask() } else this
