package com.ximalu.wmbridge

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.service.ForegroundService
import com.ximalu.wmbridge.service.NotificationListener

class MainActivity : AppCompatActivity() {

    private lateinit var config: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = Config(this)

        if (!isNotificationListenerEnabled()) {
            showEnableNlsDialog()
        }

        setupViews()
    }

    private fun setupViews() {
        // Load saved config
        val homeserver = "${config.matrixHomeserver}"
        val token = "${config.matrixToken}"
        val roomId = "${config.matrixRoomId}"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && pkgName == cn.packageName) {
                return true
            }
        }
        return false
    }

    private fun showEnableNlsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.nls_title)
            .setMessage(R.string.nls_message)
            .setPositiveButton(R.string.nls_go_settings) { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveConfig(homeserver: String, token: String, roomId: String) {
        config.matrixHomeserver = homeserver
        config.matrixToken = token
        config.matrixRoomId = roomId

        if (config.isConfigured) {
            config.serviceEnabled = true
            ForegroundService.start(this)
            Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopService() {
        config.serviceEnabled = false
        stopService(Intent(this, ForegroundService::class.java))
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
    }
}
