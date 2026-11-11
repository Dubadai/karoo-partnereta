# Developing PartnerETA

For building the app yourself and publishing releases. Riders only need the [README](../README.md).

## Build and install

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew -q assembleDebug
adb install -r app/build.nosync/outputs/apk/debug/app-debug.apk
```

## Releases and update notices

The app tells riders on the Karoo when a newer release is out: `UpdateCheck.kt` reads
`releases/latest/download/manifest.json` every time the Karoo starts and once a day, never during a
ride, and puts an "Update available" card in Control Center › Notifications. Tapping it opens the
Karoo's Settings › Apps page for the app, whose UPDATE button installs the new APK. So every
release uploads `manifest.json` next to `partnereta.apk`, and is a normal release (not a pre-release):

```bash
./gradlew :app:assembleRelease
./gradlew -q :app:releaseManifest -PreleaseNotes="What changed"
cp app/build.nosync/outputs/apk/release/app-release.apk partnereta.apk
gh release create vX.Y.Z partnereta.apk app/build.nosync/manifest.json --title vX.Y.Z --notes "What changed"
```

## Code map

| File | What it does |
| --- | --- |
| `PartnerEtaExtension.kt` | Entry point; the field and the map overlay (arrow + white-outlined lines) |
| `PartnerEtaSession.kt` | Fetches the feed, follows your position and speed, publishes a `PartnerView` every second |
| `PartnerTracker.kt` | The logic: where she probably is now, ETA, gap along the road (unit-tested) |
| `PartnerFeed.kt` | Live link code and the feed's JSON |
| `Geo.kt` | Distances, bearings, encoded polylines, matching a point to a path |
| `FieldText.kt` / `PartnerEtaDataType.kt` | What the field says / drawing it in Karoo style (`res/layout/field_partner.xml`) |
| `SetupActivity.kt` / `Settings.kt` | Name and live link, with a check of the link |
| `KarooHttp.kt`, `KarooFlows.kt` | Karoo SDK helpers |

Tests: `./gradlew testDebugUnitTest` (`app/src/test/`).
