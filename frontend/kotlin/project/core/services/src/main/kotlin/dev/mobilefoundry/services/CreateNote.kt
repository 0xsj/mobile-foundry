package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*

/** Admitted command; preserve input and count Unicode code points, not UTF-16 units. */
class CreateNote private constructor(val title: String) {
    companion object {
        fun make(title: String): AppResult<CreateNote> {
            val error = when {
                title.none { it !in " \t\r\n" } -> "Enter a title."
                title.codePointCount(0, title.length) > 200 -> "Use 200 characters or fewer."
                else -> null
            }
            return if (error != null) Outcome.Err(Failure.Invalid(
                FailureMeta("Check the highlighted field.", "notes.invalid_title"), mapOf("title" to error),
            )) else Outcome.Ok(CreateNote(title))
        }
    }
}

/** Narrow write port. Cancellation and defects propagate; no rollback is implied. */
fun interface NoteCreator { suspend fun create(command: CreateNote): AppResult<Note> }
