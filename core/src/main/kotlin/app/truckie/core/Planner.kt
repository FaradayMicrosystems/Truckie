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

    /**
     * Optimize as used in v0.2: open stops sorted nearest to farthest from where you are.
     * [distances] holds the distance from you to each open stop, in the same order as [open].
     * Stops at the same distance keep the order they were added.
     */
    fun nearestFirst(open: List<Stop>, distances: DoubleArray): List<Long> {
        require(distances.size == open.size) { "One distance per open stop" }
        return open.indices.sortedBy { distances[it] }.map { open[it].id }
    }

    /** Straight-line distance from [here] to each open stop, for when there is no road data. */
    fun straightLineDistances(open: List<Stop>, here: LatLng): DoubleArray =
        DoubleArray(open.size) { Geo.haversineMeters(here, open[it].location) }
}
