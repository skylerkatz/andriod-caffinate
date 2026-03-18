package com.skyler.caffeinate

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer version of the app.
 */
object UpdateChecker {

    sealed class Result {
        data class UpdateAvailable(val version: String, val downloadUrl: String) : Result()
        data object NoUpdate : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Checks the GitHub Releases API for a newer version.
     * Must be called off the main thread.
     */
    fun check(): Result {
        return try {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (connection.responseCode != 200) {
                return Result.NoUpdate // No releases yet or API error — silently skip
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").removePrefix("v")
            if (tagName.isEmpty()) return Result.NoUpdate

            val currentVersion = BuildConfig.VERSION_NAME
            if (!isNewer(remote = tagName, current = currentVersion)) {
                return Result.NoUpdate
            }

            // Find the APK asset in the release
            val assets = json.optJSONArray("assets") ?: return Result.NoUpdate
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (downloadUrl.isNotEmpty()) {
                        return Result.UpdateAvailable(tagName, downloadUrl)
                    }
                }
            }

            Result.NoUpdate
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Simple version comparison: splits on "." and compares each segment numerically.
     * Returns true if remote is newer than current.
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
