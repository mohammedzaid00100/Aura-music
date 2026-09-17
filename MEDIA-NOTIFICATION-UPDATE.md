# Aura Music 1.1.4 — playback notification

Aura now keeps a foreground playback notification while a track is active. The notification shows the current title and artist and provides Previous, Play/Pause, and Next actions. Tapping the notification returns to Aura.

The notification uses the existing PlaybackController as its single source of truth, so notification actions and in-app controls operate on the same queue and player state. No provider, API, download, playlist, theme, or navigation behavior was intentionally changed.

Android 13+ requests notification permission when Aura opens so the playback notification can appear in the notification shade.

Version: 1.1.4 (code 6). Package ID remains `com.aistudio.auramusic.kznrpx`.
