# Truckie

Android companion app for Google Maps for delivery drivers. Truckie holds the day's stops, puts
them in a good order, and hands Google Maps (or Waze) one stop at a time. Google Maps only drives;
Truckie decides where to go next. That stops Google Maps inserting new stops mid-route and gets
around Waze's stop limit.

## What works in v0.4

- **Add stops** by typing an address, `lat,lng`, or pasting a Google Maps link. New stops always
  go on the **end** of the list, in the order you add them. Tick **Pickup** for a pickup.
- **Hold and drag:** hold a stop for 500 ms until it lifts, drag it to a new place, let go.
  Everything in between shifts by one, so the list stays in sequence.
- **Optimize** (top bar) sorts the stops still to do nearest to farthest from your current
  position, by road distance (straight-line without signal). Never automatic. Needs location.
- **Driving loop, one tap per stop:**
  1. The next stop shows in large type with two pill buttons: blue **Navigate** (left) and green **Delivery completed?** (**Picked up?** for pickups, right).
  2. Tap it: the stop is struck out into **Done** (tap **Restore** to undo a misclick).
  3. **Navigate to next stop?** counts down 5 seconds. No tap = Yes: Google Maps or Waze opens
     already navigating. **No** keeps you in Truckie.
- **Tap a stop** to navigate there, switch pickup/delivery, or remove it.
- **Menu (⋮):** Google Maps or Waze, check for updates, clear all stops.
- **Update check** on launch against this repo's latest GitHub Release: **Update now / Later**.

### Android Auto

- Car home screen: next stop, **Delivery completed?** / **Picked up?**, **Navigate**, **Undo**, **Stops**.
- **Navigate to next stop?** after each stop, auto-yes after 5 seconds.
- Phone and car share one stop list. Navigation in the car opens Android Auto's default nav app.
- Add and drag stops on the phone; Android Auto doesn't allow typing or dragging.
- **To see Truckie in the car** (sideloaded apps are hidden by default): open Android Auto settings
  on the phone, tap **Version** about 10 times to unlock developer settings, then in the ⋮ menu >
  **Developer settings**, turn on **Unknown sources**. Reconnect to the car.

Switches in `app/src/main/java/app/truckie/Features.kt` turn built features on or off. The full
route solver (`ROUTE_SOLVER`) and the menu re-order items (`MANUAL_REORDER`) are off.

Not yet: QR scanning from the work PDA (next build), delivery-platform imports, pickup-before-delivery linking.

## Long-term goal

Auto-import jobs (for example from email, spreadsheets, or dispatch systems) so routes build themselves without manual entry.
QR scanning from the work PDA comes first.

## How the full route solver works (switched off)

1. Truckie asks for a drive-time grid between your position and every stop still to do, from the
   OSRM public server (no key). With no signal, it uses straight-line estimates instead.
2. The phone solves the order: exactly for up to 12 stops (Held-Karp), and for more stops with
   cheapest insertion, nearest neighbour and shuffled starts, each polished with 2-opt and or-opt.
   One-way travel times are respected. 30 stops take well under a second.
3. The list updates. Google Maps only ever sees one destination at a time.

The OSRM public server is for testing. Swapping it for Google's Routes API (traffic-aware, needs a
key and billing) is a change to `Osrm.kt` and `MainViewModel.optimize()` only.

## Install

- Newest build of main: https://github.com/FaradayMicrosystems/truckie/releases/download/latest/truckie-debug.apk
- Versioned releases are on the Releases page as `truckie-vX.Y.apk`.

Open the APK on an Android 8.0+ phone and allow installs from that source when asked.

### One-time setup so updates install over each other

Android only installs an update signed with the same key as the installed app. GitHub's build
machines make a new throwaway key each time unless you give them one. Do this once:

1. On any computer with Java: `keytool -genkeypair -v -keystore truckie.keystore -alias truckie -keyalg RSA -keysize 2048 -validity 10000`
2. `base64 -w0 truckie.keystore` (macOS: `base64 -i truckie.keystore`) and copy the output.
3. In this repo: Settings > Secrets and variables > Actions > New repository secret. Add
   `TRUCKIE_KEYSTORE_B64` (the base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`truckie`) and `KEY_PASSWORD`.
4. Keep `truckie.keystore` and its passwords somewhere safe. Never commit it.

### Update check while the repo is private

GitHub hides a private repo's releases from the app. Either make the repo public, or add a
fine-grained token with read-only **Contents** access to this repo only as the `UPDATE_TOKEN`
secret. Anyone with the APK can extract that token, which is why it must be read-only and limited
to this repo.

## Build

1. Install Android Studio (it includes the Android SDK and JDK 17+), open this folder, let Gradle sync.
2. Run the `app` configuration on a phone or emulator (Android 8.0 / API 26 or newer).

Command line: `./gradlew :app:assembleDebug` (APK in `app/build/outputs/apk/debug/`)
and `./gradlew :core:test` for the unit tests.

## Releases

Raise `versionName` (and `versionCode`) in `app/build.gradle.kts`, add a `## vX.Y` section to
`CHANGELOG.md`, and push to main. The build publishes "Truckie vX.Y" with `truckie-vX.Y.apk`,
which is what the in-app update check finds.

## Project layout

- `core/` — plain Kotlin, no Android: stop list rules, route optimizer, OSRM and Photon parsing,
  navigation links, version check. Unit-tested.
- `app/` — Android app (Kotlin, Jetpack Compose): screen, address lookup, hand-off, updater.
  `app/.../car/` holds the Android Auto screens (car app library).
