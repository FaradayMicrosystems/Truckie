# Changelog

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
