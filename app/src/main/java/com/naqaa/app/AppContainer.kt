package com.naqaa.app

class AppContainer(private val app: NaqaaApp) {
    val appName: String get() = app.getString(R.string.app_name)
}
