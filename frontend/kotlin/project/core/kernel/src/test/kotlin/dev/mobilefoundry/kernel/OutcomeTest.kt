package dev.mobilefoundry.kernel

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class OutcomeTest {
    @Test fun successMappingAndChaining() {
        val original: AppResult<Int> = Outcome.Ok(2)
        var mapCalls = 0
        val mapped = original.map { mapCalls++; it * 3 }
        assertEquals(Outcome.Ok(6), mapped)
        assertEquals(1, mapCalls)

        var chainCalls = 0
        val refusal = Failure.Conflict(FailureMeta("Changed."))
        val chained = mapped.flatMap {
            chainCalls++
            Outcome.Err(refusal)
        }
        assertEquals(Outcome.Err(refusal), chained)
        assertEquals(1, chainCalls)

        var errorCalls = 0
        val unchanged = original.mapError { errorCalls++; "different error type" }
        assertSame(original, unchanged)
        assertEquals(0, errorCalls)
    }

    @Test fun failureSkipsSuccessCallbacksAndMapsOnlyError() {
        val failure = Failure.Unavailable(FailureMeta("Unavailable."))
        val original: AppResult<Int> = Outcome.Err(failure)
        var successCalls = 0
        val mapped = original.map { successCalls++; it.toString() }
        val chained = original.flatMap { successCalls++; Outcome.Ok(it.toString()) }
        assertSame(original, mapped)
        assertSame(original, chained)
        assertEquals(0, successCalls)

        var errorCalls = 0
        val mappedError = original.mapError {
            errorCalls++
            assertSame(failure, it)
            "operation-specific-error"
        }
        assertEquals(Outcome.Err("operation-specific-error"), mappedError)
        assertEquals(1, errorCalls)
    }

    @Test fun absenceAndEmptyCollectionsAreSuccessfulValues() {
        val missing: AppResult<String?> = Outcome.Ok(null)
        val empty: AppResult<List<String>> = Outcome.Ok(emptyList())
        assertEquals(Outcome.Ok(null), missing)
        assertEquals(Outcome.Ok(emptyList<String>()), empty)
        assertEquals(Outcome.Ok(null), missing.map { it?.length })
    }

    @Test fun callbackDefectsPropagateInsteadOfBecomingFailures() {
        val success: AppResult<Int> = Outcome.Ok(1)
        val failure: AppResult<Int> = Outcome.Err(Failure.Timeout(FailureMeta("Timed out.")))
        val defect = IllegalStateException("Unexpected mapping defect")
        assertSame(defect, assertThrows(IllegalStateException::class.java) {
            success.map { throw defect }
        })
        assertSame(defect, assertThrows(IllegalStateException::class.java) {
            success.flatMap<Int, Failure, Int> { throw defect }
        })
        assertSame(defect, assertThrows(IllegalStateException::class.java) {
            failure.mapError { throw defect }
        })
    }

    @Test fun thrownCancellationIsNotClassifiedByMapping() {
        val success: AppResult<Int> = Outcome.Ok(1)
        val cancellation = CancellationException("Canceled by owner")
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            success.map { throw cancellation }
        })
    }
}
