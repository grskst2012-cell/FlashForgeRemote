package com.grsks.printerremote

import android.app.Application
import com.grsks.printerremote.data.AppSettings
import com.grsks.printerremote.data.PrinterStore
import com.grsks.printerremote.monitor.Notifier

class PrinterRemoteApp : Application() {
    val store: PrinterStore by lazy { PrinterStore(this) }
    val settings: AppSettings by lazy { AppSettings(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
