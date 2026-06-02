package com.financeapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Listens for incoming SMS and triggers a background scan of the inbox.
 *
 * We do NOT parse the broadcast PDU directly — Android's unified content
 * provider normalizes encoding + timestamps + multi-part SMS, so re-reading
 * from there is more reliable than handling raw network PDUs.
 *
 * We enqueue a WorkManager job (not [SmsIngestService.start]) because
 * `startForegroundService()` from a background BroadcastReceiver is
 * disallowed on Android 12+ — the call silently fails and ingestion never
 * runs. WorkManager handles background scheduling correctly across all
 * Android versions and is the modern recommended path.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        SmsIngestWorker.enqueue(context)
    }
}
