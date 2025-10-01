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

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.inqbarna.traceback.sdk.InstallReferrerProvider
import com.inqbarna.traceback.sdk.logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 31/10/25
 */
internal class DefaultInstallReferrerProvider(private val appContext: Context) : InstallReferrerProvider {
    override suspend fun resolveInstallReferrer(): Result<Uri> {
        return suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(appContext).build()
            client.startConnection(
                object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        if (!cont.isActive) {
                            return
                        }
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                val referrerUri = client.installReferrer.installReferrer
                                val finalUri = referrerUri.toUri()
                                if (finalUri.scheme != "https") {
                                    logger.info("Unexpected referrer: $finalUri")
                                    cont.resume(Result.failure(Exception("Unexpected referrer, won't handle")))
                                } else {
                                    logger.info("Install referrer received: $finalUri")
                                    cont.resume(Result.success(finalUri))
                                }
                            }

                            else -> {
                                logger.error("Failed to get install referrer, response code: $responseCode")
                                cont.resume(Result.failure(Exception("Failed to get install referrer, response code: $responseCode")))
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        if (!cont.isActive) {
                            return
                        }
                        logger.warn("Install referrer service disconnected")
                        cont.resume(Result.failure(Exception("Install referrer service disconnected")))
                    }
                }
            )

            cont.invokeOnCancellation {
                try {
                    client.endConnection()
                    logger.debug("Install referrer client connection ended")
                } catch (e: Exception) {
                    logger.error("Error ending install referrer client connection", e)
                }
            }
        }

    }
}
