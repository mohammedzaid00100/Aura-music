# Aura 1.1.1 — Downloaded entry crash

## Cause and fix

Opening Downloaded could make LazyColumn observe the new shortcut before its captured playlist state had been created. The required state was still null, causing an IllegalArgumentException in LibraryScreen.kt.

LibraryScreen now captures the selected shortcut once per composition. The lazy item list therefore uses the same selection as the playlist state created for that composition. The fix changes no screen layout or playlist storage.

## Regression coverage

DownloadedScreenRegressionTest exercises the full Library screen, including:

- Opening Downloaded from Library with no downloaded songs.
- Repeatedly switching between Downloaded, Liked and the library tabs.
- Opening Downloaded with an existing audio file, adding the song to a new playlist, opening that playlist and checking that playback receives a local-file queue.

All three tests reproduced the previous crash at LibraryScreen.kt:151 in GitHub Actions run 34278780160. The corrected build passed all 14 tests across the screen regression, playlist repository and dialog suites in GitHub Actions run 34279301174.

https://github.com/mohammedzaid00100/aura-apk-build/actions/runs/34279301174

## Update

Version code: 3. Version name: 1.1.1. Package: com.aistudio.auramusic.kznrpx.
The APK build uses the same signing identity as the previously delivered APKs, enabling an in-place update. Do not uninstall or clear storage to install the update; doing so would remove app-local downloads and playlist data.

The API configuration, other screens, download files and playlist format remain unchanged. Physical-device testing is still distinct from these automated UI tests.
