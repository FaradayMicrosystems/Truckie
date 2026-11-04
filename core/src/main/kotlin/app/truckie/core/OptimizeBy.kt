package app.truckie.core

/** What Optimize compares when it picks the nearest next stop. */
enum class OptimizeBy(val label: String, val short: String) {
    /** Fewest kilometres by road. Favours shortcuts. */
    ROAD_DISTANCE("Optimize by road distance", "by road distance"),

    /** Fewest minutes by road. Favours faster roads. */
    DRIVE_TIME("Optimize by drive time", "by drive time"),
}
