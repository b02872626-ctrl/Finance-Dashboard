package com.financeapp.remote

import android.content.Context
import com.financeapp.BuildConfig
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * Product analytics through PostHog (EU cloud).
 *
 * Only the named events [Telemetry] already tracks, plus PostHog's app
 * opened / installed / updated events. No autocapture, no screen names, no
 * session replay, no surveys. Never SMS text, amounts, balances, accounts,
 * names, emails or phones: properties go through [sanitize] first. A phone
 * is known only by the same random install ID as [Telemetry] and Sentry.
 * Off in debug builds and when `posthog.apiKey` is not set in local.properties.
 */
object ProductAnalytics {

    const val HOST = "https://eu.i.posthog.com"

    /** Free-text properties that can carry pieces of an SMS. */
    private val TEXT_KEYS = setOf("error", "summary")

    @Volatile private var started = false

    fun init(context: Context) {
        val key = BuildConfig.POSTHOG_API_KEY
        if (BuildConfig.DEBUG || key.isBlank()) return
        runCatching {
            val config = PostHogAndroidConfig(apiKey = key, host = HOST).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = false
                captureDeepLinks = false
                sessionReplay = false
                preloadFeatureFlags = false
                sendFeatureFlagEvent = false
                personProfiles = PersonProfiles.IDENTIFIED_ONLY
            }
            PostHogAndroid.setup(context.applicationContext, config)
            PostHog.identify(Telemetry.installId(context))
            started = true
        }
    }

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
