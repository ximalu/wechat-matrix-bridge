package com.ximalu.wmbridge

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.databinding.ActivityMainBinding
import com.ximalu.wmbridge.matrix.MatrixClient
import com.ximalu.wmbridge.service.ForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: Config
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = Config(this)

        // Hide system action bar — we have our own layout
        supportActionBar?.hide()

        // Check NLS permission on first launch
        if (!isNotificationListenerEnabled()) {
            showEnableNlsDialog()
        }

        // Load saved values
        loadConfig()
        updateStatus()

        binding.btnSave.setOnClickListener { saveConfig() }
        binding.btnToggleService.setOnClickListener { toggleService() }
        binding.btnTest.setOnClickListener { sendTest() }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun sendTest() {
        if (!config.isConfigured) {
            Toast.makeText(this, "请先填写 Matrix 配置并保存", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnTest.isEnabled = false
        binding.btnTest.text = getString(R.string.test_sending)
        val client = MatrixClient(config)
        scope.launch {
            val result = client.sendTestMessage()
            withContext(Dispatchers.Main) {
                binding.btnTest.isEnabled = true
                binding.btnTest.text = getString(R.string.btn_test)
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@MainActivity, R.string.test_sent, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@MainActivity,
                            "${getString(R.string.test_failed)}${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun loadConfig() {
        binding.etHomeserver.setText(config.matrixHomeserver)
        if (config.matrixToken.isNotEmpty()) {
            binding.etToken.setText(config.matrixToken)
        }
        binding.etRoomId.setText(config.matrixRoomId)
    }

    private fun saveConfig() {
        config.matrixHomeserver = binding.etHomeserver.text.toString().trim()
        config.matrixToken = binding.etToken.text.toString().trim()
        config.matrixRoomId = binding.etRoomId.text.toString().trim()
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
    }

    private fun toggleService() {
        if (isServiceRunning<ForegroundService>()) {
            // Stop
            config.serviceEnabled = false
            stopService(Intent(this, ForegroundService::class.java))
            Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
        } else {
            // Start
            if (!config.isConfigured) {
                // Save first
                saveConfig()
            }
            if (!config.isConfigured) {
                Toast.makeText(this, "请先填写 Matrix 配置", Toast.LENGTH_SHORT).show()
                return
            }
            config.serviceEnabled = true
            ForegroundService.start(this)
            Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun updateStatus() {
        val running = isServiceRunning<ForegroundService>()
        if (running) {
            binding.tvStatus.text = getString(R.string.status_running)
            binding.statusDot.setBackgroundResource(R.drawable.shape_dot_green)
            binding.btnToggleService.text = getString(R.string.btn_stop_service)
            binding.btnToggleService.setBackgroundColor(
                ContextCompat.getColor(this, R.color.status_stop_bg)
            )
        } else {
            binding.tvStatus.text = getString(R.string.status_stopped)
            binding.statusDot.setBackgroundResource(R.drawable.shape_dot_red)
            binding.btnToggleService.text = getString(R.string.btn_start_service)
            binding.btnToggleService.setBackgroundColor(
                ContextCompat.getColor(this, R.color.status_start_bg)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return manager.getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == ForegroundService::class.java.name
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && packageName == cn.packageName) {
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
}
