package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.navigation.AuraApp
import com.example.playback.AuraPlaybackNotificationService
import com.example.ui.theme.AuraMusicTheme
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as AuraApplication).container

        setContent {
            AuraMusicTheme(darkTheme = true) {
                AuraApp(appContainer = appContainer)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Re-arm the playback notification observer whenever Aura returns to the foreground.
        // This covers cases where Android previously stopped the idle service while no track
        // was active, without creating a second player or queue.
        startService(Intent(this, AuraPlaybackNotificationService::class.java))
    }
}

// Retained for test coverage compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
