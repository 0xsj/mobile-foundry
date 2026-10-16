package dev.mobilefoundry.session

import dev.mobilefoundry.http.Entity
import dev.mobilefoundry.kernel.*
import kotlinx.serialization.json.*
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object GoSessionWire {
    class Tokens(val principal: Principal, val access: String, val refresh: String, val expiry: Long) {
        override fun toString() = "GoTokens(<redacted>)"
    }
    fun uuid(value: String) = value != "00000000-0000-0000-0000-000000000000" && value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    fun name(value: String) = value.isNotEmpty() && value.codePointCount(0, value.length) <= 80 && value == value.trim() &&
        value.codePoints().noneMatch { it in 0xD800..0xDFFF || Character.getType(it) == Character.CONTROL.toInt() }
    fun token(value: String, prefix: String) = value.length == 46 && value.matches(Regex("$prefix\\.[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]"))
    fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun version(value: JsonElement?): Long? {
        val n = (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
        return n.takeIf { it.isFinite() && it >= 1 && it <= 9_007_199_254_740_991.0 && it.toLong().toDouble() == it }?.toLong()
    }
    fun millis(value: String): Long? = micros(value)?.div(1000)
    fun micros(value: String): Long? {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?Z")) || value.take(4).toInt() < 1970) return null
        val prefix = value.take(19)
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        val position = ParsePosition(0)
        val date = format.parse(prefix, position) ?: return null
        if (position.index != prefix.length || format.format(date) != prefix) return null
        val fraction = if (value.length == 20) 0 else value.substring(20, value.length - 1).padEnd(6, '0').toInt()
        return date.time * 1000 + fraction
    }
    fun failure(entity: Entity) = Failure.Internal(FailureMeta("The Go API returned an unexpected session or account response.", "session.go_invalid_response", entity.metadata.requestId, entity.metadata.correlationId))
    fun tokens(entity: Entity, now: Long): Tokens? {
        if (entity.metadata.status != 200) return null
        val body = entity.body as? JsonObject ?: return null
        if (body.string("authority_id") != "go.identity.v1") return null
        val p = body["principal"] as? JsonObject ?: return null
        val pid = p.string("id")?.takeIf(::uuid) ?: return null
        if (p.string("login")?.takeIf { it.matches(Regex("[a-z0-9][a-z0-9._@+-]{2,127}")) } == null) return null
        val display = p.string("display_name")?.takeIf(::name) ?: return null
        val principal = Principal.make(pid, display) ?: return null
        if (body.string("session_id")?.takeIf(::uuid) == null || version(body["session_version"]) == null) return null
        val access = body.string("access_token")?.takeIf { token(it, "a1") } ?: return null
        val refresh = body.string("refresh_token")?.takeIf { token(it, "r1") } ?: return null
        val expiry = body.string("access_expires_at")?.let(::millis)?.takeIf { it > now } ?: return null
        if (body.string("refresh_expires_at")?.let(::millis)?.takeIf { it >= expiry } == null) return null
        return Tokens(principal, access, refresh, expiry)
    }
    fun account(value: JsonElement, ownerId: String): Account? {
        val body = value as? JsonObject ?: return null
        val identity = body.string("id")?.takeIf(::uuid) ?: return null
        if (body.string("owner_id") != ownerId || version(body["version"]) == null) return null
        val name = body.string("name")?.takeIf(::name) ?: return null
        val created = body.string("created_at")?.let(::micros) ?: return null
        if (body.string("updated_at")?.let(::micros)?.takeIf { it >= created } == null) return null
        return Account.make(identity, name)
    }
}
