package app.truckie.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StopType { DELIVERY, PICKUP }

@Serializable
data class Stop(
    val id: Long,
    val address: String,
    val location: LatLng,
    val type: StopType = StopType.DELIVERY,
    /** Time the stop was marked done (epoch millis), or null while it is still to do. */
    val doneAt: Long? = null,
) {
    val isDone: Boolean get() = doneAt != null
}

/**
 * The day's stops in driving order. Done stops stay in the list (struck out) so they can be restored.
 * Every change returns a new StopList; nothing is changed in place.
 */
@Serializable
data class StopList(val stops: List<Stop> = emptyList(), val nextId: Long = 1) {

    val open: List<Stop> get() = stops.filter { !it.isDone }
    val done: List<Stop> get() = stops.filter { it.isDone }.sortedByDescending { it.doneAt }
    val next: Stop? get() = stops.firstOrNull { !it.isDone }

    /** New stops always go on the end, in the order they are added. Nothing else moves. */
    fun add(address: String, location: LatLng, type: StopType = StopType.DELIVERY): StopList =
        copy(stops = stops + Stop(nextId, address, location, type), nextId = nextId + 1)

    fun complete(id: Long, now: Long): StopList = update(id) { it.copy(doneAt = now) }

    /** Undo a completion: the stop goes back to its old place in the list. */
    fun restore(id: Long): StopList = update(id) { it.copy(doneAt = null) }

    fun remove(id: Long): StopList = copy(stops = stops.filterNot { it.id == id })

    fun setType(id: Long, type: StopType): StopList = update(id) { it.copy(type = type) }

    /** Moves an open stop to the front of the open stops, so it becomes the next stop. */
    fun makeNext(id: Long): StopList {
        val stop = stops.firstOrNull { it.id == id && !it.isDone } ?: return this
        val rest = stops.filterNot { it.id == id }
        val at = rest.indexOfFirst { !it.isDone }.let { if (it < 0) rest.size else it }
        return copy(stops = rest.take(at) + stop + rest.drop(at))
    }

    /** Moves an open stop one place up or down among the open stops. */
    fun move(id: Long, by: Int): StopList {
        val openIds = open.map { it.id }.toMutableList()
        val from = openIds.indexOf(id)
        if (from < 0) return this
        val to = (from + by).coerceIn(0, openIds.lastIndex)
        if (to == from) return this
        openIds.add(to, openIds.removeAt(from))
        return withOpenOrder(openIds)
    }

    /**
     * Drag and drop: moves an open stop into the slot another open stop holds now. Every stop
     * between them shifts one place, so the list stays in sequence. Done stops never move.
     */
    fun moveTo(id: Long, targetId: Long): StopList {
        val openIds = open.map { it.id }.toMutableList()
        val from = openIds.indexOf(id)
        val to = openIds.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return this
        openIds.add(to, openIds.removeAt(from))
        return withOpenOrder(openIds)
    }

    /** Puts the open stops in the given order. Done stops keep their places ahead of them. */
    fun withOpenOrder(openIds: List<Long>): StopList {
        val byId = open.associateBy { it.id }
        require(openIds.toSet() == byId.keys && openIds.size == byId.size) { "Order must list every open stop once" }
        return copy(stops = stops.filter { it.isDone } + openIds.map { byId.getValue(it) })
    }

    /**
     * "Undo optimize": puts the open stops back in the order they were added (ids grow with
     * each add). This also undoes any dragging. Done stops are untouched.
     */
    fun inAddedOrder(): StopList = withOpenOrder(open.map { it.id }.sorted())

    /** Clears the whole list for a new day. */
    fun cleared(): StopList = copy(stops = emptyList())

    private fun update(id: Long, f: (Stop) -> Stop) = copy(stops = stops.map { if (it.id == id) f(it) else it })

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): StopList = JSON.decodeFromString(serializer(), text)
    }
}
