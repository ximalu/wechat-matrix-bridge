package com.ximalu.wmbridge

import android.app.Application
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.service.ForegroundService

class WMBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = Config(this)
        if (config.serviceEnabled && config.isConfigured) {
            ForegroundService.start(this)
        }
    }
}
