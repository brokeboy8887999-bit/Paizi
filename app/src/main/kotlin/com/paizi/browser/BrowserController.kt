package com.paizi.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.paizi.core.logging.LoggingManager

class BrowserController(private val context: Context) {
    private val TAG = "BrowserController"

    data class BrowserLaunchResult(
        val success: Boolean,
        val message: String,
        val resolvedPackage: String?
    )

    fun openUrl(url: String): BrowserLaunchResult {
        val sanitized = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        LoggingManager.i(TAG, "Attempting to open browser for: $sanitized")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sanitized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val resolveInfo = intent.resolveActivity(context.packageManager)
        return if (resolveInfo != null) {
            try {
                context.startActivity(intent)
                BrowserLaunchResult(true, "Browser launched successfully", resolveInfo.packageName)
            } catch (e: Exception) {
                LoggingManager.e(TAG, "Error launching browser activity", e)
                BrowserLaunchResult(false, "Failed to launch browser: ${e.message}", resolveInfo.packageName)
            }
        } else {
            LoggingManager.w(TAG, "No activity available to handle web URL in this environment")
            BrowserLaunchResult(false, "No browser or web view activity available in this environment", null)
        }
    }

    suspend fun fetchWebOrSearch(query: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = if (query.startsWith("http://") || query.startsWith("https://")) {
                query
            } else {
                "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) PAIZI-BrowserAgent/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string() ?: ""
                    // Strip HTML tags for readable text snippet
                    raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(3000)
                } else {
                    "Documentation search HTTP ${response.code}: ${response.message}"
                }
            }
        } catch (e: Exception) {
            "Web documentation retrieval failed: ${e.message}"
        }
    }
}
