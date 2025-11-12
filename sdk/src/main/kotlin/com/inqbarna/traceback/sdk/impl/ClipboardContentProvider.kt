/*
 * MIT License
 *
 * Copyright (c) 2025 inqbarna
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.inqbarna.traceback.sdk.impl

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.inqbarna.traceback.sdk.InternalMatchType
import com.inqbarna.traceback.sdk.Traceback.config
import com.inqbarna.traceback.sdk.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 31/10/25
 */

internal interface ClipboardContentProvider {
    suspend fun getClipboardLinkIfAvailable(): String?
}

internal fun obtainClipboardContentProvider(
    focusGainSignal: Flow<Boolean>?,
    context: Context,
): ClipboardContentProvider {
    return when (config.minMatchType) {
        InternalMatchType.Unique -> {
            if (focusGainSignal != null) {
                DefaultClipboardProvider(focusGainSignal, context)
            } else {
                logger.debug("Won't use clipboard, no focus gain signal provided")
                DisabledClipboardProvider
            }
        }
        else -> {
            logger.info("Won't use clipboard, match type is not unique")
            DisabledClipboardProvider
        }
    }
}

private class DefaultClipboardProvider(
    private val focusGainSignal: Flow<Boolean>,
    context: Context,
) : ClipboardContentProvider {

    private val appContext = context.applicationContext

    @SuppressLint("VisibleForTests")
    override suspend fun getClipboardLinkIfAvailable(): String? {
        return runCatching {
            withTimeoutOrNull(1.seconds) {
                // await app in focus before proceeding to ensure clipboard access is valid
                focusGainSignal.first { true }

                val clipboardManager = appContext.getSystemService<ClipboardManager>()!!
                if (
                    clipboardManager.hasPrimaryClip()
                    && clipboardManager.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                ) {
                    val clip =
                        clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(appContext)
                            ?.toString()
                    if (!clip.isNullOrEmpty()) {
                        logger.debug("Using clipboard content as link: $clip")
                        clip.toHttpUrlOrNull()?.toString()?.also {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                clipboardManager.clearPrimaryClip()
                            }
                        }
                    } else {
                        logger.warn("Clipboard content is empty, proceeding without clipboard")
                        null
                    }
                } else {
                    logger.info("No valid clipboard content found, proceeding without clipboard")
                    null
                }
            }
        }.onFailure {
            logger.error("Failed to get clipboard content", it)
        }.getOrNull()
    }
}

private object DisabledClipboardProvider : ClipboardContentProvider {
    override suspend fun getClipboardLinkIfAvailable(): String? = null
}
