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
