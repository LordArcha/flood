# Flood

Flood is an open-source Android live wallpaper in the same family as Diffuse: the current album is melted into a soft color field, that field drifts like liquid, and a beat stirs it. The cover is not shown as a picture.

It is original code, with its own name and visuals. It is not affiliated with Depthware or Diffuse.

## What it does

- Live wallpaper rendered on the GPU by blurring the current album into color and warping that field every frame
- Reads the song title, artist, and cover from the system media session, so it works with Spotify, YouTube Music, Apple Music, Tidal, SoundCloud, and any other player that posts one
- Fluid scale, motion, blur, darken, album color, a tint, or mono
- Remembers the last cover across reboots
- Live beats from the audio that is playing, including headphones
- Optional microphone, off unless you turn it on
- Shares a now-playing card

There is no network permission. Covers stay on the phone. Audio is not written to disk.

## Beats

With Live beats on, Flood measures the player that is producing sound. It does not open the microphone. Android still shows a permission named for recording, because that is the permission a visualizer has to hold. Leave **Microphone** off and Flood will not capture the room.

Turn **Microphone** on only if a player blocks that meter. Then Flood listens to the room while its screen or wallpaper is visible, shows a notification, and closes the microphone when the screen turns off. Headphones will not move the wallpaper in that mode.

## Open it in Android Studio

Android Studio is installed at `/opt/android-studio`. The SDK is in `~/Android/Sdk`.

1. Open this project. Source: https://github.com/LordArcha/flood
2. Let Gradle sync.
3. Run the `app` configuration on a device.

On the phone:

1. Allow notification access when Flood asks. That is how the cover and title are read.
2. Set Flood as the live wallpaper.
3. Play something. The home screen should pick up the cover.
4. Turn on Live beats if you want the field to react. Leave Microphone off.

On Xiaomi and other phones with aggressive power management, also allow Flood to run in the background, or the listener stops after a while.

## Build from a terminal

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Release build

F-Droid compiles this repository from the tag `v0.1.0` and signs the result. A local release build is unsigned unless `keystore.properties` exists in the project root (that file is gitignored):

```
storeFile=../path/to/flood.keystore
storePassword=...
keyAlias=flood
keyPassword=...
```

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleRelease
```

The store listing text lives in `fastlane/metadata/android/en-US/`. The F-Droid recipe to submit is `fdroid/app.flood.wallpaper.yml`.

## License

MIT. See `LICENSE`.
