# Truckie

Android companion app for Google Maps for delivery drivers. Truckie holds the day's stops, puts
them in a good order, and hands Google Maps (or Waze) one stop at a time. Google Maps only drives;
Truckie decides where to go next. That stops Google Maps inserting new stops mid-route and gets
around Waze's stop limit.

## What works in v0.5

- **Add stops** by typing an address, `lat,lng`, or pasting a Google Maps link. New stops always
  go on the **end** of the list, in the order you add them. Tick **Pickup** for a pickup.
- **Hold and drag:** hold a stop for 500 ms until it lifts, drag it to a new place, let go.
  Everything in between shifts by one, so the list stays in sequence.
- **Optimize** (top bar) chains the stops still to do by road: nearest to you first, then the
  nearest to that stop, and so on. "Nearest" means **road distance** (the drive-time option was
  switched off in v0.4.2 because it gave bad orders). Never automatic. Needs location.
- **Driving loop, one tap per stop:**
  1. The next stop shows in large type with two pill buttons: blue **Navigate** (left) and green **That one's done! Let's ride!** (right, for pickups and deliveries).
  2. Tap it: the stop is struck out into **Done** (tap **Restore** to undo a misclick).
  3. **Navigate to next stop?** counts down 5 seconds. No tap = Yes: Google Maps or Waze opens
     already navigating. **No** keeps you in Truckie.
