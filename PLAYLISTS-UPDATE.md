> These are the original playlist-feature notes. For the subsequent 1.1.1 crash fix, see DOWNLOADED-CRASH-FIX.md.

# Downloaded playlists

This update adds playlist organization only inside Library > Downloaded.

## Use

1. Download a song as before. New downloads remain in Downloaded > All songs.
2. Open Downloaded > Playlists > + New playlist and enter a name.
3. In All songs, tap a completed song's three-dot menu > Add to playlist, then choose a playlist. You can also create a playlist directly from this picker.
4. Open a downloaded playlist and tap a song to play that playlist's available downloaded songs.
5. Use the playlist's three-dot menu > Rename playlist to change its name.
6. Within a playlist, a song's menu also offers Remove from playlist. This removes membership only; the downloaded audio remains in All songs.

## Storage and behavior

- Playlist names and song IDs persist locally in downloaded-playlists.json in the app's private no-backup directory.
- A playlist references the existing audio files. Adding the same song to several playlists does not duplicate its audio or start another download.
- Duplicate song entries are prevented. Playlist names must be nonblank, unique ignoring case, and no longer than 80 characters.
- Only complete, available downloads can be added. Pending and failed downloads retain their existing progress, cancel, and retry controls.
- When an audio download is removed, it is omitted from playlist song lists and playback. Its membership is retained so downloading that same song ID again makes it reappear.
- Renaming preserves membership. Failed reads do not overwrite saved playlists; failed writes are reported instead of shown as successful changes.
- Downloaded playlist playback uses local file URLs and queues only downloaded members of that playlist.
- App uninstall or clearing app storage removes local playlist data, as with other app-local data.

## Scope

Three existing source files were modified: LibraryScreen.kt, TrackItem.kt (an optional trailing slot used only in Downloaded), and DownloadRepository.kt (local playlist repository access).

Two implementation files and two targeted test files were added. Other screens, online playlists, API configuration, download transfers, playback controller, theme, assets, dependencies, application ID and version settings are unchanged.

## Verification

App compilation and the targeted OfflinePlaylistRepositoryTest and DownloadedPlaylistDialogsTest suites passed in GitHub Actions run 34272968919. These cover persistence across restart, duplicate membership, concurrent additions, invalid names, unavailable downloads, membership removal, corrupt storage, failed writes, and the create/add/rename dialogs.

Validation: https://github.com/mohammedzaid00100/aura-apk-build/actions/runs/34272968919

This deliverable is the updated source ZIP. No new signed APK was built in this update. Physical-phone playback and installation still require device testing.
