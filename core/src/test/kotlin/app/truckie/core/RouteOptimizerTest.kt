package app.truckie.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteOptimizerTest {
    private fun grid(points: List<LatLng>) = Geo.estimateMatrix(points)

    private fun bruteForce(m: Array<DoubleArray>): Double {
        var best = Double.MAX_VALUE
        fun go(path: MutableList<Int>, left: MutableSet<Int>) {
            if (left.isEmpty()) { best = minOf(best, RouteOptimizer.cost(path, m)); return }
            for (s in left.toList()) { path += s; left -= s; go(path, left); left += s; path.removeAt(path.lastIndex) }
        }
        go(mutableListOf(0), (1 until m.size).toMutableSet())
        return best
    }

    @Test fun visitsEveryStopOnce() {
        val rnd = Random(1)
        val pts = List(31) { LatLng(-33.9 + rnd.nextDouble() * 0.3, 151.0 + rnd.nextDouble() * 0.3) }
        val order = RouteOptimizer.solve(grid(pts))
        assertEquals((1 until 31).toList(), order.sorted())
    }

    @Test fun straightRoadIsDrivenInOrder() {
        // Start at the west end; stops given shuffled along one east-west road.
        val pts = listOf(LatLng(-33.0, 151.00), LatLng(-33.0, 151.04), LatLng(-33.0, 151.01), LatLng(-33.0, 151.03), LatLng(-33.0, 151.02))
        assertEquals(listOf(2, 4, 3, 1), RouteOptimizer.solve(grid(pts)))
    }

    @Test fun exactSolverMatchesBruteForce() {
        repeat(25) { seed ->
            val rnd = Random(seed)
            val pts = List(8) { LatLng(-37.8 + rnd.nextDouble() * 0.2, 144.9 + rnd.nextDouble() * 0.2) }
            val m = grid(pts)
            val got = RouteOptimizer.cost(listOf(0) + RouteOptimizer.solve(m), m)
            assertEquals(bruteForce(m), got, 1e-6, "seed $seed")
        }
    }

    @Test fun heuristicIsCloseToExact() {
        // The heuristic handles runs above the exact limit; check it against the exact answer at 12 stops.
        var worst = 1.0
        repeat(20) { seed ->
            val rnd = Random(100 + seed)
            val pts = List(13) { LatLng(-37.8 + rnd.nextDouble() * 0.2, 144.9 + rnd.nextDouble() * 0.2) }
            val m = grid(pts)
            val h = RouteOptimizer.cost(listOf(0) + RouteOptimizer.heuristic(m), m)
            val e = RouteOptimizer.cost(listOf(0) + RouteOptimizer.exact(m), m)
            worst = maxOf(worst, h / e)
        }
        assertTrue(worst <= 1.02, "worst heuristic/exact ratio $worst")
    }

    @Test fun respectsOneWayCosts() {
        // 0 -> 1 -> 2 is cheap, 0 -> 2 -> 1 is dear, even though the grid is not symmetric.
        val m = arrayOf(
            doubleArrayOf(0.0, 1.0, 1.0),
            doubleArrayOf(9.0, 0.0, 1.0),
            doubleArrayOf(9.0, 50.0, 0.0),
        )
        assertEquals(listOf(1, 2), RouteOptimizer.solve(m))
    }

    @Test fun thirtyStopsSolveQuickly() {
        val rnd = Random(7)
        val pts = List(31) { LatLng(-27.5 + rnd.nextDouble() * 0.4, 153.0 + rnd.nextDouble() * 0.4) }
        val m = grid(pts)
        val t0 = System.nanoTime()
        val order = RouteOptimizer.solve(m)
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue(ms < 2000, "took $ms ms")
        // Never worse than the order the stops were entered in.
        assertTrue(RouteOptimizer.cost(listOf(0) + order, m) <= RouteOptimizer.cost((0 until 31).toList(), m))
    }

    @Test fun tinyInputs() {
        assertEquals(emptyList(), RouteOptimizer.solve(arrayOf(doubleArrayOf(0.0))))
        assertEquals(listOf(1), RouteOptimizer.solve(arrayOf(doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 0.0))))
    }
}
