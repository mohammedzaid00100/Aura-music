package com.example

import android.app.Application
import com.example.core.di.AppContainer
import com.example.core.di.DefaultAppContainer

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Do not start the media notification service while Aura is idle.
        // DefaultAppContainer starts it only after a real track becomes active.
        // This avoids cold-start service restrictions on clean installs while
        // preserving the exact same notification controls during playback.
    }
}
