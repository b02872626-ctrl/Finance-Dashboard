package com.financeapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for incoming SMS and immediately runs them through the ingest pipeline.
 * Uses a SupervisorJob scope so one failure doesn't cancel other ingestions.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Instead of manually parsing messy raw network PDUs (which can cause
        // string/timestamp variations vs the Android unified content provider, 
        // leading to duplicate transactions), we simply trigger the ingest 
        // service which safely reads the synchronized database.
        SmsIngestService.start(context)
    }
}
