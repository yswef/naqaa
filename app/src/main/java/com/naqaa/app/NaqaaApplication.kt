package com.naqaa.app

import android.app.Application
import com.naqaa.app.content.Content
import com.naqaa.app.notify.Notices
import com.naqaa.app.util.CrashLog

class NaqaaApplication : Application() {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Install the crash recorder before other startup work can fail.
        CrashLog.install(this)
        runCatching { Notices.channels(this) }
        runCatching { Content.load(this) }
    }
}
