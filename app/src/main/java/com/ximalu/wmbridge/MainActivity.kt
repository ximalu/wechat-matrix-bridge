package com.ximalu.wmbridge

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.data.KeywordMode
import com.ximalu.wmbridge.data.MaxBatchSize
import com.ximalu.wmbridge.data.SendFrequency
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
        supportActionBar?.hide()

        if (!isNotificationListenerEnabled()) {
            showEnableNlsDialog()
        }

        // Setup dropdown pickers
        setupFrequencyPicker()
        setupKeywordModePicker()
        setupMaxBatchSizePicker()
        loadConfig()
        updateStatus()

        binding.btnSave.setOnClickListener { saveConfig() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, MessageHistoryActivity::class.java))
        }
        binding.btnPermissions.setOnClickListener {
            startActivity(Intent(this, AuthorizationActivity::class.java))
        }
        binding.btnToggleService.setOnClickListener { toggleService() }
        binding.btnTest.setOnClickListener { sendTest() }

        // ── Debug ──
        binding.swDebugLog.isChecked = config.debugLoggingEnabled
        binding.swDebugLog.setOnCheckedChangeListener { _, isChecked ->
            config.debugLoggingEnabled = isChecked
            Toast.makeText(this, if (isChecked) "详细日志已开启" else "详细日志已关闭", Toast.LENGTH_SHORT).show()
        }
        binding.btnShowLogs.setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // ── Frequency dropdown ──

    private fun setupFrequencyPicker() {
        val items = SendFrequency.entries.map { it.label }.toTypedArray()
        binding.etFrequency.setOnClickListener {
            val current = try {
                SendFrequency.valueOf(config.sendFrequency).ordinal
            } catch (_: Exception) { 3 } // default MIN_10
            showPickerDialog(
                items, current,
                "发送频率"
            ) { pos ->
                val freq = SendFrequency.entries[pos]
                config.sendFrequency = freq.name
                binding.etFrequency.setText(freq.label)
            }
        }
    }

    // ── Keyword mode dropdown ──

    private fun setupKeywordModePicker() {
        val items = KeywordMode.entries.map { it.label }.toTypedArray()
        binding.etKeywordMode.setOnClickListener {
            val current = try {
                KeywordMode.valueOf(config.keywordMode).ordinal
            } catch (_: Exception) { 0 }
            showPickerDialog(
                items, current,
                "过滤模式"
            ) { pos ->
                val mode = KeywordMode.entries[pos]
                config.keywordMode = mode.name
                binding.etKeywordMode.setText(mode.label)
            }
        }
    }

    // ── Max batch size dropdown ──

    private fun setupMaxBatchSizePicker() {
        val items = MaxBatchSize.entries.map { it.label }.toTypedArray()
        binding.etMaxBatch.setOnClickListener {
            val current = try {
                MaxBatchSize.valueOf(config.maxBatchSize).ordinal
            } catch (_: Exception) { 1 } // default SIZE_20
            showPickerDialog(
                items, current,
                "单条消息上限"
            ) { pos ->
                val size = MaxBatchSize.entries[pos]
                config.maxBatchSize = size.name
                binding.etMaxBatch.setText(size.label)
            }
        }
    }

    private fun showPickerDialog(
        items: Array<String>, selected: Int, title: String,
        onSelect: (Int) -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(items, selected) { d, pos ->
                onSelect(pos)
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Load / Save ──

    private fun loadConfig() {
        binding.etHomeserver.setText(config.matrixHomeserver)
        if (config.matrixToken.isNotEmpty()) {
            binding.etToken.setText(config.matrixToken)
        }
        binding.etRoomId.setText(config.matrixRoomId)

        // Frequency
        val freq = try {
            SendFrequency.valueOf(config.sendFrequency)
        } catch (_: Exception) { SendFrequency.MIN_10 }
        binding.etFrequency.setText(freq.label)

        // Notification toggle
        binding.swShowNotification.isChecked = config.showPersistentNotification

        // Keywords
        val kwMode = try {
            KeywordMode.valueOf(config.keywordMode)
        } catch (_: Exception) { KeywordMode.OFF }
        binding.etKeywordMode.setText(kwMode.label)
        binding.etKeywords.setText(config.keywords)
        val maxBatch = try {
            MaxBatchSize.valueOf(config.maxBatchSize)
        } catch (_: Exception) { MaxBatchSize.SIZE_20 }
        binding.etMaxBatch.setText(maxBatch.label)
    }

    private fun saveConfig() {
        config.matrixHomeserver = binding.etHomeserver.text.toString().trim()
        config.matrixToken = binding.etToken.text.toString().trim()
        config.matrixRoomId = binding.etRoomId.text.toString().trim()
        config.showPersistentNotification = binding.swShowNotification.isChecked
        config.keywords = binding.etKeywords.text.toString().trim()
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
    }

    // ── Service toggle ──

    private fun toggleService() {
        if (ForegroundService.isServiceRunning<ForegroundService>(this)) {
            config.serviceEnabled = false
            stopService(Intent(this, ForegroundService::class.java))
            Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
        } else {
            saveConfig()
            config.serviceEnabled = true
            ForegroundService.start(this)
            Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    // ── Test send ──

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

    // ── Status ──

    private fun updateStatus() {
        val running = ForegroundService.isServiceRunning<ForegroundService>(this)
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

    // ── NLS check ──

    @Suppress("DEPRECATION")
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { name ->
            val cn = ComponentName.unflattenFromString(name)
            cn != null && packageName == cn.packageName
        }
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
