package com.naqaa.app

import android.app.Application
import com.naqaa.app.content.Content
import com.naqaa.app.notify.Notices

class NaqaaApplication : Application() {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        Notices.channels(this)
        Content.load(this)
    }
}
