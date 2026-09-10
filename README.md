# Spotter Cam

A custom Android camera app built with CameraX (Kotlin) — photo and video in one.

## Features

### Shared
- **Live preview** — full-screen viewfinder before you shoot
- **Zoom** — slider mapped to the camera's real zoom range, live ratio readout
- **Tap-to-focus** — tap the preview; a focus ring animates and the camera focuses/meters there
- **Exposure compensation** — slider driven by the device's actual EV range
- **Front/back camera switch**

### Photo mode
- **Aspect ratio** — cycle 4:3 → 16:9 → 1:1 (1:1 is a center-crop applied right after capture, since CameraX has no native square preset)
- **Flash** — Off → On → Auto
- **Format** — JPEG or PNG

### Video mode
- **Quality** — 480p → 720p → 1080p → 4K (`Quality.SD/HD/FHD/UHD` via CameraX's `Recorder`, with fallback to the nearest supported quality on that device)
- **Frame rate** — 24 / 30 / 60 fps, applied as a target AE FPS range via Camera2 interop
- **Auto stabilization** — toggle On/Off, applied via the Camera2 `CONTROL_VIDEO_STABILIZATION_MODE` capture request
- Tap the shutter to start recording, tap again to stop — a red timer badge shows elapsed time
- Videos save to `Movies/SpotterCam`, photos to `Pictures/SpotterCam` (both via MediaStore, visible in your gallery)

## How to open and build
1. Install **Android Studio** (Hedgehog/Koala or newer): https://developer.android.com/studio
2. Unzip this project, then in Android Studio choose **File → Open** and select the `SpotterCam` folder.
3. Click **Sync Now** when prompted. `gradle-wrapper.jar` isn't bundled in this download — Android Studio will fetch it automatically on sync, or run `gradle wrapper` once from a terminal in the project root if it doesn't.
4. Connect an Android phone (USB debugging on) or use an emulator with a virtual camera — note emulators often don't support real 4K/60fps capture or stabilization, so test on a real device for those.
5. Click **Run ▶**.

## Real-device caveats
- **4K / 60fps / stabilization availability is hardware-dependent.** Not every phone's camera sensor supports every quality/fps/stabilization combination — CameraX falls back to the nearest supported quality automatically, but very high fps at 4K, or stabilization at 4K, may silently be ignored by the sensor on some devices. If something doesn't visibly change, it's usually the hardware declining the combo rather than a bug.
- **Format (video)**: video is always saved as standard MP4 (H.264) — CameraX's stable `Recorder` API doesn't expose a container/codec picker, so there's no meaningful "format" toggle to add for video beyond quality/fps. The Format toggle in the UI applies to photo mode (JPEG/PNG).
- **Audio**: video recording requests `RECORD_AUDIO`; if you deny it, video still records without sound.
- Min SDK 24 (Android 7.0+), target/compile SDK 34.
- App icon is a placeholder vector — swap `ic_launcher_foreground.xml` / `ic_launcher_background.xml` for your own branding.

## Project structure
```
app/src/main/java/com/example/spottercam/MainActivity.kt   -> all camera + video logic
app/src/main/res/layout/activity_main.xml                  -> UI layout (photo + video control rows)
app/src/main/res/values/                                   -> colors, strings, theme
app/src/main/AndroidManifest.xml                            -> permissions (camera, mic, storage<=28)
```
