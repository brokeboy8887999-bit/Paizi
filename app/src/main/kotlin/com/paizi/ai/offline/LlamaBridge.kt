package com.paizi.ai.offline

import com.paizi.core.logging.LoggingManager
import java.io.File

/**
 * Real Native JNI Bridge for llama.cpp GGUF inference on Android.
 * Integrates with native arm64-v8a / x86_64 llama.cpp shared libraries.
 */
object LlamaBridge {
    private const val TAG = "LlamaBridge"

    var isLibraryLoaded: Boolean = false
        private set
    var libraryLoadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("llama")
            isLibraryLoaded = true
            LoggingManager.i(TAG, "libllama.so loaded successfully via JNI")
        } catch (e: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            libraryLoadError = e.message ?: "UnsatisfiedLinkError loading libllama.so"
            LoggingManager.w(TAG, "Native llama library not present in current environment: $libraryLoadError")
        } catch (e: Exception) {
            isLibraryLoaded = false
            libraryLoadError = e.message ?: "Exception loading libllama.so"
            LoggingManager.w(TAG, "Error initializing LlamaBridge: $libraryLoadError")
        }
    }

    /**
     * Diagnostic report on native llama.cpp status.
     */
    fun getNativeDiagnostics(): LlamaDiagnosticInfo {
        val abi = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
        return LlamaDiagnosticInfo(
            isLibraryLoaded = isLibraryLoaded,
            supportedAbis = abi,
            loadError = libraryLoadError
        )
    }

    // Native JNI method declarations when libllama.so is linked:
    private external fun nativeInitModel(modelPath: String, contextSize: Int): Long
    private external fun nativeGenerateInference(contextHandle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFreeModel(contextHandle: Long)

    fun loadModel(modelFile: File, contextSize: Int = 4096): Long {
        if (!isLibraryLoaded) {
            throw IllegalStateException("Native llama.cpp library is not loaded in this environment: $libraryLoadError")
        }
        if (!modelFile.exists()) {
            throw IllegalArgumentException("GGUF Model file does not exist: ${modelFile.absolutePath}")
        }
        return nativeInitModel(modelFile.absolutePath, contextSize)
    }

    fun infer(contextHandle: Long, prompt: String, maxTokens: Int = 512): String {
        if (!isLibraryLoaded) {
            throw IllegalStateException("Native llama.cpp library is not loaded: $libraryLoadError")
        }
        return nativeGenerateInference(contextHandle, prompt, maxTokens)
    }

    fun free(contextHandle: Long) {
        if (isLibraryLoaded && contextHandle != 0L) {
            nativeFreeModel(contextHandle)
        }
    }

    data class LlamaDiagnosticInfo(
        val isLibraryLoaded: Boolean,
        val supportedAbis: String,
        val loadError: String?
    )
}
