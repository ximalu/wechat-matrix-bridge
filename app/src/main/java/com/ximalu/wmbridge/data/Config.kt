package com.ximalu.wmbridge.data

import android.content.Context
import android.content.SharedPreferences

class Config(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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

    val isConfigured: Boolean
        get() = matrixToken.isNotBlank() && matrixRoomId.isNotBlank()

    companion object {
        private const val PREF_NAME = "wmbridge_config"
        private const val KEY_HOMESERVER = "matrix_homeserver"
        private const val KEY_TOKEN = "matrix_token"
        private const val KEY_ROOM_ID = "matrix_room_id"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val DEFAULT_HOMESERVER = "https://mozilla.modular.im"
    }
}
