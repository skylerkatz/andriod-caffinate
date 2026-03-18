package com.skyler.caffeinate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var permissionButton: MaterialButton
    private lateinit var notificationButton: MaterialButton
    private lateinit var instructionsText: TextView
    private lateinit var updateCard: MaterialCardView
    private lateinit var updateText: TextView
    private lateinit var updateButton: MaterialButton
    private lateinit var updateProgress: ProgressBar

    private var pendingDownloadUrl: String? = null
    private var hasCheckedUpdate = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> updateUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        permissionButton = findViewById(R.id.permission_button)
        notificationButton = findViewById(R.id.notification_button)
        instructionsText = findViewById(R.id.instructions_text)
        updateCard = findViewById(R.id.update_card)
        updateText = findViewById(R.id.update_text)
        updateButton = findViewById(R.id.update_button)
        updateProgress = findViewById(R.id.update_progress)

        permissionButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        notificationButton.setOnClickListener {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        updateButton.setOnClickListener {
            downloadAndInstall()
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        if (!hasCheckedUpdate) {
            hasCheckedUpdate = true
            checkForUpdate()
        }
    }

    private fun updateUi() {
        val canWrite = Settings.System.canWrite(this)
        val hasNotificationPermission = checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        when {
            !canWrite -> {
                statusText.text = getString(R.string.permission_needed_body)
                permissionButton.visibility = View.VISIBLE
                notificationButton.visibility = View.GONE
                instructionsText.visibility = View.GONE
            }
            !hasNotificationPermission -> {
                statusText.text = getString(R.string.notification_permission_needed)
                permissionButton.visibility = View.GONE
                notificationButton.visibility = View.VISIBLE
                instructionsText.visibility = View.GONE
            }
            else -> {
                statusText.text = getString(R.string.ready_title)
                permissionButton.visibility = View.GONE
                notificationButton.visibility = View.GONE
                instructionsText.visibility = View.VISIBLE
            }
        }
    }

    private fun checkForUpdate() {
        Thread {
            val result = UpdateChecker.check()
            runOnUiThread {
                when (result) {
                    is UpdateChecker.Result.UpdateAvailable -> {
                        pendingDownloadUrl = result.downloadUrl
                        updateText.text = getString(R.string.update_available, result.version)
                        updateButton.isEnabled = true
                        updateButton.visibility = View.VISIBLE
                        updateProgress.visibility = View.GONE
                        updateCard.visibility = View.VISIBLE
                    }
                    is UpdateChecker.Result.NoUpdate,
                    is UpdateChecker.Result.Error -> {
                        updateCard.visibility = View.GONE
                    }
                }
            }
        }.start()
    }

    private fun downloadAndInstall() {
        val url = pendingDownloadUrl ?: return

        updateButton.isEnabled = false
        updateButton.text = getString(R.string.update_downloading)
        updateProgress.visibility = View.VISIBLE
        updateProgress.progress = 0

        val context = applicationContext

        Thread {
            UpdateInstaller.download(context, url, object : UpdateInstaller.DownloadListener {
                override fun onProgress(percent: Int) {
                    runOnUiThread {
                        updateProgress.progress = percent
                    }
                }

                override fun onComplete(apkFile: File) {
                    runOnUiThread {
                        updateProgress.progress = 100
                        UpdateInstaller.install(context, apkFile)
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        updateButton.isEnabled = true
                        updateButton.text = getString(R.string.update_download_button)
                        updateProgress.visibility = View.GONE
                    }
                }
            })
        }.start()
    }
}
