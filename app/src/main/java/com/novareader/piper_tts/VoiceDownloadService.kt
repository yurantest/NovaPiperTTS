package com.novareader.piper_tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Загрузка голосов в foreground-сервисе: не зависит от жизни Activity (поворот, сворачивание,
 * закрытие экрана), показывает уведомление с прогрессом и кнопкой «Отмена», докачивает после обрыва.
 */
class VoiceDownloadService : Service() {
    private val pool = Executors.newSingleThreadExecutor()
    private val flags = ConcurrentHashMap<String, AtomicBoolean>()
    private val lock = Any()
    private var pending = 0
    private var lastStartId = 0
    @Volatile private var currentTitle = ""
    @Volatile private var currentPercent = -1

    private val repo by lazy { PiperVoiceRepository(this) }
    private val manager by lazy { PiperModelManager(this) }

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flagsInt: Int, startId: Int): Int {
        synchronized(lock) { lastStartId = startId }
        // startForegroundService требует вызвать startForeground быстро — делаем это сразу.
        startFg(buildNotification())
        val key = intent?.getStringExtra(EXTRA_KEY)
        when (intent?.action) {
            ACTION_START -> if (key != null) enqueue(key)
            ACTION_CANCEL -> if (key != null) flags[key]?.set(true) else flags.values.forEach { it.set(true) }
        }
        if (synchronized(lock) { pending == 0 }) finishIfIdle()
        return START_NOT_STICKY
    }

    private fun enqueue(key: String) {
        val voice = repo.loadCached().firstOrNull { it.key == key }
        if (voice == null) { FileLogger.log("download: unknown voice $key"); return }
        if (VoiceDownloads.isActive(key) || manager.isInstalled(voice)) return
        val cancel = AtomicBoolean(false)
        flags[key] = cancel
        synchronized(lock) { pending++ }
        VoiceDownloads.queued(key)
        FileLogger.log("Download start: $key")
        pool.execute { run(voice, cancel) }
    }

    private fun run(v: PiperVoice, cancel: AtomicBoolean) {
        currentTitle = v.friendlyName
        currentPercent = -1
        updateNotification()
        var error: String? = null
        try {
            if (cancel.get()) throw PiperModelManager.DownloadCancelled()
            var lastPct = -1
            manager.ensureInstalled(v, { done, total ->
                if (total > 0) {
                    val pct = (done * 100 / total).toInt()
                    if (pct != lastPct) {
                        lastPct = pct
                        currentPercent = pct
                        VoiceDownloads.progress(v.key, pct)
                        updateNotification()
                    }
                }
            }, { cancel.get() })
            repo.setSelected(v.key, true)
            FileLogger.log("Download OK: ${v.key}")
        } catch (e: PiperModelManager.DownloadCancelled) {
            manager.clearPartial(v)
            error = getString(R.string.download_cancelled)
            FileLogger.log("Download cancelled: ${v.key}")
        } catch (e: Throwable) {
            FileLogger.error("Download failed: ${v.key}", e)
            error = e.message ?: e.javaClass.simpleName
        } finally {
            flags.remove(v.key)
            VoiceDownloads.finished(v.key, error)
            synchronized(lock) { pending-- }
            currentTitle = ""
            finishIfIdle()
        }
    }

    private fun finishIfIdle() {
        val id: Int
        synchronized(lock) { if (pending > 0) return; id = lastStartId }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(id)   // не остановится, если за это время пришёл новый запрос
    }

    private fun startFg(n: Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val cancelPi = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (currentTitle.isEmpty()) getString(R.string.app_name) else currentTitle
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(getString(R.string.download_notification))
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.download_cancel), cancelPi)
        if (currentPercent in 0..100) b.setProgress(100, currentPercent, false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    override fun onDestroy() {
        pool.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.novareader.piper_tts.DOWNLOAD_START"
        const val ACTION_CANCEL = "com.novareader.piper_tts.DOWNLOAD_CANCEL"
        const val EXTRA_KEY = "key"
        private const val CHANNEL = "voice_download"
        private const val NOTIF_ID = 42

        fun start(context: Context, key: String) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, VoiceDownloadService::class.java).setAction(ACTION_START).putExtra(EXTRA_KEY, key)
            )
        }

        fun cancel(context: Context, key: String) {
            context.startService(
                Intent(context, VoiceDownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_KEY, key)
            )
        }
    }
}
