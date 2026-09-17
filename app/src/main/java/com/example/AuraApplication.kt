package com.example

import android.app.Application
import android.content.Intent
import com.example.core.di.AppContainer
import com.example.core.di.DefaultAppContainer
import com.example.playback.AuraPlaybackNotificationService

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Start a lightweight observer service with the app process. It stays silent while
        // nothing is playing and promotes itself to a media-playback foreground service as soon
        // as Aura has an active track.
        startService(Intent(this, AuraPlaybackNotificationService::class.java))
    }
}