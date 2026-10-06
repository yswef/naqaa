package com.naqaa.app

import android.app.Application
import com.naqaa.app.content.Content
import com.naqaa.app.notify.Notices
import com.naqaa.app.util.CrashLog

class NaqaaApplication : Application() {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // The recorder and the launch marker come first: anything below can fail without
        // taking the explanation with it.
        CrashLog.install(this)
        CrashLog.sessionStarted(this)
        runCatching { Notices.channels(this) }
        runCatching { Content.load(this) }
    }
}
