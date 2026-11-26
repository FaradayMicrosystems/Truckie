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

    private fun tagged(s: String) = VoiceStops.split(s).map { it.type to it.text }

    @Test fun pickupAndDeliveryWordsSplitAndTag() {
        assertEquals(
            listOf(StopType.PICKUP to "258 Leitchs Road Brendale", StopType.DELIVERY to "207 Todds Road Lawnton"),
            tagged("pick up 258 Leitchs Road Brendale delivery 207 Todds Road Lawnton"),
        )
        assertEquals(
            listOf(StopType.PICKUP to "258 Leitchs Road, Brendale", StopType.DELIVERY to "207 Todds Road, Lawnton"),
            tagged("Pick up 258 Leitchs Road, Brendale, deliver 207 Todds Road, Lawnton"),
        )
        assertEquals(
            listOf(StopType.PICKUP to "258 Leitchs Road Brendale", StopType.DELIVERY to "207 Todds Road Lawnton"),
            tagged("Pick-up 258 Leitchs Road Brendale and deliver to 207 Todds Road Lawnton"),
        )
    }

    @Test fun speechRecognizerVariants() {
        assertEquals(
            listOf(
                StopType.PICKUP to "1 A St",
                StopType.DELIVERY to "2 B St",
                StopType.PICKUP to "3 C St",
                StopType.DELIVERY to "4 D St",
                StopType.PICKUP to "5 E St",
                StopType.DELIVERY to "6 F St",
            ),
            tagged("pickup 1 A St drop off 2 B St picked up 3 C St a delivery to 4 D St Pickup from 5 E St drop-off at 6 F St"),
        )
    }

    @Test fun tagWordsMixWithNext() {
        assertEquals(
            listOf(StopType.PICKUP to "5 Depot Rd", StopType.DELIVERY to "9 Main St", StopType.DELIVERY to "12 King St"),
            tagged("pick up 5 Depot Rd next delivery 9 Main St then 12 King St"),
        )
    }

    @Test fun untaggedStopThenTaggedOne() {
        assertEquals(
            listOf(StopType.DELIVERY to "10 Smith St Newtown", StopType.PICKUP to "4 Mill Rd"),
            tagged("10 Smith St Newtown pickup 4 Mill Rd"),
        )
    }

    @Test fun keepsBlockLetters() {
        assertEquals(listOf("Block A", "2 B St"), texts("Block A delivery 2 B St"))
    }

    @Test fun hearWithTagsAndDone() {
        val h = VoiceStops.hear("pick up 258 Leitchs Road Brendale delivery 207 Todds Road Lawnton, done")
        assertEquals(listOf(StopType.PICKUP, StopType.DELIVERY), h.stops.map { it.type })
        assertEquals(true, h.finished)
    }
}
