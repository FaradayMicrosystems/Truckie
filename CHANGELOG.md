# Changelog

## v0.4.2 — 2026-11-04

- **Check for updates** now says exactly what happened: you're up to date (and which release is
  the latest), GitHub couldn't be reached, or GitHub refused the update token (with what to fix).
- The green button now reads **That one's done! Let's ride!** for pickups and deliveries, on the phone and in Android Auto.
- **Optimize by drive time removed** (hidden, code kept behind `Features.OPTIMIZE_BY_DRIVE_TIME`).
  It gave bad orders in real use: the free OSRM server has no live traffic, so drive time chases
  highways and zig-zags. Optimize now always uses **road distance**, and the ⋮ menu toggle is gone.
- **One permanent signing key** (in `signing/`, private repo), so from now on updates install over
  the old app. Uninstall a pre-v0.4.2 build once before installing this one.
- README fixed: install links now point at the Releases page (the old direct link 404s when not
  signed in to GitHub), the signing steps match how it works now, and the update check is described.

## v0.4.1 — 2026-11-04

- **Optimize chains by road:** from where you are, it goes to the nearest stop, then from that stop
  to the nearest one left, and so on. (Before, every stop was measured from your position only.)
- **Choose what "nearest" means** in the ⋮ menu: **Optimize by drive time** (default) or
  **Optimize by road distance**. Truckie remembers your choice.
- The **Optimize** button is wider and shows the mode underneath ("by drive time" / "by road distance").
- One road lookup covers all stops. With no signal it falls back to straight-line distance and says so.

## v0.4 — 2026-11-04

- The next-stop card now has two big pill buttons side by side, so they're easy to tell apart:
  - **Navigate** on the left: blue with white text (shows which app it opens).
  - **Delivery completed?** / **Picked up?** on the right: green.
- Android Auto uses the same colours and order.
- **Undo optimize** in the ⋮ menu puts the stops still to do back in the order you added them
  (this also undoes any dragging). Done stops stay done.
- "Navigate to next stop?" with the 5-second countdown is unchanged.

## v0.3.1 — 2026-11-04

- **Fix:** Navigate sent Google Maps / Waze the coordinates Truckie had looked up, and those
  sometimes pointed at the middle of the suburb instead of the house. Navigate now sends the
  address itself, so Google Maps / Waze find it (phone and Android Auto). Stops entered as
  `lat,lng` still navigate to those exact coordinates.

## v0.3 — 2026-11-04

- **Android Auto:** Truckie shows up on the car screen.
  - Home: the next stop with **Delivery completed?** (or **Picked up?**) and **Navigate**.
  - After a stop: **Navigate to next stop?**, which opens navigation after 5 seconds unless you tap **No**.
  - **Undo** brings back the stop you just completed. **Stops** lists the stops still to do; tap one to navigate there.
  - Phone and car share one list, so a stop ticked off in the car is ticked off on the phone.
  - In the car, navigation opens in Android Auto's default navigation app.
- Adding and dragging stops stays on the phone (Android Auto doesn't allow typing or dragging).
- Sideloaded apps only appear in Android Auto with **Unknown sources** turned on in its developer settings.

## v0.2 — 2026-11-04

- Stops stay in the order you add them, until you move them.
- **Hold and drag:** hold a stop for half a second and it lifts (with a buzz). Drag it anywhere in
  the list and let go. The stops in between shift up or down, and the numbers update.
- **Optimize** now sorts the stops still to do nearest to farthest from where you are, by road
  distance (straight-line when there's no signal). It needs location turned on.
- "Make next stop", "Move up" and "Move down" are gone from the stop menu; dragging replaces them.
- The full route solver from v0.1 is kept in the code, switched off in `Features.kt`.

## v0.1 — 2026-11-03

First build: the routing loop.

- Add stops by typing an address (or pasting lat,lng or a Google Maps link). New stops always go
  on the end of the list, in the order you add them. Tick "Pickup" to mark a pickup.
- **Optimize** re-orders the stops still to do, starting from where you are. It uses road drive
  times when there is signal and straight-line estimates when there isn't. It never runs by itself.
- The next stop is shown in large type with one big **Delivery completed?** (or **Picked up?**) button.
  The stop is struck out into the Done list, where **Restore** puts it back.
- After each stop, **Navigate to next stop?** counts down from 5 and then opens Google Maps (or Waze)
  already driving to it. Tap **No** to stay in Truckie.
- Tap any stop to navigate there, make it next, move it up or down, switch pickup/delivery, or remove it.
- Menu: choose Google Maps or Waze, check for updates, clear all stops for a new day.
- On launch, Truckie checks GitHub for a newer release and offers **Update now / Later**.
