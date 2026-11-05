package com.financeapp.remote

import android.content.Context
import com.financeapp.BuildConfig
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

/**
 * Crash reports through Sentry.
 *
 * Only crashes and errors, never SMS text, amounts, balances, account numbers,
 * names or phone numbers. Sentry identifies a phone by the same anonymous
 * install ID as [Telemetry]. Off in debug builds and when `sentry.dsn` is not
 * set in local.properties.
 */
object CrashReporting {

    fun init(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (BuildConfig.DEBUG || dsn.isBlank()) return
        val installId = Telemetry.installId(context)

        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.release = "kash@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.environment = "production"
            options.isSendDefaultPii = false
            options.tracesSampleRate = null
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.maxBreadcrumbs = 0
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
        }
        io.sentry.Sentry.setUser(User().apply { id = installId })
    }

    /** Strips anything that could be money data before an event leaves the phone. */
    internal fun scrub(event: SentryEvent): SentryEvent {
        event.breadcrumbs = null
        event.extras = null
        event.request = null
        event.serverName = null
        event.user = event.user?.let { u -> User().apply { id = u.id } }
        event.message?.let { m ->
            m.formatted = m.formatted?.let(::scrubText)
            m.message = m.message?.let(::scrubText)
            m.params = null
        }
        event.exceptions?.forEach { e -> e.value = e.value?.let(::scrubText) }
        return event
    }

    /**
     * Exception messages can carry pieces of an SMS (e.g. `For input string:
     * "12,500.00"`). Masks every number and anything after a quote, so the
     * message keeps its shape but no amount, account or phone survives.
     */
    internal fun scrubText(text: String): String =
        text
            .replace(Regex("""(["'])[^"']*\1"""), "$1…$1")
            .replace(Regex("""\d[\d,.*\s-]*\d|\d"""), "#")
}
