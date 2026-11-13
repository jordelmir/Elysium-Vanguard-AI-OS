# Android screen sharing notification — 2026-10-21

## Scope

Updates only the Elysium Nexus screen-sharing flow in the Android JSM feature.

## Changes

- Declares the media-projection foreground-service permission and service in the feature manifest.
- Requests Android's screen-capture consent before starting the foreground service, then starts the WebRTC pipeline.
- Shows an ongoing, low-priority notification while sharing and provides a **Detener** action that shuts down signaling, WebRTC, and the foreground service.
- Requests notification permission on Android 13+ before sharing and explains when it is required.
- Uses a new notification-channel ID so an older installation's high-priority channel settings do not override the quiet service notification.

## Verification

- `:features:jsm:compileDebugKotlin`: passed.
- Merged JSM manifest contains `FOREGROUND_SERVICE_MEDIA_PROJECTION` and `CaptureForegroundService` with `foregroundServiceType="mediaProjection"`.
- `:app:assembleDebug`: blocked by existing Kotlin errors in `integrations/filemanager` (`MusicHubScreen.kt` and `SovereignScrollbar.kt`), unrelated to this change. Those files were left untouched.
- No APK was installed and no physical notification test is claimed.

## Google Play policy context

Screen capture is user-started and user-visible, and the ongoing notification provides a direct stop control. Release review should verify the merged manifest, the Play Console foreground-service declaration, and the privacy disclosure against the exact APK/AAB.
