package com.inqbarna.traceback.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import org.slf4j.LoggerFactory

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 17/6/25
 */
@SuppressLint("StaticFieldLeak")
object Traceback {
    private lateinit var appContext: Context
    private val logger by lazy { LoggerFactory.getLogger("Traceback") }
    private lateinit var webView: WebView
    internal fun init(context: Context) {
        appContext = context.applicationContext
        webView = WebView(appContext)
        logger.info("Traceback SDK initialized with context: ${appContext.packageName}")
    }

    suspend fun resolvePendingTracebackLink(intent: Intent): Result<Uri> {
        TODO("Implement the logic to resolve pending traceback links")
    }
}
