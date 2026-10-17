package com.acewood.synapse.logic

/** Runtime-discovered HA helpers used by the intercom script. No helper entity ids are guessed. */
data class IntercomTarget(
    val roomSelectId: String,
    val scriptId: String,
    val messageInputId: String?,
    val rooms: List<String>,
) {
    val defaultRoom: String? get() = rooms.firstOrNull { !isMasterBedroom(it) } ?: rooms.firstOrNull()
    fun isMasterBedroom(room: String): Boolean = Intercom.isMasterBedroom(room)
}

object Intercom {
    fun resolve(cache: EntityCache?): IntercomTarget? {
        val entities = cache?.all().orEmpty()
        val select = entities.firstOrNull { it.entityId == "input_select.intercom_room" }
            ?: return null
        val script = entities.firstOrNull { it.entityId == "script.intercom_send" }
            ?: return null
        val options = (select.attributes["options"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
        val message = entities.filter { it.domain == "input_text" }
            .map { it to messageScore(it) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first?.entityId
        return IntercomTarget(select.entityId, script.entityId, message, options)
    }

    private fun messageScore(e: Entity): Int {
        val id = e.entityId.lowercase()
        val name = e.friendlyName.lowercase()
        return when {
            id == "input_text.intercom_message" -> 100
            id.contains("intercom") -> 80
            id.contains("announce") -> 70
            name.contains("intercom") -> 60
            name.contains("announce") || name.contains("message") -> 50
            else -> 0
        }
    }

    fun isMasterBedroom(room: String): Boolean {
        val s = room.lowercase().replace('_', ' ').trim()
        return s.contains("master bedroom") || s == "master" || s.contains("master bed")
    }
}
