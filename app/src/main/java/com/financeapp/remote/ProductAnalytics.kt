package com.financeapp.remote

import android.content.Context
import android.util.Log
import com.financeapp.BuildConfig
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.android.replay.PostHogSessionReplayConfig

/**
 * Product analytics through PostHog (EU cloud).
 *
 * Only the named events [Telemetry] already tracks, plus PostHog's app
 * opened / installed / updated events. No autocapture, no screen names, no
 * surveys. Session replay is on but fully masked ([replayConfig]): every
 * text, text field and image is a grey box, logcat is not sent, so a
 * recording shows only layout and taps. Never SMS text, amounts, balances, accounts,
 * names, emails or phones: properties go through [sanitize] first. A phone
 * is known only by the same random install ID as [Telemetry] and Sentry.
 * Off in debug builds and when `posthog.apiKey` is not set in local.properties.
 */
object ProductAnalytics {

    const val HOST = "https://eu.i.posthog.com"

    /** Free-text properties that can carry pieces of an SMS. */
    private val TEXT_KEYS = setOf("error", "summary")

    private const val TAG = "KashAnalytics"

    @Volatile private var started = false

    fun init(context: Context) {
        val key = BuildConfig.POSTHOG_API_KEY
        if (BuildConfig.DEBUG || key.isBlank()) return
        runCatching {
            val config = PostHogAndroidConfig(apiKey = key, host = HOST).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = false
                captureDeepLinks = false
                sessionReplay = true
                sessionReplayConfig = replayConfig()
                preloadFeatureFlags = false
                sendFeatureFlagEvent = false
                personProfiles = PersonProfiles.IDENTIFIED_ONLY
                debug = BuildConfig.POSTHOG_DEBUG
                // Kash makes only a few events per visit; PostHog's default
                // waits for 20, so they sat on the phone. Send each one now.
                flushAt = 1
            }
            PostHogAndroid.setup(context.applicationContext, config)
            PostHog.identify(Telemetry.installId(context))
            started = true
        }.onFailure { Log.w(TAG, "PostHog setup failed: ${it::class.java.simpleName}") }
    }

    /**
     * Fully masked replay. Kash screens are Compose, which PostHog records in
     * screenshot mode; with these masks every Text, TextField and Image is
     * painted over before the frame leaves the phone. Logcat stays home.
     * `ProductAnalyticsTest` keeps these switches from being turned off.
     */
    internal fun replayConfig() = PostHogSessionReplayConfig(
        maskAllTextInputs = true,
        maskAllImages = true,
        captureLogcat = false,
        screenshot = true,
    )

    fun capture(name: String, props: Map<String, Any?> = emptyMap()) {
        if (!started) return
        runCatching { PostHog.capture(event = name, properties = sanitize(props)) }
    }

    /** Settings → Delete account: forget the install's PostHog identity and queue. */
    fun reset() {
        if (!started) return
        runCatching { PostHog.reset() }
    }

    /**
     * Drops nulls and masks numbers and quoted text in free-text values
     * (the same masking as crash reports), so an error message that quotes
     * an SMS never leaves the phone with an amount or account in it.
     */
    internal fun sanitize(props: Map<String, Any?>): Map<String, Any> =
        props.entries
            .mapNotNull { (k, v) -> v?.let { k to it } }
            .associate { (k, v) ->
                k to if (k in TEXT_KEYS && v is String) CrashReporting.scrubText(v) else v
            }
}
