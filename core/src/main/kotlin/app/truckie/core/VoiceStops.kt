package app.truckie.core

/** One stop heard in a voice list, before it is looked up. */
data class SpokenStop(val text: String, val type: StopType = StopType.DELIVERY)

/**
 * v0.9.0: several stops said in one go, e.g.
 * "12 Smith Street Newtown next 40 King Street Penrith then pickup 5 Depot Road".
 *
 * Stops are split on "next", "next stop", "then", "and then", "new stop", full stops,
 * semicolons and new lines. Not on plain "and" or commas: addresses carry those
 * ("Smith and Jones Road", "12 Smith St, Newtown"), and not on "stop" ("Truck Stop Lane"). A stop that starts with "pickup" / "pick up"
 * is a pickup. Order is kept exactly as spoken.
 *
 * v0.12.0: "pick up" / "pickup" / "pick-up" / "picked up" and "delivery" / "deliver" / "deliver to" /
 * "drop off" / "drop-off" also start a new stop AND tag it, so
 * "pick up 258 Leitchs Road Brendale delivery 207 Todds Road Lawnton" is two stops: a pickup, then a delivery.
 */
object VoiceStops {
    private val SPLIT = Regex(
        """(?i)(?:[.;\n]+|\s*\b(?:and\s+then|then|next(?:\s+stop)?|new\s+stop)\b[\s,:]*)""",
    )
    // The words that start a tagged stop, as voice typing tends to write them.
    private const val PICKUP_WORD = """pick(?:ed)?\s*-?\s*ups?"""
    private const val DELIVERY_WORD = """(?:deliver(?:y|ies|ing|ed)?|drop\s*-?\s*off)"""
    // v0.12.0: split just before a pickup/delivery word (zero width, so the word stays for tagging).
    private val SPLIT_BEFORE_TAG = Regex("""(?i)(?<=\S)\s+(?=(?:$PICKUP_WORD|$DELIVERY_WORD)\b)""")
    private val PICKUP = Regex("""(?i)^(?:a\s+)?$PICKUP_WORD\b[\s,:.-]*(?:(?:at|from)\b)?\s*""")
    private val DELIVERY = Regex("""(?i)^(?:a\s+)?$DELIVERY_WORD\b[\s,:.-]*(?:(?:to|at)\b)?\s*""")
    // "…Brendale and deliver to…" leaves a dangling "and" on the stop before.
    // and "…St a delivery to…" a dangling lower-case "a" (a capital A is a block or unit letter: "Block A").
    private val TRAILING_JOIN = Regex("""(?:[\s,]+(?i:and|then)|\s+a)$""")
    private val FILLER = Regex("""(?i)^(?:(?:first|okay|ok|um|uh|right|so|number one)\b[\s,:]*)+""")

    /** v0.10.0: what one voice turn said: its stops, and whether the driver said they're finished. */
    data class Heard(val stops: List<SpokenStop>, val finished: Boolean)

    // "…12 Smith St, done" ends the session after adding. A bare "stop" only counts on its own
    // (addresses like "Truck Stop Lane" end in it), so it isn't in the trailing list.
    private val TRAILING_FINISH = Regex("""(?i)[\s,.]*\b(?:that[’']?s\s+it|that[’']?s\s+all|all\s+done|i[’']?m\s+done|done|finish(?:ed)?)[\s.!]*$""")
    private val ONLY_FINISH = Regex("""(?i)^\W*(?:stop|end|cancel|done|finish(?:ed)?|that[’']?s\s+it|that[’']?s\s+all|all\s+done|i[’']?m\s+done)\W*$""")

    fun hear(transcript: String): Heard {
        if (ONLY_FINISH.matches(transcript)) return Heard(emptyList(), finished = true)
        val finished = TRAILING_FINISH.containsMatchIn(transcript)
        val rest = if (finished) TRAILING_FINISH.replace(transcript, "") else transcript
        return Heard(split(rest), finished)
    }

    fun split(transcript: String): List<SpokenStop> =
        transcript.split(SPLIT).flatMap { it.split(SPLIT_BEFORE_TAG) }.mapNotNull { part ->
            var t = part.trim().trim(',', ' ')
            t = FILLER.replace(t, "")
            val type = if (PICKUP.containsMatchIn(t)) StopType.PICKUP else StopType.DELIVERY
            t = PICKUP.replace(t, "")
            t = DELIVERY.replace(t, "").trim().trim(',', ' ')
            t = TRAILING_JOIN.replace(t, "").trim().trim(',', ' ')
            // A lone word ("okay", "done") is not an address.
            if (t.length < 3 || ONLY_FINISH.matches(t)) null
            else SpokenStop(t, type)
        }
}
