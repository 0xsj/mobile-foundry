package dev.mobilefoundry.query

import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.kernel.FailureMeta
import dev.mobilefoundry.kernel.Outcome
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class QueryStateTest {
    @Test fun sharedStateMatrixPreservesAbsenceAndEmptySnapshots() {
        val fixtures = Json.parseToJsonElement(File(System.getProperty("foundry.query.fixtures"), "states.json").readText()).jsonArray
        val failure = Failure.Unavailable(FailureMeta("Unavailable"))
        for (element in fixtures) {
            val fixture = element.jsonObject
            val snapshot = fixture.getValue("snapshot").jsonPrimitive.contentOrNull
            val expected = fixture.getValue("expectedValue").jsonPrimitive.contentOrNull
            val state: QueryState<String> = when (fixture.getValue("phase").jsonPrimitive.content) {
                "idle" -> QueryState.Idle
                "loading" -> QueryState.Loading(snapshot)
                "loaded" -> QueryState.Loaded(requireNotNull(snapshot))
                "failed" -> QueryState.Failed(failure, snapshot)
                else -> error("Unknown fixture phase")
            }
            assertEquals(fixture.getValue("id").jsonPrimitive.content, expected, state.value)
            assertEquals(fixture.getValue("loading").jsonPrimitive.boolean, state.isLoading)
            assertEquals(if (fixture.getValue("failed").jsonPrimitive.boolean) failure else null, state.failure)
            assertEquals(QueryState.Loading(expected), state.starting())
            assertEquals(QueryState.Failed(failure, expected), state.settled(Outcome.Err(failure)))
            assertEquals(QueryState.Loaded("New"), state.settled(Outcome.Ok("New")))
            if (fixture.getValue("restoredPhase").jsonPrimitive.content == "idle") assertEquals(QueryState.Idle, state.restored())
            else assertEquals(QueryState.Loaded(requireNotNull(expected)), state.restored())
        }
    }

    @Test fun repeatedRefreshFailureAndCancellationRetainEmptySuccess() {
        val failure = Failure.Timeout(FailureMeta("Timed out"))
        var state: QueryState<List<Int>> = QueryState.Idle
        state = state.starting().settled(Outcome.Ok(listOf(1)))
        assertEquals(QueryState.Loaded(listOf(1)), state)
        state = state.starting().settled(Outcome.Ok(emptyList()))
        state = state.starting().settled(Outcome.Err(failure))
        state = state.starting().starting()
        assertEquals(QueryState.Loading(emptyList<Int>()), state)
        assertEquals(QueryState.Loaded(emptyList<Int>()), state.restored())
    }
}
