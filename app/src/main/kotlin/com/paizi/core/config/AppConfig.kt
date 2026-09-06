package com.paizi.core.config

import android.content.Context
import java.io.File

object AppConfig {
    const val APP_VERSION = "1.0.0"
    const val APP_NAME = "PAIZI"
    const val MAX_FIX_ATTEMPTS = 3
    const val DEFAULT_HTTP_TIMEOUT_SECONDS = 60L
    const val MAX_OFFLINE_MODEL_SLOTS = 5
    const val MAX_ONLINE_PROVIDER_SLOTS = 10

    fun getWorkspaceRoot(context: Context): File {
        val dir = File(context.filesDir, "paizi_workspace")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelsDirectory(context: Context): File {
        val dir = File(context.filesDir, "paizi_models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getExportsDirectory(context: Context): File {
        val dir = File(context.filesDir, "paizi_exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
