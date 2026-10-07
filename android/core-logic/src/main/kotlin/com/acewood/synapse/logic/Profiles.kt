package com.acewood.synapse.logic

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Multi-user profiles for the panel. The person using the remote is chosen by the PIN they enter
 * ("code set at setup"). Each role has its own capabilities (mapped to the Level 0/1/2 security
 * model) and its own layout/orientation. Admin (Mason) can do everything incl. managing profiles.
 *
 * PINs are hashed with one device-wide salt, so identical PINs collide (lets us reject duplicates
 * at setup) while never storing the clear PIN. Threat model is shoulder-surfing on a kiosk, not
 * offline cracking, so a shared salt is the right trade-off and keeps PIN->profile lookup simple.
 */
enum class Role(
    /** Highest Action-Authorization level reachable without extra auth. 0=lights/media/temp,
     *  1=cameras/automation/device mgmt, 2=locks/garage/alarm/credentials. */
    val maxLevel: Int,
    val canEditSettings: Boolean,
    val canManageProfiles: Boolean,
    /** Simplified, big-button UI (child/guest). */
    val simplified: Boolean,
) {
    ADMIN(2, true, true, false),
    USER(2, false, false, false),
    CHILD(0, false, false, true),
    GUEST(0, false, false, true);

    fun canDoAction(level: Int): Boolean = level in 0..maxLevel

    companion object {
        fun from(s: String?): Role = entries.firstOrNull { it.name.equals(s?.trim(), true) } ?: GUEST
    }
}

data class Layout(
    val orientation: String = "auto",     // "portrait" | "landscape" | "auto"
    val homeApps: List<String> = emptyList(),
    val rooms: List<String> = emptyList(),
    val accent: String = "#3ab4ff",
    val tileScale: Float = 1.0f,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "orientation" to orientation, "home_apps" to homeApps, "rooms" to rooms,
        "accent" to accent, "tile_scale" to tileScale.toDouble(),
    )
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(m: Map<String, Any?>?): Layout {
            if (m == null) return Layout()
            fun strs(k: String) = (m[k] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } } ?: emptyList()
            return Layout(
                orientation = (m["orientation"] as? String)?.lowercase()?.takeIf { it in setOf("portrait", "landscape", "auto") } ?: "auto",
                homeApps = strs("home_apps"), rooms = strs("rooms"),
                accent = (m["accent"] as? String) ?: "#3ab4ff",
                tileScale = (m["tile_scale"] as? Double)?.toFloat() ?: 1.0f,
            )
        }
    }
}

object PinHash {
    private val rnd = SecureRandom()
    fun newSalt(): String = ByteArray(12).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    fun hash(pin: String, saltHex: String): String =
        MessageDigest.getInstance("SHA-256").digest((saltHex + "·" + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}

data class Profile(
    val id: String,
    val name: String,
    val role: Role,
    /** SHA-256 of (deviceSalt · pin); empty = no PIN (guest only). */
    val pinHash: String,
    val layout: Layout = Layout(),
) {
    val simplified get() = role.simplified
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "name" to name, "role" to role.name.lowercase(), "pin_hash" to pinHash, "layout" to layout.toMap(),
    )
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(m: Map<String, Any?>): Profile = Profile(
            id = (m["id"] as? String) ?: EntityIds.slug((m["name"] as? String) ?: "user"),
            name = (m["name"] as? String) ?: "User",
            role = Role.from(m["role"] as? String),
            pinHash = (m["pin_hash"] as? String) ?: "",
            layout = Layout.fromJson(m["layout"] as? Map<String, Any?>),
        )
    }
}

class Profiles(
    /** device-wide salt for all profile PINs */
    val salt: String,
    val list: List<Profile>,
    val defaultId: String? = null,
) {
    val admin: Profile? get() = list.firstOrNull { it.role == Role.ADMIN }
    val defaultProfile: Profile?
        get() = list.firstOrNull { it.id == defaultId } ?: list.firstOrNull { it.role == Role.GUEST } ?: admin

    /** The profile whose PIN was entered, or null. */
    fun resolve(pin: String): Profile? {
        if (pin.isEmpty()) return null
        val h = PinHash.hash(pin, salt)
        return list.firstOrNull { it.pinHash.isNotEmpty() && PinHash.equal(it.pinHash, h) }
    }

    fun byId(id: String?): Profile? = list.firstOrNull { it.id == id }

    fun validate(): List<String> = buildList {
        if (list.none { it.role == Role.ADMIN }) add("need at least one admin profile")
        if (list.map { it.id }.toSet().size != list.size) add("duplicate profile ids")
        val pins = list.filter { it.pinHash.isNotEmpty() }.map { it.pinHash }
        if (pins.toSet().size != pins.size) add("two profiles share a PIN")
        if (list.any { it.pinHash.isEmpty() && it.role != Role.GUEST }) add("only a guest profile may have no PIN")
    }

    fun toJson(): String =
        Json.write(linkedMapOf("salt" to salt, "default_id" to defaultProfile?.id, "profiles" to list.map { it.toMap() }))

    /** Add/replace a profile from a clear PIN (hashes it with the device salt). */
    fun withProfile(id: String, name: String, role: Role, pin: String, layout: Layout = Layout()): Profiles {
        val p = Profile(id, name, role, if (pin.isEmpty()) "" else PinHash.hash(pin, salt), layout)
        return Profiles(salt, list.filterNot { it.id == id } + p, defaultId)
    }
    /** Upsert used by the admin UI; null PIN means keep the existing hash, never clear it accidentally. */
    fun upsert(id: String, name: String, role: Role, pin: String?, layout: Layout = Layout()): Profiles {
        val old = list.firstOrNull { it.id == id }
        val hash = when {
            pin != null && pin.isNotEmpty() -> PinHash.hash(pin, salt)
            pin != null && role == Role.GUEST -> ""
            old != null -> old.pinHash
            else -> ""
        }
        return Profiles(salt, list.filterNot { it.id == id } + Profile(id, name, role, hash, layout), defaultId)
    }
    fun without(id: String): Profiles = Profiles(salt, list.filterNot { it.id == id }, if (defaultId == id) null else defaultId)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): Profiles {
            val m = Json.parseObject(text)
            val profs = (m["profiles"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(Profile::fromJson) } ?: emptyList()
            return Profiles((m["salt"] as? String) ?: "", profs, m["default_id"] as? String)
        }

        /** Starter set from the setup codes: admin + guest. More added later in settings. */
        fun starter(adminName: String, adminPin: String, guestPin: String = ""): Profiles {
            val salt = PinHash.newSalt()
            fun h(pin: String) = if (pin.isEmpty()) "" else PinHash.hash(pin, salt)
            return Profiles(salt, listOf(
                Profile("admin", adminName, Role.ADMIN, h(adminPin)),
                Profile("guest", "Guest", Role.GUEST, h(guestPin), Layout(tileScale = 1.25f)),
            ), defaultId = "guest")
        }
    }
}
