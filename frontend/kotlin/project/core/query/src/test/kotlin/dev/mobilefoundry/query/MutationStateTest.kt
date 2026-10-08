package dev.mobilefoundry.query

import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MutationStateTest {
    @Test fun sharedMatrixDropsReceiptsAcrossAttemptsAndResetsExplicitly() {
        val cases = Json.parseToJsonElement(File(System.getProperty("foundry.query.fixtures"), "mutations.json").readText()).jsonArray
        val failure = Failure.Timeout(FailureMeta("No confirmation"))
        for (entry in cases) {
            val row = entry.jsonObject
            val expectedValue = row.getValue("value").jsonPrimitive.contentOrNull
            val state: MutationState<String> = when (row.getValue("phase").jsonPrimitive.content) {
                "idle" -> MutationState.Idle
                "submitting" -> MutationState.Submitting
                "succeeded" -> MutationState.Succeeded(checkNotNull(expectedValue))
                "failed" -> MutationState.Failed(failure)
                else -> error("Unknown mutation phase")
            }
            assertEquals(expectedValue, state.value)
            assertEquals(row.getValue("busy").jsonPrimitive.boolean, state.isSubmitting)
            assertEquals(if (row.getValue("failed").jsonPrimitive.boolean) failure else null, state.failure)
            assertEquals(MutationState.Submitting, state.starting())
            assertNull(state.starting().value)
            assertEquals(MutationState.Succeeded("Next"), state.settled(Outcome.Ok("Next")))
            assertEquals(MutationState.Failed(failure), state.settled(Outcome.Err(failure)))
            assertEquals(MutationState.Idle, state.reset())
        }
    }
}
