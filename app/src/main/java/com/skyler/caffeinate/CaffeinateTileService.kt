package com.skyler.caffeinate

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class CaffeinateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        if (CaffeinateState.isActive(this)) {
            deactivate()
        } else {
            activate()
        }
        refreshTile()
    }

    private fun activate() {
        val currentTimeout = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            60_000
        )
        CaffeinateState.saveOriginalTimeout(this, currentTimeout)
        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            Int.MAX_VALUE
        )
        CaffeinateService.start(this)
        CaffeinateState.setActive(this, true)
    }

    private fun deactivate() {
        val originalTimeout = CaffeinateState.getOriginalTimeout(this)
        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            originalTimeout
        )
        CaffeinateService.stop(this)
        CaffeinateState.setActive(this, false)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val active = CaffeinateState.isActive(this)

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this,
            if (active) R.drawable.ic_caffeinate_on else R.drawable.ic_caffeinate_off
        )
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(
            if (active) R.string.tile_subtitle_on else R.string.tile_subtitle_off
        )
        tile.contentDescription = getString(R.string.tile_label) + " " +
            getString(if (active) R.string.tile_subtitle_on else R.string.tile_subtitle_off)
        tile.updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        if (CaffeinateState.isActive(this)) {
            deactivate()
        }
    }
}
