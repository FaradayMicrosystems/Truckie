package app.truckie.core

/** Turns the open stops plus where you are into an optimized driving order. */
object Planner {
    /**
     * Points to send for a travel-time grid: your position first (or, without one, the current
     * next stop, which then stays first), followed by the other open stops.
     */
    fun gridPoints(open: List<Stop>, here: LatLng?): List<LatLng> =
        if (here != null) listOf(here) + open.map { it.location } else open.map { it.location }

    /** Open stop ids in optimized order, given the grid for [gridPoints]. */
    fun optimizedOrder(open: List<Stop>, here: LatLng?, matrix: Array<DoubleArray>): List<Long> {
        if (open.size <= 1) return open.map { it.id }
        val order = RouteOptimizer.solve(matrix)
        return if (here != null) order.map { open[it - 1].id }
        else listOf(open[0].id) + order.map { open[it].id }
    }
}
