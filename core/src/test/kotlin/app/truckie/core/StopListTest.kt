package app.truckie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StopListTest {
    private val p = LatLng(-33.87, 151.21)
    private fun list(vararg names: String) = names.fold(StopList()) { l, n -> l.add(n, p) }
    private fun StopList.names() = open.map { it.address }

    @Test fun newStopsGoOnTheEnd() {
        val l = list("A", "B").withOpenOrder(listOf(2, 1)).add("C", p)
        assertEquals(listOf("B", "A", "C"), l.names())
    }

    @Test fun completeAndRestore() {
        var l = list("A", "B", "C").complete(1, now = 100)
        assertEquals("B", l.next?.address)
        assertEquals(listOf("A"), l.done.map { it.address })
        l = l.restore(1)
        assertEquals("A", l.next?.address)
        assertEquals(listOf("A", "B", "C"), l.names())
    }

    @Test fun doneListIsNewestFirst() {
        val l = list("A", "B").complete(1, 100).complete(2, 200)
        assertEquals(listOf("B", "A"), l.done.map { it.address })
        assertNull(l.next)
    }

    @Test fun makeNextAndMove() {
        var l = list("A", "B", "C").complete(1, 1)
        l = l.makeNext(3)
        assertEquals(listOf("C", "B"), l.names())
        l = l.move(3, +1)
        assertEquals(listOf("B", "C"), l.names())
        assertEquals(l, l.move(3, +1))
    }

    @Test fun dragMovesIntoTargetSlot() {
        var l = list("A", "B", "C", "D", "E")
        l = l.moveTo(id = 1, targetId = 4) // drag A down onto D
        assertEquals(listOf("B", "C", "D", "A", "E"), l.names())
        l = l.moveTo(id = 5, targetId = 2) // drag E up onto B
        assertEquals(listOf("E", "B", "C", "D", "A"), l.names())
        assertEquals(l, l.moveTo(3, 3))
    }

    @Test fun dragIgnoresDoneStopsAndStillAppends() {
        var l = list("A", "B", "C").complete(1, 10)
        assertEquals(l, l.moveTo(2, 1)) // A is done: not a drop target
        l = l.moveTo(3, 2).add("D", p)
        assertEquals(listOf("C", "B", "D"), l.names())
        assertEquals(listOf("A"), l.done.map { it.address })
    }

    @Test fun optimizeSortsNearestToFarthest() {
        val here = LatLng(-33.0, 151.0)
        val l = StopList()
            .add("far", LatLng(-33.0, 151.10))
            .add("near", LatLng(-33.0, 151.01))
            .add("mid", LatLng(-33.0, 151.05))
            .add("mid again", LatLng(-33.0, 151.05))
        val order = Planner.nearestFirst(l.open, Planner.straightLineDistances(l.open, here))
        assertEquals(listOf(2L, 3L, 4L, 1L), order) // ties keep the order added
        assertEquals(listOf("near", "mid", "mid again", "far"), l.withOpenOrder(order).names())
    }

    @Test fun undoOptimizeRestoresAddedOrder() {
        var l = list("A", "B", "C", "D").complete(2, 10)
        l = l.withOpenOrder(listOf(4, 1, 3)).moveTo(3, 4)
        assertEquals(listOf("C", "D", "A"), l.names())
        l = l.inAddedOrder()
        assertEquals(listOf("A", "C", "D"), l.names())
        assertEquals(listOf("B"), l.done.map { it.address })
    }

    @Test fun optimizeChainsNearestNext() {
        // Star sort would visit by distance from here: A(1), B(2), C(3). The chain goes
        // here -> A, then from A the closest is C, then B.
        val l = list("A", "B", "C")
        val m = arrayOf(
            //            here   A     B     C
            doubleArrayOf(0.0, 1.0, 2.0, 3.0), // here
            doubleArrayOf(1.0, 0.0, 9.0, 1.5), // A
            doubleArrayOf(2.0, 9.0, 0.0, 9.0), // B
            doubleArrayOf(3.0, 1.5, 9.0, 0.0), // C
        )
        assertEquals(listOf(1L, 3L, 2L), Planner.nearestNextOrder(l.open, m))
    }

    @Test fun nearestNextTiesKeepAddedOrder() {
        val l = list("A", "B")
        val m = arrayOf(doubleArrayOf(0.0, 5.0, 5.0), doubleArrayOf(5.0, 0.0, 1.0), doubleArrayOf(5.0, 1.0, 0.0))
        assertEquals(listOf(1L, 2L), Planner.nearestNextOrder(l.open, m))
    }

    @Test fun jsonRoundTrip() {
        val l = list("A", "B").complete(1, 5).setType(2, StopType.PICKUP)
        assertEquals(l, StopList.fromJson(l.toJson()))
    }

    @Test fun plannerKeepsNextStopFirstWithoutLocation() {
        val l = StopList()
            .add("far", LatLng(-33.0, 151.10))
            .add("near", LatLng(-33.0, 151.01))
            .add("start", LatLng(-33.0, 151.00))
            .makeNext(3)
        val pts = Planner.gridPoints(l.open, null)
        val order = Planner.optimizedOrder(l.open, null, Geo.estimateMatrix(pts))
        assertEquals(listOf(3L, 2L, 1L), order)
    }

    @Test fun plannerStartsFromCurrentPosition() {
        val l = StopList().add("far", LatLng(-33.0, 151.10)).add("near", LatLng(-33.0, 151.01))
        val here = LatLng(-33.0, 151.0)
        val order = Planner.optimizedOrder(l.open, here, Geo.estimateMatrix(Planner.gridPoints(l.open, here)))
        assertEquals(listOf(2L, 1L), order)
    }
}
