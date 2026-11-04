package app.truckie

/**
 * Switches for features that are built but not turned on yet.
 * Flip to true to bring a feature back; the code behind it is kept and still tested.
 */
object Features {
    /** Optimize button and route re-ordering. Off in v0.1.1: stops stay in the order they were added. */
    const val OPTIMIZE = false

    /** "Make next stop", "Move up" and "Move down" on a stop. Off for the same reason. */
    const val MANUAL_REORDER = false
}
