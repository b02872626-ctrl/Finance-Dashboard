package com.financeapp.data.repository

import android.content.Context
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.model.ErrorLogEntity
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
    }

    companion object {
        fun fromContext(context: Context): ErrorLogRepository =
            ErrorLogRepository(AppDatabase.getInstance(context.applicationContext))
    }
}

