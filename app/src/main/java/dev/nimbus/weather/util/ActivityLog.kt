/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/util/ActivityLog.kt
 * What the app actually does, for battery and data analyses: network calls, shown or not, screen, CPU time.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Opus 5.5 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather.util

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * The activity log (setting, off by default): one line per network call – host and path without
 * the query (no coordinates), bytes, duration – and per change of the app shown or hidden and of
 * the screen; each line with the process's CPU time since the line before. Written to logcat (tag
 * `Nimbus`) and to a small file that turns over, kept on the device until it is shared.
 */
object ActivityLog {
    private const val TAG = "Nimbus"
    private const val PREFS = "diagnostics"
    private const val KEY = "activityLog"
    /** A file this large is turned over: the log keeps it and the one before (some 4000 lines). */
    private const val MAX_BYTES = 256 * 1024L

    /** Where the log is kept (null: no file, e.g. in tests). */
    @Volatile var dir: File? = null
    @Volatile var enabled = false
        private set
    private var prefs: android.content.SharedPreferences? = null
    @Volatile private var shown = 0
    @Volatile private var power: PowerManager? = null
    private var lastCpu = 0L
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "activity-log").apply { isDaemon = true } }
    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    fun init(app: Application) {
        dir = File(app.noBackupFilesDir, "log")
        power = app.getSystemService(PowerManager::class.java)
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { enabled = it.getBoolean(KEY, false) }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { if (shown++ == 0) note("app shown") }
            override fun onActivityStopped(activity: Activity) { if (--shown == 0) note("app hidden") }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = note(if (intent.action == Intent.ACTION_SCREEN_OFF) "screen off" else "screen on")
        }, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        note("process start")
    }

    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        if (!on) note("log off")
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
        if (on) note("log on")
    }

    /** One line: time, the app's state, CPU time since the line before, [event]. */
    fun note(event: String) {
        if (!enabled) return
        val line = synchronized(this) {
            val cpu = Process.getElapsedCpuTime()
            val state = when {
                power?.isInteractive == false -> "screen-off"
                shown > 0 -> "front"
                else -> "back"
            }
            "${LocalDateTime.now().format(time)} $state cpu+${cpu - lastCpu}ms $event".also { lastCpu = cpu }
        }
        Log.i(TAG, line)
        val dir = dir ?: return
        writer.execute {
            runCatching {
                dir.mkdirs()
                val file = File(dir, "activity.log")
                if (file.length() > MAX_BYTES) file.renameTo(File(dir, "activity.1.log"))
                file.appendText(line + "\n")
            }
        }
    }

    /** Waits until every line noted so far is written (tests; before sharing). */
    fun flush() { writer.submit {}.get() }

    /** The log so far, the older file first, as one file to share; null: nothing logged. */
    fun export(into: File): File? {
        flush()
        val dir = dir ?: return null
        val parts = listOf("activity.1.log", "activity.log").map { File(dir, it) }.filter { it.exists() }
        if (parts.isEmpty()) return null
        into.parentFile?.mkdirs()
        into.writeText(parts.joinToString("") { it.readText() })
        return into
    }

    /** Deletes the log (and what was exported of it). */
    fun clear() {
        flush()
        dir?.listFiles()?.forEach { it.delete() }
    }

    /**
     * Host and path – the query left out, every number in the path masked: map tiles say there where
     * one looks, some services (Sensor.Community) take the coordinates in the path.
     */
    fun where(url: HttpUrl) = url.host + url.pathSegments.joinToString("/", prefix = "/") { it.replace(DIGITS, "#") }
    private val DIGITS = Regex("[0-9]+")

    /** Sees every call of the client it is given to; while the log is off, none. */
    val events = EventListener.Factory { call -> if (enabled) CallLog() else EventListener.NONE }

    private class CallLog : EventListener() {
        private val start = System.nanoTime()
        private var received = 0L
        private var sent = 0L
        private var code: Int? = null
        private var fromCache = false

        override fun cacheHit(call: Call, response: Response) { fromCache = true }
        override fun requestBodyEnd(call: Call, byteCount: Long) { sent += byteCount }
        override fun responseHeadersEnd(call: Call, response: Response) { code = response.code }
        override fun responseBodyEnd(call: Call, byteCount: Long) { received += byteCount }
        override fun callEnd(call: Call) = done(call, null)
        override fun callFailed(call: Call, ioe: IOException) = done(call, ioe)

        private fun done(call: Call, error: IOException?) {
            // answered from the cache without the network: nothing went over the air
            if (fromCache && code == null) return
            val ms = (System.nanoTime() - start) / 1_000_000
            val result = error?.let { "failed (${it.javaClass.simpleName})" } ?: "$code"
            note("${call.request().method} ${where(call.request().url)} $result ↓${kb(received)} ↑${kb(sent)} ${ms}ms")
        }

        private fun kb(bytes: Long) = String.format(java.util.Locale.ROOT, "%.1fkB", bytes / 1024.0)
    }
}
