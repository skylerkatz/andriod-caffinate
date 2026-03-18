package com.skyler.caffeinate

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an APK and triggers the system package installer.
 */
object UpdateInstaller {

    interface DownloadListener {
        fun onProgress(percent: Int)
        fun onComplete(apkFile: File)
        fun onError(message: String)
    }

    /**
     * Downloads the APK from [url] to the app's cache directory.
     * Must be called off the main thread.
     */
    fun download(context: Context, url: String, listener: DownloadListener) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            // Clean up old downloads
            updateDir.listFiles()?.forEach { it.delete() }

            val apkFile = File(updateDir, "update.apk")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }

            val totalBytes = connection.contentLength
            var downloadedBytes = 0

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            listener.onProgress((downloadedBytes * 100) / totalBytes)
                        }
                    }
                }
            }

            listener.onComplete(apkFile)
        } catch (e: Exception) {
            listener.onError(e.message ?: "Download failed")
        }
    }

    /**
     * Launches the system package installer for the given APK file.
     */
    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
