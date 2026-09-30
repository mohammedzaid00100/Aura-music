# Aura Music

Native Android music app with song downloads, local-first Media3 playback and downloaded-only playlists, built with Kotlin and Jetpack Compose.

Aura Music grew from a personal need for a music app that keeps downloaded songs available offline and makes them easier to organize by mood

## Key features

- Download controls with progress, cancellation and retry states.
- Persistent audio files in the app's own storage.
- Local-first playback through Android Media3.
- A **Downloaded** library with **All songs** and **Playlists**.
- Create and rename playlists, add downloaded songs, and remove playlist membership without deleting the audio.
- Local playlist persistence without duplicating audio files.

## Stack

Kotlin, Jetpack Compose, Android DownloadManager and Android Media3.

## Development and status

This is the current source repository for Aura Music. The earlier `aura-apk-build` repository contains build work from previous iterations.

Open the project in Android Studio and use the versions declared in its Gradle configuration. Keep the existing application ID and signing identity when producing an update for an installed APK. A successful build does not replace playback and download testing on a device.

## Engineering notes

- [Song downloads and offline playback](DOWNLOADS-UPDATE.md)
- [Downloaded playlists](PLAYLISTS-UPDATE.md)
- [Downloaded-screen crash fix](DOWNLOADED-CRASH-FIX.md)
- [Media notifications](MEDIA-NOTIFICATION-UPDATE.md)
- [Existing notices](NOTICE.md)

These notes document individual development stages; their build and test statements apply to those stages. Online content still depends on the configured providers and network availability.
