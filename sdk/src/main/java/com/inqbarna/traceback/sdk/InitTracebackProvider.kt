package com.inqbarna.traceback.sdk

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 17/6/25
 */
class InitTracebackProvider : ContentProvider() {
    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int = 0

    override fun getType(p0: Uri): String? = null

    override fun insert(p0: Uri, p1: ContentValues?): Uri? = null

    override fun onCreate(): Boolean {
        Traceback.init(requireNotNull(context))
        return true
    }

    override fun query(
        p0: Uri,
        p1: Array<out String>?,
        p2: String?,
        p3: Array<out String>?,
        p4: String?
    ): Cursor? {
        TODO("Not yet implemented")
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        TODO("Not yet implemented")
    }
}
