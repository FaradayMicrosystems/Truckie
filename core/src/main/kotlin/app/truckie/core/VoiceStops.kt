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
 */
object VoiceStops {
    private val SPLIT = Regex(
        """(?i)(?:[.;\n]+|\s*\b(?:and\s+then|then|next(?:\s+stop)?|new\s+stop)\b[\s,:]*)""",
    )
    private val PICKUP = Regex("""(?i)^(?:a\s+)?pick\s*-?\s*up\b[\s,:]*(?:(?:at|from)\b)?\s*""")
    private val DELIVERY = Regex("""(?i)^(?:a\s+)?deliver(?:y|ing)?\b[\s,:]*(?:(?:to|at)\b)?\s*""")
    private val FILLER = Regex("""(?i)^(?:(?:first|okay|ok|um|uh|right|so|number one)\b[\s,:]*)+""")

    fun split(transcript: String): List<SpokenStop> =
        transcript.split(SPLIT).mapNotNull { part ->
            var t = part.trim().trim(',', ' ')
            t = FILLER.replace(t, "")
            val type = if (PICKUP.containsMatchIn(t)) StopType.PICKUP else StopType.DELIVERY
            t = PICKUP.replace(t, "")
            t = DELIVERY.replace(t, "").trim().trim(',', ' ')
            // A lone word ("okay", "done") is not an address.
            if (t.length < 3 || t.equals("done", ignoreCase = true) || t.equals("that's it", ignoreCase = true)) null
            else SpokenStop(t, type)
        }
}
