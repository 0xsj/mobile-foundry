package dev.mobilefoundry.kernel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

private data class Fixture(
    val caseId: String,
    val kind: FailureKind,
    val meta: FailureMeta,
    val fields: Map<String, String>,
    val retryAfter: RetryAfter?,
    val publicMessage: String,
    val publicCode: String?,
) {
    fun failure(): Failure = when (kind) {
        FailureKind.UNAUTHENTICATED -> Failure.Unauthenticated(meta)
        FailureKind.FORBIDDEN -> Failure.Forbidden(meta)
        FailureKind.RATE_LIMITED -> Failure.RateLimited(meta, retryAfter)
        FailureKind.UNAVAILABLE -> Failure.Unavailable(meta)
        FailureKind.TIMEOUT -> Failure.Timeout(meta)
        FailureKind.CANCELED -> Failure.Canceled(meta)
        FailureKind.INTERNAL -> Failure.Internal(meta)
        FailureKind.NOT_FOUND -> Failure.NotFound(meta)
        FailureKind.INVALID -> Failure.Invalid(meta, fields)
        FailureKind.CONFLICT -> Failure.Conflict(meta)
    }
}

private fun fixtures(): List<Fixture> {
    val directory = requireNotNull(System.getProperty("foundry.fixtures"))
    val lines = File(directory, "failures.tsv").readLines()
    check(lines.first().startsWith("kind\tmessage\tcode\t"))
    return lines.drop(1).map { line ->
        val cells = line.split('\t')
        check(cells.size == 11)
        fun optional(index: Int): String? = cells[index].ifEmpty { null }
        Fixture(
            caseId = cells[10],
            kind = FailureKind.entries.single { it.value == cells[0] },
            meta = FailureMeta(cells[1], optional(2), optional(3), optional(4)),
            fields = if (cells[5].isEmpty()) emptyMap() else mapOf(cells[5] to cells[6]),
            retryAfter = optional(7)?.let { requireNotNull(RetryAfter.fromMilliseconds(it.toLong())) },
            publicMessage = cells[8],
            publicCode = optional(9),
        )
    }
}

class FailureTest {
    @Test fun sharedFixturesCoverEveryKindAndPublicProjection() {
        val cases = fixtures()
        assertEquals(FailureKind.entries.toSet(), cases.map { it.kind }.toSet())
        assertEquals(13, cases.size)
        assertEquals(cases.size, cases.map { it.caseId }.toSet().size)
        for (fixture in cases) {
            val failure = fixture.failure()
            assertEquals(fixture.kind, failure.kind)
            assertEquals(fixture.meta, failure.meta)
            val publicValue = failure.publicInfo()
            assertEquals(fixture.kind, publicValue.kind)
            assertEquals(fixture.publicMessage, publicValue.meta.message)
            assertEquals(fixture.publicCode, publicValue.meta.code)
            assertEquals(fixture.meta.requestId, publicValue.meta.requestId)
            assertEquals(fixture.meta.correlationId, publicValue.meta.correlationId)
            assertEquals(publicValue, publicValue.publicInfo())
            when (publicValue) {
                is Failure.Invalid -> assertEquals(fixture.fields, publicValue.fields)
                is Failure.RateLimited -> assertEquals(fixture.retryAfter, publicValue.retryAfter)
                else -> Unit
            }
            if (fixture.kind != FailureKind.INTERNAL) assertEquals(failure, publicValue)
        }
    }

    @Test fun validationFieldsAreASnapshotAndCannotBeMutated() {
        val original = mutableMapOf("email" to "Required.")
        val failure = Failure.Invalid(FailureMeta("Check input."), original)
        original["email"] = "Changed by caller."
        original["password"] = "Extra."
        assertEquals(mapOf("email" to "Required."), failure.fields)
        assertThrows(UnsupportedOperationException::class.java) {
            (failure.fields as MutableMap<String, String>)["email"] = "Changed after extraction."
        }
        assertEquals(mapOf("email" to "Required."), failure.fields)
    }

    @Test fun retryTimingRejectsNegativeAndPreservesBounds() {
        assertNull(RetryAfter.fromMilliseconds(-1))
        assertNull(RetryAfter.fromMilliseconds(Long.MIN_VALUE))
        assertEquals(0L, RetryAfter.fromMilliseconds(0)?.milliseconds)
        assertEquals(Long.MAX_VALUE, RetryAfter.fromMilliseconds(Long.MAX_VALUE)?.milliseconds)
    }

    @Test fun internalProjectionDoesNotChangeTheOriginalValue() {
        val failure = Failure.Internal(FailureMeta("Private database detail.", "database.connection_failed"))
        val publicValue = failure.publicInfo()
        assertEquals("An unexpected error occurred.", publicValue.meta.message)
        assertNull(publicValue.meta.code)
        assertEquals("Private database detail.", failure.meta.message)
        assertEquals("database.connection_failed", failure.meta.code)
    }
}
