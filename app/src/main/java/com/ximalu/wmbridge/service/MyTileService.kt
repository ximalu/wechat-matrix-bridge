package com.ximalu.wmbridge.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import com.ximalu.wmbridge.R

/**
 * Quick Settings 快捷磁贴。
 * 一键查看服务状态 / 跳转无障碍设置。
 */
class MyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val running = ForegroundService.isServiceRunning<ForegroundService>(this)
        if (running) {
            // 服务运行中 → 打开 MainActivity
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivityAndCollapse(intent)
            }
        } else {
            // 服务未运行 → 跳转无障碍设置页
            startActivityAndCollapse(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            )
        }
        updateTileState()
    }

    private fun updateTileState() {
        val running = ForegroundService.isServiceRunning<ForegroundService>(this)
        val tile = qsTile ?: return
        if (running) {
            tile.label = "桥接运行中"
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.subtitle = "点击打开"
            }
        } else {
            tile.label = "桥接已停止"
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.subtitle = "点击设置"
            }
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "WMBridge.Tile"
    }
}
