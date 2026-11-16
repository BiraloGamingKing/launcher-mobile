package net.millida.launcher.core.account

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import net.millida.launcher.core.mojang.MojangJson
import java.io.IOException
import kotlin.math.ceil

/** The server asked to wait; [retryAfterSeconds] is its own figure, null when it named none. */
class RateLimited(message: String, val retryAfterSeconds: Int?) : IOException(message)

/** How long a 429 asks to wait: the `retryAfterSeconds` of its body, else the Retry-After header in seconds. */
object RetryAfter {

    const val MAX_SECONDS = 24 * 60 * 60

    fun seconds(body: String, header: String?): Int? = bounded(fromBody(body)) ?: bounded(header?.trim()?.toDoubleOrNull())

    private fun fromBody(body: String): Double? {
        val json = try {
            MojangJson.parseToJsonElement(body) as? JsonObject
        } catch (notJson: SerializationException) {
            null
        }
        return (json?.get("retryAfterSeconds") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    }

    private fun bounded(raw: Double?): Int? {
        if (raw == null || !raw.isFinite() || raw <= 0.0) return null
        return ceil(raw).coerceAtMost(MAX_SECONDS.toDouble()).toInt()
    }
}

/**
 * Says once that the sign-in wait cannot reach the server. Misses count only while the player looks
 * at the launcher: behind the browser the system cuts the network, and that is not worth a message.
 */
class LoginWaitWatch(private val threshold: Int = DEFAULT_THRESHOLD) {
    private var misses = 0
    private var told = false

    fun shouldTell(failure: IOException?, visible: Boolean): Boolean {
        if (failure == null) {
            misses = 0
            told = false
            return false
        }
        misses = if (visible) misses + 1 else 0
        if (told || misses < threshold) return false
        told = true
        return true
    }

    companion object {
        const val DEFAULT_THRESHOLD = 3
    }
}
