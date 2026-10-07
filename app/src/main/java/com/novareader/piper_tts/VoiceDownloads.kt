package com.novareader.piper_tts

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Состояние загрузок. Пишет сервис, читает Activity; колбэки приходят в главном потоке. */
object VoiceDownloads {
    interface Listener {
        fun onProgress(key: String, percent: Int)
        fun onFinished(key: String, error: String?)
    }

    private val main = Handler(Looper.getMainLooper())
    private val active = ConcurrentHashMap<String, Int>()   // key -> процент (-1: в очереди)
    private val listeners = CopyOnWriteArrayList<Listener>()

    fun add(l: Listener) { listeners.addIfAbsent(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    fun isActive(key: String) = active.containsKey(key)
    fun percent(key: String): Int = active[key] ?: -1

    fun queued(key: String) { active[key] = -1; post { l -> l.onProgress(key, -1) } }

    fun progress(key: String, percent: Int) {
        if (active.put(key, percent) == percent) return
        post { l -> l.onProgress(key, percent) }
    }

    fun finished(key: String, error: String?) {
        active.remove(key)
        post { l -> l.onFinished(key, error) }
    }

    private inline fun post(crossinline f: (Listener) -> Unit) {
        main.post { for (l in listeners) f(l) }
    }
}
