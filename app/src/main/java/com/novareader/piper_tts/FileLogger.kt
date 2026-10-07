package com.novareader.piper_tts

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val TAG = "NovaPiperTTS"
    private const val FILE_NAME = "nova_tts_log.txt"
    private const val PREFS = "nova_piper_logs"
    private const val KEY_WRITE_FILE = "write_file"

    private var appContext: Context? = null
    private var internalLog: File? = null
    private val memoryLog = StringBuilder()
    @Volatile private var writeFile = false

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        writeFile = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WRITE_FILE, false)

        internalLog = File(context.filesDir, FILE_NAME)
        if (writeFile) {
            try {
                if (internalLog?.isFile == true) {
                    memoryLog.append(internalLog?.readText() ?: "")
                }
            } catch (_: Throwable) {}
        } else {
            try { internalLog?.delete() } catch (_: Throwable) {}
        }

        log("=== Логирование запущено ===")
        log("Время: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        log("Запись в файл: ${if (writeFile) "включена" else "выключена"}")
    }

    fun isFileWritingEnabled(): Boolean = writeFile

    @Synchronized
    fun setFileWritingEnabled(enabled: Boolean) {
        writeFile = enabled
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WRITE_FILE, enabled)
            .apply()

        if (enabled) {
            if (internalLog == null) {
                internalLog = File(ctx.filesDir, FILE_NAME)
            }
            try {
                internalLog?.writeText(memoryLog.toString())
            } catch (_: Throwable) {}
            Log.d(TAG, "Запись логов в файл включена")
        } else {
            try { internalLog?.delete() } catch (_: Throwable) {}
            Log.d(TAG, "Запись логов в файл выключена")
        }
    }

    @Synchronized
    fun log(message: String) {
        val entry = formatEntry(message)
        Log.d(TAG, message)
        memoryLog.append(entry)
        if (writeFile) appendFile(entry)
    }

    @Synchronized
    fun error(message: String, throwable: Throwable? = null) {
        val entry = formatEntry("ERROR: $message")
        Log.e(TAG, message, throwable)
        memoryLog.append(entry)
        if (writeFile) appendFile(entry)

        if (throwable != null) {
            val stack = Log.getStackTraceString(throwable) + "\n"
            memoryLog.append(stack)
            if (writeFile) appendFile(stack)
        }
    }

    @Synchronized
    fun getLogContent(): String =
        memoryLog.toString().ifEmpty { "Лог пока пуст" }

    fun getLogFilePath(): String = "Внутреннее хранилище приложения/$FILE_NAME"

    @Synchronized
    fun clear() {
        memoryLog.setLength(0)
        try { internalLog?.writeText("") } catch (_: Throwable) {}
        log("Лог очищен")
    }

    private fun formatEntry(message: String): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        return "[$ts] $message\n"
    }

    private fun appendFile(text: String) {
        try {
            if (internalLog == null) {
                val ctx = appContext ?: return
                internalLog = File(ctx.filesDir, FILE_NAME)
            }
            internalLog?.appendText(text)
        } catch (_: Throwable) {}
    }
}
