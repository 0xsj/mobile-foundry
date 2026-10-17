package dev.mobilefoundry.services

import java.time.Instant

/** Shared wire identifier/calendar admission; independent of service domain. */
internal object RemoteServiceAdmission {
    fun uuid(value: String) = value != "00000000-0000-0000-0000-000000000000" && Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$").matches(value)
    private val upperDate = Instant.ofEpochSecond(253402300800L)
    fun date(value: Instant) = value >= Instant.EPOCH && value < upperDate
    fun timestamp(value: String?): Instant? {
        if (value == null || !Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,6})?Z$").matches(value)) return null
        return runCatching { Instant.parse(value) }.getOrNull()?.takeIf(::date)
    }
}