- **Auto-complete on arrival** (v0.5, off until you turn it on in ⋮): stop within 250 m of the
  next stop for 2 minutes and Truckie does what the green button does, then opens navigation to
  the next stop after 5 seconds. See [Auto-complete on arrival](#auto-complete-on-arrival).
- **Tap a stop** to navigate there, switch pickup/delivery, or remove it.
- **Menu (⋮):** Google Maps or Waze, **Auto-complete on arrival** (with arrival zone and
  **Open Maps by itself**), **Undo optimize** (back to the order added), check for updates, clear all stops.
- **Update check** on launch against this repo's latest GitHub Release: **Update now / Later**.
  It stays quiet unless there's an update. **⋮ > Check for updates** always says what happened:
  up to date, no signal, or GitHub refused the token (and what to fix).

### Auto-complete on arrival

Turn on in **⋮ > Auto-complete on arrival**. A stop is ticked off only when **all** of these hold:

- It is the **next** stop (top of the list). Other stops are never checked, so the house next door can't fire.
- The vehicle was seen **driving** (15 km/h or faster) since this became the next stop. Opening
  Truckie while already parked, or two stops on one street without moving the truck, needs a tap.
- A GPS reading accurate to **±30 m or better** puts you inside the **arrival zone** (250 m by
  default for big industrial sites; ⋮ > Arrival zone switches 100 / 150 / 250 m). Less accurate readings are ignored.
- You stay **under 7 km/h for 2 minutes straight** (walking to the door still counts). Leaving the
  zone or driving off resets the clock to zero.

Then: the stop is struck out, a notification says **Done: <address>** with **Undo** and
**Navigate**, and navigation to the next stop opens after 5 seconds. Undo puts the stop back, and
it won't auto-complete again until you've driven out of its zone. **Restore** under Done works too.

- **Background:** watching starts when you open Truckie or tap Navigate, with a "Auto-complete is
  on" notification (Android requires it) that says what it sees, e.g. "Stopped at 12 Smith St: done
  in 1:20 unless you move". **Turn off** is on that notification. It stops by itself when no stops are left.
  Only "while using the app" location is needed. GPS is checked every 5 s near the stop and every
  30 s when more than 1.5 km away.
- **Open Maps by itself:** Android blocks a background app from opening Maps. Turn on **⋮ > Open
  Maps by itself** (Android's "Display over other apps" switch for Truckie) and the next stop opens
  hands-free. Without it, the notification's **Navigate** button is one tap.
- **Pins:** a stop's position comes from the address search. If the pin is more than the zone away
  from where you park, the stop simply won't auto-complete: tap the green button as usual.

### Android Auto

- Car home screen: next stop, **That one's done! Let's ride!**, **Navigate**, **Undo**, **Stops**.
- **Navigate to next stop?** after each stop, auto-yes after 5 seconds.
- Phone and car share one stop list. Navigation in the car opens Android Auto's default nav app.
- **Auto-complete on arrival** works in the car too: the car screen shows **Navigate to next stop?**
  (auto-yes after 5 s) when Truckie is on the car screen. While Maps is on the car screen, the
  **Done** alert pops up there with **Navigate** and **Undo** buttons.
- Add and drag stops on the phone; Android Auto doesn't allow typing or dragging.
- **To see Truckie in the car** (sideloaded apps are hidden by default): open Android Auto settings
  on the phone, tap **Version** about 10 times to unlock developer settings, then in the ⋮ menu >
  **Developer settings**, turn on **Unknown sources**. Reconnect to the car.

Switches in `app/src/main/java/app/truckie/Features.kt` turn built features on or off. The full
route solver (`ROUTE_SOLVER`) and the menu re-order items (`MANUAL_REORDER`) are off.

Not yet: QR scanning from the work PDA (a later build, waiting on a sample QR code), delivery-platform imports, pickup-before-delivery linking.

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

- **Stable** (what drivers should use): https://github.com/FaradayMicrosystems/truckie/releases/latest
  (download `truckie-vX.Y.apk` under **Assets**).
- **Experimental** (newest work, may break): always the newest build at
  https://github.com/FaradayMicrosystems/truckie/releases/download/experimental/truckie-experimental.apk
  (each build is also kept as its own `vX.Y.Za+N` pre-release).

The repo is private, so these links only work while signed in to GitHub with access to it.
Open the APK on an Android 8.0+ phone and allow installs from that source when asked.

### Signing (why updates install over each other)

Android only installs an update signed with the same key as the installed app. Truckie's one
permanent key is committed in `signing/` (on purpose: the repo is private), so every build, on
GitHub or on a computer, signs the same. Builds before v0.4.2 used throwaway keys: uninstall one of
those once before installing v0.4.2. **If this repo is ever made public, move the key to Actions
secrets first**, because anyone holding it can sign apps as Truckie.

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

**Version numbers (X.Y.Z, always three digits):** X = primary releases only. Y = changes users
will notice: features, experimental features, anything that changes how Truckie works. Z = minor
fixes and cosmetic tweaks (colours, design, logo). An optional letter marks a pre-release:
**a** alpha, **b** beta, **c** release candidate.

| Branch | `baseVersionName` in `app/build.gradle.kts` | Each push publishes | Who gets it |
|---|---|---|---|
| `main` | `"0.5.3"` | full release `v0.5.3` (`truckie-v0.5.3.apk`) | stable installs |
| `experimental` | `"0.5.4a"` (or `b`, `c`) | pre-release `v0.5.4a+<build>` | experimental installs |

- **All new work** goes in a PR to `experimental` (the default branch). Its CHANGELOG section reads
  `## v0.5.4 — unreleased (experimental)`.
- **Full release** (only when Aurora says so): a PR from `experimental` to `main` that drops the
  letter ("0.5.4") and dates the CHANGELOG heading. Merging it publishes `v0.5.4`. Then
  `experimental` moves on to the next version with a letter. The next number depends on the change:
  Z for fixes and behind-the-scenes work, Y for features users notice.
- `versionCode` is GitHub's build number (the `+<build>`), so every new APK installs over every older one.
- The in-app update check follows the install's channel: stable installs only see full releases;
  experimental installs see newer a/b/c builds and the matching stable release.

## Project layout

- `core/` — plain Kotlin, no Android: stop list rules, route optimizer, OSRM and Photon parsing,
  navigation links, version check, arrival detection (`Arrival.kt`). Unit-tested.
- `app/` — Android app (Kotlin, Jetpack Compose): screen, address lookup, hand-off, updater.
  `ArrivalService.kt` watches GPS for auto-complete. `app/.../car/` holds the Android Auto screens (car app library).
