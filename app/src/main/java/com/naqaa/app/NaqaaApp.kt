package com.naqaa.app

import android.app.Application

class NaqaaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        lateinit var container: AppContainer
            private set
    }
}
