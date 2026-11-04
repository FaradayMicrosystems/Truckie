package app.truckie

/**
 * Switches for features that are built but not all turned on.
 * The code behind each switch is kept and still tested.
 */
object Features {
    /** Shows the Optimize button. */
    const val OPTIMIZE = true

    /**
     * What Optimize does. false (v0.2): sort stops nearest to farthest from where you are.
     * true: the full route solver (shortest drive through every stop), kept for later.
     */
    const val ROUTE_SOLVER = false

    /**
     * The "Optimize by drive time" choice in the ⋮ menu. Off in v0.4.2: it gave bad orders on the road
     * (the free OSRM server has no traffic, so it chases highways). Off means Optimize always uses road distance.
     */
    const val OPTIMIZE_BY_DRIVE_TIME = false

    /** Hold a stop for half a second, then drag it to a new place in the list. */
    const val DRAG_REORDER = true

    /** "Make next stop", "Move up" and "Move down" in a stop's menu. Off: dragging replaces them. */
    const val MANUAL_REORDER = false

    /** How long to hold a stop before it lifts for dragging. */
    const val DRAG_HOLD_MILLIS = 500L
}
