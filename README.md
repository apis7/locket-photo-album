# Locket Album

A fast, no-bloat photo gallery for Android. Everything stays on the phone. The app has no
internet permission.

## Features

- 4-wide photo grid with month headers, shimmer loading, and a draggable date scrubber
- Jump to any date; search by name, folder, tag, album, month, weekday or year, with a date-range filter
- Albums, favorites, a videos view, and phone folders
- Tags on any photo or video
- Long-press to select, drag to select a range; share, star, add to album or tag, and trash in bulk
- Trash that keeps items for 30 days, with restore and undo
- Full-screen viewer: shared-element zoom, pinch and double-tap zoom, swipe down to close
- Videos autoplay silently; motion photos play when opened; slow-mo plays slow
- Optional stacking of similar photos taken moments apart
- Photo details: camera, exposure, size, location
- Set as home or lock-screen wallpaper with a framing preview

## Build

Requirements: JDK 17 or newer and the Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease        # needs a local signing config
```

Minimum Android version: 11 (API 30).

## Tech

Kotlin, Jetpack Compose (Material 3), MediaStore, Coil 3, Media3 (ExoPlayer and Transformer),
and SQLite for albums and tags.
