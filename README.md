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
