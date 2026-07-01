package com.ximalu.wmbridge.data

import android.content.Context
import android.content.SharedPreferences

enum class SendFrequency(val label: String, val millis: Long?) {
    PER_MESSAGE("每收到一条", null),
    MIN_3("每3分钟", 180_000L),
    MIN_5("每5分钟", 300_000L),
    MIN_10("每10分钟", 600_000L),
    MIN_20("每20分钟", 1_200_000L),
    MIN_30("每30分钟", 1_800_000L)
}

enum class KeywordMode(val label: String) {
    OFF("关闭"),
    INCLUDE("仅包含关键词"),
    EXCLUDE("排除关键词")
}

class Config(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS)

    var matrixHomeserver: String
        get() {
            val v = prefs.getString(KEY_HOMESERVER, "") ?: ""
            return v.ifBlank { DEFAULT_HOMESERVER }
        }
        set(value) = prefs.edit().putString(KEY_HOMESERVER, value).apply()

    var matrixToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var matrixRoomId: String
        get() = prefs.getString(KEY_ROOM_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROOM_ID, value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var sendFrequency: String
        get() = prefs.getString(KEY_FREQ, SendFrequency.MIN_10.name) ?: SendFrequency.MIN_10.name
        set(value) = prefs.edit().putString(KEY_FREQ, value).apply()

    var showPersistentNotification: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NOTIF, value).apply()

    var keywordMode: String
        get() = prefs.getString(KEY_KW_MODE, KeywordMode.OFF.name) ?: KeywordMode.OFF.name
        set(value) = prefs.edit().putString(KEY_KW_MODE, value).apply()

    var keywords: String
        get() = prefs.getString(KEY_KW_LIST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_KW_LIST, value).apply()

    // ── Keep-alive settings ──

    /** 通知栏前台保活 */
    var keepAliveNotification: Boolean
        get() = prefs.getBoolean(KEY_KA_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_KA_NOTIF, value).apply()

    /** 不可见悬浮窗保活 */
    var keepAliveOverlay: Boolean
        get() = prefs.getBoolean(KEY_KA_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_KA_OVERLAY, value).apply()

    // ── Device admin status ──

    /** 设备管理员是否已激活（只读标记） */
    var deviceAdminActivated: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVICE_ADMIN, value).apply()

    val isConfigured: Boolean
        get() = matrixToken.isNotBlank() && matrixRoomId.isNotBlank()

    companion object {
        private const val PREF_NAME = "wmbridge_config"
        private const val KEY_HOMESERVER = "matrix_homeserver"
        private const val KEY_TOKEN = "matrix_token"
        private const val KEY_ROOM_ID = "matrix_room_id"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_FREQ = "send_frequency"
        private const val KEY_SHOW_NOTIF = "show_persistent_notification"
        private const val KEY_KW_MODE = "keyword_mode"
        private const val KEY_KW_LIST = "keywords"
        private const val KEY_KA_NOTIF = "keep_alive_notification"
        private const val KEY_KA_OVERLAY = "keep_alive_overlay"
        private const val KEY_DEVICE_ADMIN = "device_admin_activated"
        private const val DEFAULT_HOMESERVER = "https://mozilla.modular.im"
    }
}
