package com.financeapp.data.repository

import android.content.Context
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.model.ErrorLogEntity
import com.financeapp.remote.Telemetry
import kotlinx.coroutines.flow.Flow

class ErrorLogRepository(private val db: AppDatabase) {
    private val dao = db.errorLogDao()

    fun observeAll(): Flow<List<ErrorLogEntity>> = dao.observeAll()

    suspend fun clear() = dao.clear()

    suspend fun log(summary: String, message: String) {
        dao.insert(
            ErrorLogEntity(
                summary = summary.trim().ifBlank { "Error" },
                message = message
            )
        )
    }

    suspend fun log(summary: String, throwable: Throwable) {
        log(summary, throwable.stackTraceToString())
        // Offline is normal and would bury real failures on the dashboard.
        if (throwable !is java.io.IOException) {
            appContext?.let {
                Telemetry.track(it, Telemetry.ERROR, mapOf(
                    "summary" to summary.take(120),
                    "error" to "${throwable::class.java.simpleName}: ${throwable.message.orEmpty()}".take(300)
                ))
            }
        }
    }

    /** Set by [fromContext]; lets [log] queue a telemetry event. */
    private var appContext: Context? = null

    companion object {
        fun fromContext(context: Context): ErrorLogRepository =
            ErrorLogRepository(AppDatabase.getInstance(context.applicationContext))
                .also { it.appContext = context.applicationContext }
    }
}

