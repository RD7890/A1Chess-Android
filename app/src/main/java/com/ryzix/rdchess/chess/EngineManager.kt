package com.ryzix.rdchess.chess

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class EngineType { RYZIX, STOCKFISH }

/**
 * Discovers, caches, and downloads chess engine binaries.
 *
 * Ryzix   — compiled via CMake → extracted by Android as libryzix.so in nativeLibraryDir.
 *            Always available; no download needed.
 *
 * Stockfish — first checked in nativeLibraryDir (bundled APK build).
 *             If absent, looked up in getExternalFilesDir("engines")/libstockfish.so
 *             (previously downloaded by the user inside the app).
 */
class EngineManager(private val context: Context) {

    companion object {
        private const val TAG = "EngineManager"
        // Official Stockfish 16 release for Android arm64.
        // The binary is downloaded once and stored permanently.
        // Update this URL when a newer Stockfish version is released.
        const val STOCKFISH_DOWNLOAD_URL =
            "https://github.com/official-stockfish/Stockfish/releases/download/sf_16.1/stockfish-android-armv8"
        const val STOCKFISH_FILENAME = "libstockfish.so"
        const val RYZIX_FILENAME     = "libryzix.so"
    }

    // ── Binary discovery ──────────────────────────────────────────────────────

    /** Always-available Ryzix binary (compiled into the APK via CMake). */
    fun getRyzixBinary(): File? {
        val f = File(context.applicationInfo.nativeLibraryDir, RYZIX_FILENAME)
        return if (f.exists() && f.length() > 0L) f.also { Log.d(TAG, "Ryzix found: ${f.absolutePath}") }
               else null.also { Log.w(TAG, "Ryzix binary not found at ${context.applicationInfo.nativeLibraryDir}/$RYZIX_FILENAME") }
    }

    /** Returns the usable Stockfish binary, or null if not yet available. */
    fun getStockfishBinary(): File? {
        // 1. Check APK-bundled binary (present if developer included it in jniLibs)
        val bundled = File(context.applicationInfo.nativeLibraryDir, STOCKFISH_FILENAME)
        if (bundled.exists() && bundled.length() > 0L) {
            Log.d(TAG, "Stockfish bundled: ${bundled.absolutePath}")
            return bundled
        }
        // 2. Check user-downloaded binary
        val downloaded = stockfishDownloadFile
        return if (downloaded.exists() && downloaded.length() > 0L) {
            Log.d(TAG, "Stockfish downloaded: ${downloaded.absolutePath}")
            downloaded
        } else null
    }

    /** The path where Stockfish will be saved after download. */
    val stockfishDownloadFile: File
        get() {
            val dir = context.getExternalFilesDir("engines") ?: context.filesDir.resolve("engines")
            dir.mkdirs()
            return File(dir, STOCKFISH_FILENAME)
        }

    fun isRyzixAvailable()     = getRyzixBinary() != null
    fun isStockfishAvailable() = getStockfishBinary() != null

    fun getBinaryFor(type: EngineType): File? = when (type) {
        EngineType.RYZIX     -> getRyzixBinary()
        EngineType.STOCKFISH -> getStockfishBinary()
    }

    // ── Stockfish download ────────────────────────────────────────────────────

    /**
     * Downloads Stockfish from the official releases.
     * Saves to [stockfishDownloadFile], sets executable bit.
     * [onProgress] receives 0–100.
     * Returns true on success.
     */
    suspend fun downloadStockfish(onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val outFile = stockfishDownloadFile
                outFile.parentFile?.mkdirs()

                val conn = URL(STOCKFISH_DOWNLOAD_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout    = 60_000
                conn.connect()

                if (conn.responseCode !in 200..299) {
                    Log.e(TAG, "Download failed: HTTP ${conn.responseCode}")
                    return@withContext false
                }

                val total = conn.contentLengthLong
                var received = 0L

                conn.inputStream.use { inp ->
                    outFile.outputStream().use { out ->
                        val buf = ByteArray(8_192)
                        var n: Int
                        while (inp.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            received += n
                            if (total > 0) onProgress((received * 100 / total).toInt())
                        }
                    }
                }

                outFile.setExecutable(true)
                Log.d(TAG, "Stockfish downloaded: ${outFile.length()} bytes")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Stockfish download error: ${e.message}", e)
                false
            }
        }
}
