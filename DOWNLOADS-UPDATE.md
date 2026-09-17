# Aura Music — song downloads update

## Delivery status

The source now includes download controls, persistent phone storage and local audio playback. **No updated APK was built, and the Android tests have not been run.** This workspace has no Android SDK, Gradle installation or Kotlin compiler; external tool downloads were blocked. The supplied APK was inspected to confirm its application ID matches this project (`com.aistudio.auramusic.kznrpx`). It is the original binary, not a build of these changes.

## Using the feature after rebuilding

1. Tap the download arrow beside a song on Home or Search, or beside the heart in the expanded player.
2. The arrow becomes a progress indicator, then a downloaded check mark.
3. Open **Library → Downloaded** to see completed songs and pending or failed transfers.
4. Play completed songs from that list with Wi-Fi and mobile data switched off.
5. Tap a downloaded check mark to remove the saved file. Tap an active download to cancel, or a failed download to retry/remove it.

There is no song-count limit or artificial storage quota. Android reports insufficient space when the phone cannot hold a download. Both Wi-Fi and mobile-data downloads are enabled; roaming downloads wait. Android manages queued transfers after you leave Aura, subject to the phone's normal network and background restrictions. A request interrupted before it reaches Android's queue becomes retryable when Aura reopens.

Songs are stored in Aura's app-specific Music folder on the phone, not in a disposable cache or cloud service. The downloads index is kept outside Android backup. Clearing app data or uninstalling Aura removes these app-owned downloads. This update provides offline listening inside Aura; it does not add export to the public Music folder. Artwork and lyrics retain their existing online/cache behavior.

## Changes kept within the requested feature

- Added `core/download/DownloadRepository.kt` and `core/design/DownloadButton.kt`.
- Connected the download button to the existing shared song row and full player.
- Connected the existing Downloaded shortcut to real downloads and replaced its hardcoded count.
- Passed the download repository through the existing application container and Library view model.
- Enabled Media3 local-file reading and local-first lookup for selected tracks and queue transitions. Queue items carry a stable track ID so items queued before a download completes can also use the saved file.
- Added offline storage/metadata regression tests.

The existing providers, API configuration, dependencies, manifest, branding, themes, artwork assets and unrelated screens are unchanged. The existing provider's title/artist resolution behavior is reused; its matching accuracy and service availability have not been independently verified.

## Build the updated APK

Use the same Android build environment and signing key that produced the original APK. Apply this source there and build its app module. The APK must be rebuilt from source; editing this ZIP does not update an installed app.

For Android Studio:

1. Extract this ZIP and open the folder containing `settings.gradle.kts`.
2. Use the project's existing tool versions: Gradle 9.3.1, Android Gradle Plugin 9.1.1 and Android SDK 36.1, as declared in the supplied project. Keep the environment/configuration files from your working project.
3. The original ZIP omits `gradlew`, `gradlew.bat`, the wrapper JAR and signing keystores. Reuse these from your working project. Alternatively, with Gradle 9.3.1 installed, run `gradle wrapper --gradle-version 9.3.1` in this folder to generate wrapper files.
4. For an in-place update, retain the original signing configuration/key. Do not uninstall the current app just to work around a signing mismatch. For a separate debug build, the original README explains how to remove the custom `debugConfig` assignment and let Android Studio use its standard debug signing key; this will not necessarily install over the original APK.
5. Run the new focused tests, then assemble the app:

   ```text
   gradlew.bat :app:testDebugUnitTest --tests "com.example.core.download.OfflineDownloadsTest"
   gradlew.bat :app:assembleDebug
   ```

   On macOS/Linux use `./gradlew` in place of `gradlew.bat`. Use your existing release signing/build process for a distributable release APK. Release keys and passwords are not included in this deliverable.

6. A successful debug build places the APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Checks performed and checks still required

Performed: compared every original source/archive file against the modified project; reviewed download persistence, duplicate requests, cancellation races, missing/truncated file handling, free-space errors and local playback wiring; confirmed the APK/source application IDs match; verified the final ZIP's CRC integrity. Seven existing source files were modified; 78 original files were preserved byte-for-byte.

Added but not executed: seven Robolectric tests for restoration across repository restarts, missing/truncated file rejection, interrupted preparation recovery, local media metadata, remote queue lookup IDs and safe filenames.

Required on a phone before distribution:

- Download two songs, turn on airplane mode, close/reopen Aura and play both from Library → Downloaded. Seek, skip, repeat and shuffle.
- Start a song online, download another queued song, then disable the network and advance to it.
- Leave Aura during a download, reopen it and confirm progress/completion is recovered.
- Tap download repeatedly; confirm only one transfer appears. Cancel and quickly retry; confirm the canceled request cannot enqueue later.
- Interrupt the network mid-download and restore it. Confirm Android resumes where supported or provides a retryable failure.
- Check insufficient-space behavior on a disposable emulator with limited storage; do not fill your personal phone for this test.
- Remove a download, verify the count changes and the file is removed, then download it again.
- Check song rows and the player on a narrow screen with larger text enabled.

Android API references used: [DownloadManager.Request](https://developer.android.com/reference/android/app/DownloadManager.Request) and [Media3 DefaultDataSource](https://developer.android.com/reference/androidx/media3/datasource/DefaultDataSource).
