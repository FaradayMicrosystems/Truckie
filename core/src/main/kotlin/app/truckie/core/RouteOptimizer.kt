package app.truckie.core

/**
 * Finds a fast order to visit every stop once, starting from a fixed point and ending anywhere.
 *
 * Method: up to [EXACT_LIMIT] stops are solved exactly (Held-Karp). Above that, cheapest
 * insertion and nearest neighbour build first orders, then 2-opt (reverse a stretch) and or-opt
 * (move a run of 1 to 3 stops) improve them, restarted from a few shuffled orders; the best wins.
 * Routes are scored on the full grid every time, so one-way streets (A to B slower than B to A)
 * are handled correctly. About 30 stops solve in well under a second.
 */
object RouteOptimizer {

    /**
     * @param matrix travel cost (seconds) from row to column. Index 0 is the start (your position
     *   or the stop you are at); 1..n-1 are the stops to order.
     * @return the stop indices 1..n-1 in visiting order.
     */
    fun solve(matrix: Array<DoubleArray>): List<Int> {
        val n = matrix.size
        require(matrix.all { it.size == n }) { "Matrix must be square" }
        if (n <= 2) return (1 until n).toList()
        if (n - 1 <= EXACT_LIMIT) return exact(matrix)
        return heuristic(matrix)
    }

    /** Stop counts up to this are solved exactly. */
    const val EXACT_LIMIT = 12
    private const val RESTARTS = 60

    internal fun heuristic(matrix: Array<DoubleArray>): List<Int> {
        val rnd = kotlin.random.Random(42) // fixed seed: the same stops always give the same order
        val starts = mutableListOf(cheapestInsertion(matrix), nearestNeighbour(matrix))
        repeat(RESTARTS) { starts += (listOf(0) + (1 until matrix.size).shuffled(rnd)).toMutableList() }
        return starts.map { improve(it, matrix) }.minBy { cost(it, matrix) }.drop(1)
    }

    /** Held-Karp dynamic programming over subsets: the true best open path from index 0. */
    internal fun exact(m: Array<DoubleArray>): List<Int> {
        val k = m.size - 1 // stops 1..k map to bits 0..k-1
        val full = (1 shl k) - 1
        val dp = Array(1 shl k) { DoubleArray(k) { Double.MAX_VALUE } }
        val parent = Array(1 shl k) { IntArray(k) { -1 } }
        for (j in 0 until k) dp[1 shl j][j] = m[0][j + 1]
        for (mask in 1..full) {
            for (j in 0 until k) {
                val cur = dp[mask][j]
                if (cur == Double.MAX_VALUE || mask and (1 shl j) == 0) continue
                for (nx in 0 until k) {
                    if (mask and (1 shl nx) != 0) continue
                    val nm = mask or (1 shl nx)
                    val c = cur + m[j + 1][nx + 1]
                    if (c < dp[nm][nx]) { dp[nm][nx] = c; parent[nm][nx] = j }
                }
            }
        }
        var last = (0 until k).minBy { dp[full][it] }
        var mask = full
        val rev = mutableListOf<Int>()
        while (last >= 0) {
            rev += last + 1
            val p = parent[mask][last]
            mask = mask and (1 shl last).inv()
            last = p
        }
        return rev.asReversed()
    }

    /** Total cost of a path that starts at path[0]. */
    fun cost(path: List<Int>, m: Array<DoubleArray>): Double {
        var c = 0.0
        for (k in 1 until path.size) c += m[path[k - 1]][path[k]]
        return c
    }

    private fun nearestNeighbour(m: Array<DoubleArray>): MutableList<Int> {
        val left = (1 until m.size).toMutableSet()
        val path = mutableListOf(0)
        while (left.isNotEmpty()) {
            val here = path.last()
            val next = left.minBy { m[here][it] }
            path += next
            left -= next
        }
        return path
    }

    private fun cheapestInsertion(m: Array<DoubleArray>): MutableList<Int> {
        val path = mutableListOf(0)
        val left = (1 until m.size).toMutableSet()
        while (left.isNotEmpty()) {
            var bestStop = -1
            var bestPos = -1
            var bestAdd = Double.MAX_VALUE
            for (s in left) {
                for (pos in 1..path.size) {
                    val prev = path[pos - 1]
                    val add = if (pos == path.size) m[prev][s]
                    else m[prev][s] + m[s][path[pos]] - m[prev][path[pos]]
                    if (add < bestAdd) { bestAdd = add; bestStop = s; bestPos = pos }
                }
            }
            path.add(bestPos, bestStop)
            left -= bestStop
        }
        return path
    }

    private fun improve(start: MutableList<Int>, m: Array<DoubleArray>): MutableList<Int> {
        var path = start
        var bestCost = cost(path, m)
        val eps = 1e-9
        var improved = true
        while (improved) {
            improved = false
            // 2-opt: reverse path[i..j]; index 0 (the start) never moves.
            for (i in 1 until path.size - 1) {
                for (j in i + 1 until path.size) {
                    val cand = ArrayList<Int>(path.size)
                    cand.addAll(path.subList(0, i))
                    cand.addAll(path.subList(i, j + 1).asReversed())
                    cand.addAll(path.subList(j + 1, path.size))
                    val c = cost(cand, m)
                    if (c < bestCost - eps) { path = cand; bestCost = c; improved = true }
                }
            }
            // Or-opt: move a run of 1..3 stops to another place.
            for (len in 1..3) {
                for (i in 1..path.size - len) {
                    val seg = path.subList(i, i + len).toList()
                    val rest = path.subList(0, i) + path.subList(i + len, path.size)
                    for (pos in 1..rest.size) {
                        if (pos == i) continue
                        val cand = ArrayList<Int>(path.size)
                        cand.addAll(rest.subList(0, pos))
                        cand.addAll(seg)
                        cand.addAll(rest.subList(pos, rest.size))
                        val c = cost(cand, m)
                        if (c < bestCost - eps) { path = cand; bestCost = c; improved = true; break }
                    }
                    if (improved) break
                }
                if (improved) break
            }
        }
        return path
    }
}
