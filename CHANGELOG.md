# Changelog

## v0.1.1 — 2026-11-04

- Stops now always stay in the order you add them.
- The Optimize button is hidden and does nothing. "Make next stop", "Move up" and "Move down" are
  hidden too. The code is kept behind switches in `Features.kt` for a later version.

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
