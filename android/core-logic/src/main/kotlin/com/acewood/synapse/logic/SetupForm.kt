package com.acewood.synapse.logic

/**
 * First-run setup wizard rules (from ChatGPT's SetupWizardView, landed by Claude with its review fixes):
 *  - SAVE needs a successful "TEST HOME ASSISTANT" for the exact URL + token being saved. Editing
 *    either after a test makes the panel re-test (fingerprint check), so a typo can't slip through.
 *  - No hard-coded LAN default: the URL starts empty with an example hint.
 */
data class SetupInput(
    val ownerName: String,
    val nodeId: String,
    val room: String,
    val haUrl: String,
    val haToken: String,
    val pin: String,
    val confirmPin: String,
)

object SetupForm {
    const val URL_HINT = "http://homeassistant.local:8123"

    fun normalizeNodeId(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-')

    /** The URL+token pair a test proved; compared again at save time. */
    fun fingerprint(haUrl: String, haToken: String): String =
        (haUrl.trim().trimEnd('/') + "\u0000" + haToken.trim()).hashCode().toString(16)

    fun toConfig(i: SetupInput): NodeConfig = NodeConfig(
        nodeId = normalizeNodeId(i.nodeId),
        room = i.room.trim().ifBlank { "livingroom" },
        ownerName = i.ownerName.trim(),
        haUrl = i.haUrl.trim().trimEnd('/'),
        haToken = i.haToken.trim(),
        pin = i.pin,
    )

    /** First problem found, phrased for the person holding the panel; null = form is valid. */
    fun problem(i: SetupInput): String? {
        val cfg = toConfig(i)
        if (cfg.ownerName.isBlank()) return "Enter the name to show on the remote."
        if (cfg.nodeId.isBlank()) return "Enter a device ID (letters, numbers, - or _)."
        if (!(cfg.haUrl.startsWith("http://") || cfg.haUrl.startsWith("https://"))) return "Use a Home Assistant URL that starts with http:// or https://."
        if (cfg.haToken.length < 20) return "Paste a Home Assistant long-lived access token."
        if (i.pin.length < 4 || !i.pin.all { it.isDigit() }) return "The admin PIN must be at least 4 digits."
        if (i.pin != i.confirmPin) return "The PIN entries don't match."
        val errors = cfg.validate()
        return if (errors.isEmpty()) null else errors.joinToString(" · ")
    }

    /** Why SAVE is blocked, or null when it may proceed. */
    fun saveBlocker(i: SetupInput, verifiedFingerprint: String?): String? {
        problem(i)?.let { return it }
        if (verifiedFingerprint == null) return "Tap TEST HOME ASSISTANT first. Synapse only saves a connection that works."
        if (verifiedFingerprint != fingerprint(i.haUrl, i.haToken)) return "The URL or token changed since the test. Test again before saving."
        return null
    }

    /** Human result for the HTTP status of GET <url>/api/ with the token. */
    fun testMessage(httpCode: Int?): Pair<Boolean, String> = when (httpCode) {
        200 -> true to "Connected. Home Assistant accepted the token."
        401, 403 -> false to "Home Assistant rejected the token (HTTP $httpCode). Make a new long-lived token and paste it again."
        404 -> false to "That address answered, but it isn't Home Assistant's API (HTTP 404). Check the URL and port."
        null -> false to "Couldn't reach Home Assistant. Check Wi-Fi, the URL, and that HA is running."
        else -> false to "Home Assistant answered HTTP $httpCode. Check the URL and token."
    }
}
