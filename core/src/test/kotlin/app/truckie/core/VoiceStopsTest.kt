package app.truckie.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceStopsTest {
    private fun texts(s: String) = VoiceStops.split(s).map { it.text }

    @Test fun splitsOnNextAndThenInSpokenOrder() {
        assertEquals(
            listOf("12 Smith Street Newtown", "40 King Street Penrith", "5 Depot Road Erskine Park"),
            texts("12 Smith Street Newtown next 40 King Street Penrith then 5 Depot Road Erskine Park"),
        )
        assertEquals(
            listOf("1 George St Sydney", "2 Pitt St Sydney"),
            texts("1 George St Sydney, next stop, 2 Pitt St Sydney"),
        )
        assertEquals(listOf("A Rd", "B Rd", "C Rd").map { "10 $it" }, texts("10 A Rd. 10 B Rd and then 10 C Rd"))
    }

    @Test fun keepsAndAndCommasInsideAddresses() {
        assertEquals(
            listOf("3 Smith and Jones Road, Blacktown", "Unit 4, 8 Wattle Avenue"),
            texts("3 Smith and Jones Road, Blacktown next Unit 4, 8 Wattle Avenue"),
        )
    }

    @Test fun nextInsideAWordDoesNotSplit() {
        assertEquals(listOf("7 Nextdoor Lane Thenford"), texts("7 Nextdoor Lane Thenford"))
    }

    @Test fun stopAndAtInsideNamesStay() {
        assertEquals(listOf("2 Truck Stop Lane", "Atkinson Street"), texts("2 Truck Stop Lane next deliver Atkinson Street"))
    }

    @Test fun autoPunctuationFromVoiceTyping() {
        assertEquals(
            listOf("12 Smith Street, Newtown", "40 King Street"),
            texts("12 Smith Street, Newtown. Next, 40 King Street."),
        )
    }

    @Test fun pickupPrefixMakesAPickup() {
        val r = VoiceStops.split("pickup from 5 Depot Road next deliver to 9 Main Street next pick up 2 Mill Rd")
        assertEquals(listOf("5 Depot Road", "9 Main Street", "2 Mill Rd"), r.map { it.text })
        assertEquals(listOf(StopType.PICKUP, StopType.DELIVERY, StopType.PICKUP), r.map { it.type })
    }

    @Test fun dropsFillerAndEmptyBits() {
        assertEquals(listOf("12 Smith St"), texts("okay first 12 Smith St next done"))
        assertEquals(emptyList(), texts("next then"))
        assertEquals(emptyList(), texts(""))
    }

    @Test fun finishWordsEndTheSession() {
        assertEquals(VoiceStops.Heard(emptyList(), true), VoiceStops.hear("Done."))
        assertEquals(VoiceStops.Heard(emptyList(), true), VoiceStops.hear("stop"))
        assertEquals(VoiceStops.Heard(emptyList(), true), VoiceStops.hear("That's it"))
        val last = VoiceStops.hear("12 Smith St Newtown, that's it")
        assertEquals(listOf("12 Smith St Newtown"), last.stops.map { it.text })
        assertEquals(true, last.finished)
        val more = VoiceStops.hear("2 Truck Stop Lane")
        assertEquals(listOf("2 Truck Stop Lane"), more.stops.map { it.text })
        assertEquals(false, more.finished)
    }
}
