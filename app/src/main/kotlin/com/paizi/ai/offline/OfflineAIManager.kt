package com.paizi.ai.offline

import android.content.Context
import com.paizi.core.config.AppConfig
import com.paizi.core.logging.LoggingManager
import com.paizi.database.OfflineModelDao
import com.paizi.database.OfflineModelEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OfflineAIManager(
    private val context: Context,
    private val modelDao: OfflineModelDao,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val TAG = "OfflineAIManager"
    private val activeDownloadJobs = ConcurrentHashMap<Int, Job>()
    private var activeContextHandle: Long = 0L
    private var activeLoadedSlot: Int? = null

    private val _downloadProgressFlow = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val downloadProgressFlow: StateFlow<Map<Int, DownloadProgress>> = _downloadProgressFlow.asStateFlow()

    data class DownloadProgress(
        val slotIndex: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Float,
        val isDownloading: Boolean,
        val error: String? = null
    )

    fun getModelsDirectory(): File = AppConfig.getModelsDirectory(context)

    suspend fun startOrResumeDownload(slotIndex: Int, scope: CoroutineScope) {
        val model = modelDao.getModelBySlot(slotIndex) ?: return
        if (model.downloadUrl.isBlank()) {
            updateModelStatus(slotIndex, "ERROR", "Download URL is empty")
            return
        }

        if (activeDownloadJobs[slotIndex]?.isActive == true) {
            LoggingManager.w(TAG, "Download job already active for slot $slotIndex")
            return
        }

        val targetFile = File(getModelsDirectory(), model.fileName)
        val tempFile = File(getModelsDirectory(), "${model.fileName}.part")

        val job = scope.launch(Dispatchers.IO) {
            try {
                updateModelStatus(slotIndex, "DOWNLOADING", null)
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                LoggingManager.i(TAG, "Starting download for ${model.name}. Existing bytes: $existingBytes")

                val requestBuilder = Request.Builder().url(model.downloadUrl)
                if (existingBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=$existingBytes-")
                }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IOException("HTTP error ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    val totalExpected = if (existingBytes > 0 && response.code == 206) {
                        existingBytes + contentLength
                    } else if (model.fileSize > 0) {
                        model.fileSize
                    } else {
                        contentLength
                    }

                    // Check storage space
                    val freeSpace = getModelsDirectory().freeSpace
                    if (freeSpace < (totalExpected - existingBytes)) {
                        throw IOException("Insufficient storage space: required ${(totalExpected - existingBytes) / (1024 * 1024)}MB, available ${freeSpace / (1024 * 1024)}MB")
                    }

                    val append = existingBytes > 0 && response.code == 206
                    FileOutputStream(tempFile, append).use { fos ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var readBytes: Int
                            var downloaded = if (append) existingBytes else 0L
                            var lastProgressUpdate = System.currentTimeMillis()

                            while (input.read(buffer).also { readBytes = it } != -1) {
                                fos.write(buffer, 0, readBytes)
                                downloaded += readBytes

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 300) {
                                    val percent = if (totalExpected > 0) (downloaded.toFloat() / totalExpected.toFloat()) * 100f else 0f
                                    updateProgress(slotIndex, downloaded, totalExpected, percent, true)
                                    lastProgressUpdate = now
                                }
                            }
                        }
                    }
                }

                // Download completed, now verify SHA-256
                updateModelStatus(slotIndex, "VERIFYING", null)
                LoggingManager.i(TAG, "Download finished, computing SHA-256 for ${tempFile.name}")

                val calculatedSha256 = calculateSha256(tempFile)
                LoggingManager.i(TAG, "Calculated SHA-256: $calculatedSha256 (Expected: ${model.sha256Hash})")

                if (model.sha256Hash.isNotBlank() && !calculatedSha256.equals(model.sha256Hash, ignoreCase = true)) {
                    tempFile.delete()
                    throw IOException("SHA-256 Integrity Verification Failed. Expected: ${model.sha256Hash}, Got: $calculatedSha256. Corrupt file deleted.")
                }

                // Rename .part to final target file
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename temporary file to destination: ${targetFile.name}")
                }

                val finalModel = model.copy(
                    status = "INSTALLED",
                    downloadedBytes = targetFile.length(),
                    localFilePath = targetFile.absolutePath,
                    lastError = null
                )
                modelDao.insertOrUpdateModel(finalModel)
                updateProgress(slotIndex, targetFile.length(), targetFile.length(), 100f, false)
                LoggingManager.i(TAG, "Model ${model.name} successfully installed at ${targetFile.absolutePath}")

            } catch (e: CancellationException) {
                LoggingManager.w(TAG, "Download paused/cancelled for slot $slotIndex")
                updateModelStatus(slotIndex, "PAUSED", "Download paused")
                val currentDownloaded = if (tempFile.exists()) tempFile.length() else 0L
                updateProgress(slotIndex, currentDownloaded, model.fileSize, 0f, false)
            } catch (e: Exception) {
                LoggingManager.e(TAG, "Download failed for slot $slotIndex", e)
                updateModelStatus(slotIndex, "ERROR", e.message)
                val currentDownloaded = if (tempFile.exists()) tempFile.length() else 0L
                updateProgress(slotIndex, currentDownloaded, model.fileSize, 0f, false, e.message)
            } finally {
                activeDownloadJobs.remove(slotIndex)
            }
        }
        activeDownloadJobs[slotIndex] = job
    }

    fun pauseDownload(slotIndex: Int) {
        activeDownloadJobs[slotIndex]?.cancel()
        activeDownloadJobs.remove(slotIndex)
    }

    suspend fun deleteModel(slotIndex: Int) {
        pauseDownload(slotIndex)
        val model = modelDao.getModelBySlot(slotIndex) ?: return
        val targetFile = File(getModelsDirectory(), model.fileName)
        val tempFile = File(getModelsDirectory(), "${model.fileName}.part")

        if (targetFile.exists()) targetFile.delete()
        if (tempFile.exists()) tempFile.delete()

        if (activeLoadedSlot == slotIndex) {
            unloadModel()
        }

        modelDao.insertOrUpdateModel(
            model.copy(
                status = "NOT_CONFIGURED",
                downloadedBytes = 0L,
                localFilePath = "",
                lastError = null
            )
        )
        updateProgress(slotIndex, 0L, model.fileSize, 0f, false)
    }

    suspend fun loadModel(slotIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val model = modelDao.getModelBySlot(slotIndex) ?: return@withContext false
        val file = File(model.localFilePath)
        if (!file.exists()) {
            updateModelStatus(slotIndex, "ERROR", "Model file does not exist locally")
            return@withContext false
        }

        try {
            updateModelStatus(slotIndex, "LOADING", null)
            unloadModel()

            activeContextHandle = LlamaBridge.loadModel(file, model.contextSize)
            activeLoadedSlot = slotIndex
            updateModelStatus(slotIndex, "LOADED", null)
            LoggingManager.i(TAG, "Model ${model.name} loaded into memory (Handle: $activeContextHandle)")
            true
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Failed to load model $slotIndex", e)
            updateModelStatus(slotIndex, "ERROR", e.message)
            false
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (activeContextHandle != 0L) {
            LlamaBridge.free(activeContextHandle)
            activeContextHandle = 0L
        }
        activeLoadedSlot?.let { slot ->
            val model = modelDao.getModelBySlot(slot)
            if (model != null && (model.status == "LOADED" || model.status == "ACTIVE")) {
                modelDao.insertOrUpdateModel(model.copy(status = "INSTALLED"))
            }
        }
        activeLoadedSlot = null
    }

    suspend fun generateInference(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        if (activeContextHandle == 0L || activeLoadedSlot == null) {
            throw IllegalStateException("No offline GGUF model is currently loaded in memory.")
        }
        LlamaBridge.infer(activeContextHandle, prompt, maxTokens)
    }

    private suspend fun updateModelStatus(slotIndex: Int, status: String, error: String?) {
        val model = modelDao.getModelBySlot(slotIndex) ?: return
        modelDao.insertOrUpdateModel(model.copy(status = status, lastError = error))
    }

    private fun updateProgress(slotIndex: Int, downloaded: Long, total: Long, percent: Float, isDownloading: Boolean, error: String? = null) {
        val map = _downloadProgressFlow.value.toMutableMap()
        map[slotIndex] = DownloadProgress(slotIndex, downloaded, total, percent, isDownloading, error)
        _downloadProgressFlow.value = map
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
