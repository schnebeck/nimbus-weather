/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetProbe.kt
 * Which of the widget's pictures the launcher shows: each carries an empty pixel of its own,
 * fetched from here when it is put up.
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

package dev.nimbus.weather.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.nimbus.weather.util.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The launcher draws the widget, and what size it reports is not always the size it gives it.
 * Each picture shows an empty pixel at its own address (`…/<widget>/<round>/<width>x<height>`):
 * the launcher fetches only that of the picture it puts up – what that tells of the frame
 * [WidgetFrames] works out, and where it is something new the widget is shown anew at once.
 */
class WidgetProbe : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        heard(context, uri)
        // from a file: the launcher reads it at once, no thread left writing
        val file = java.io.File(context.cacheDir, "widget_pixel.png")
        if (file.length() != PIXEL.size.toLong()) file.writeBytes(PIXEL)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        /** A transparent PNG of one pixel. */
        private val PIXEL = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT,
        )

        /** The address of the pixel of widget [id]'s picture of [size], offered in [round]. */
        fun uri(context: Context, id: Int, round: Int, size: WidgetSize): Uri =
            Uri.parse("content://${context.packageName}.probe/$id/$round/${size.widthDp}x${size.heightDp}")

        /**
         * The pixel at [uri] fetched: the search for the frame told – and where that brings
         * something new, the widget shown anew (the next round, or planned for the frame found).
         */
        fun heard(context: Context, uri: Uri): FrameSearch? {
            val parts = uri.pathSegments
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val round = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val size = FrameSearch.size(parts.getOrNull(2)) ?: return null
            val search = WidgetFrames(context).heard(id, round, size) ?: return null
            if (search.settled) {
                val f = search.fit!!
                ActivityLog.note("widget $id: frame ${f.widthDp}×${f.heightDp} dp (${search.signature})")
                android.util.Log.i("NimbusWidget", "widget $id: frame ${f.widthDp}×${f.heightDp} dp")
            }
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { Widgets.show(app, listOf(id)) } }
            return search
        }
    }
}
