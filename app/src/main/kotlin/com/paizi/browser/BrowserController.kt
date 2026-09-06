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
}
