package com.ximalu.wmbridge

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.databinding.ActivityAuthorizationBinding
import com.ximalu.wmbridge.service.ForegroundService
import com.ximalu.wmbridge.service.MyDeviceAdminReceiver

/**
 * 一站式权限引导页。
 * 每 500ms 轮询检查所有权限状态，实时更新 UI。
 */
class AuthorizationActivity : AppCompatActivity(), Runnable {

    private lateinit var binding: ActivityAuthorizationBinding
    private lateinit var config: Config
    private val handler = Handler(Looper.getMainLooper())
    private val permissionViews = mutableListOf<PermissionItem>()

    private data class PermissionItem(
        val title: String,
        val desc: String,
        val btnText: String,
        val checkAction: () -> Boolean,
        val grantAction: () -> Unit,
        var view: LinearLayout? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthorizationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        config = Config(this)

        setupPermissionItems()
        setupKeepAliveSwitches()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(this, 500)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(this)
    }

    override fun run() {
        refreshAllStatus()
        handler.postDelayed(this, 500)
    }

    private fun setupPermissionItems() {
        val items = listOf(
            PermissionItem(
                title = "通知使用权",
                desc = "必须开启才能监听微信通知",
                btnText = "去设置",
                checkAction = { isNotificationListenerEnabled() },
                grantAction = {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            ),
            PermissionItem(
                title = "通知权限",
                desc = "Android 13+ 需要通知权限才能运行前台服务",
                btnText = "去设置",
                checkAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        !isFinishing
                    } else true
                },
                grantAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                    }
                }
            ),
            PermissionItem(
                title = "忽略电池优化",
                desc = "允许后台运行，防止系统休眠时被杀",
                btnText = "去设置",
                checkAction = { isIgnoringBatteryOptimizations() },
                grantAction = {
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            ),
            PermissionItem(
                title = "设备管理员",
                desc = "激活后卸载需要先取消管理员，防止误卸载",
                btnText = "激活",
                checkAction = { isDeviceAdminActive() },
                grantAction = {
                    startActivity(
                        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(
                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                ComponentName(this@AuthorizationActivity, MyDeviceAdminReceiver::class.java)
                            )
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "防止桥接服务被误卸载"
                            )
                        }
                    )
                }
            ),
            PermissionItem(
                title = "悬浮窗保活权限",
                desc = "允许显示悬浮窗（用于不可见保活，无界面干扰）",
                btnText = "去设置",
                checkAction = { canDrawOverlays() },
                grantAction = {
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
                    }
                }
            )
        )

        val container = binding.permissionContainer
        for (item in items) {
            val view = LayoutInflater.from(this)
                .inflate(R.layout.item_permission, container, false) as LinearLayout
            view.findViewById<TextView>(R.id.permission_title).text = item.title
            view.findViewById<TextView>(R.id.permission_desc).text = item.desc
            view.findViewById<Button>(R.id.btnGrant).text = item.btnText
            view.findViewById<Button>(R.id.btnGrant).setOnClickListener { item.grantAction() }
            container.addView(view)
            permissionViews.add(item.copy(view = view))
        }
    }

    @Suppress("DEPRECATION")
    private fun setupKeepAliveSwitches() {
        val keepalive = binding.keepaliveSection
        keepalive.swKeepAliveNotification.isChecked = config.keepAliveNotification
        keepalive.swKeepAliveOverlay.isChecked = config.keepAliveOverlay

        keepalive.swKeepAliveNotification.setOnCheckedChangeListener { _, isChecked ->
            config.keepAliveNotification = isChecked
            if (isChecked && ForegroundService.isServiceRunning<ForegroundService>(this)) {
                ForegroundService.restartNotification(this)
            }
        }

        keepalive.swKeepAliveOverlay.setOnCheckedChangeListener { _, isChecked ->
            config.keepAliveOverlay = isChecked
            if (ForegroundService.isServiceRunning<ForegroundService>(this)) {
                val intent = Intent(this, ForegroundService::class.java).apply {
                    action = ForegroundService.ACTION_TOGGLE_OVERLAY
                }
                startService(intent)
            }
        }
    }

    private fun refreshAllStatus() {
        for (item in permissionViews) {
            val granted = item.checkAction()
            val iv = item.view?.findViewById<ImageView>(R.id.ivStatus)
            iv?.setImageResource(
                if (granted) R.drawable.ic_check_green else R.drawable.ic_close_red
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { name ->
            val cn = ComponentName.unflattenFromString(name)
            cn != null && packageName == cn.packageName
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(POWER_SERVICE)
        return if (pm is android.os.PowerManager) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else false
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val cn = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val active = dpm.isAdminActive(cn)
        config.deviceAdminActivated = active
        return active
    }

    @Suppress("DEPRECATION")
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }
}
