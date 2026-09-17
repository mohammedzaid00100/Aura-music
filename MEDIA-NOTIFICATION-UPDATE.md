# Aura Music 1.1.4 — playback notification

Aura now keeps an Android media playback notification while a track is active. The notification shows the current title and artist and provides Previous, Play/Pause, and Next controls. Tapping the notification returns to Aura.

## Implementation

- `AuraPlaybackNotificationService` observes the existing `PlaybackController.playbackState`.
- Notification actions call the same `PlaybackController` used by Aura's in-app player, so there is no second ExoPlayer and no separate queue.
- A system `MediaSession` mirrors the active title, artist, position, play/pause state, and transport actions so Android system/lock-screen media controls can route commands back to Aura.
- The service is started with the Aura app process but remains silent until an active track exists. When a track becomes active it promotes itself to a media-playback foreground service and keeps the notification available while that track is active, including while paused.
- Android 8+ notification-channel APIs are guarded so Aura's existing minimum SDK 24 remains supported.
- The manifest retains the required foreground-service/media-playback declarations.
- Media-session notifications are exempt from Android 13's `POST_NOTIFICATIONS` behavior change; Aura therefore does not need to force a notification-permission prompt just to provide these media controls.

No provider, API, download, downloaded-playlist, theme, navigation, package-ID, or unrelated playback behavior was intentionally changed.

Version: **1.1.4** (`versionCode 6`). Package ID remains `com.aistudio.auramusic.kznrpx`.

## Device acceptance checks

1. Start a song and pull down the notification shade.
2. Confirm title/artist and Previous / Pause / Next controls appear.
3. Pause from the notification and resume from the same notification.
4. Skip forward and backward and confirm Aura's in-app queue stays synchronized.
5. Lock the phone and test the system media controls.
6. Repeat with a downloaded song while offline.
