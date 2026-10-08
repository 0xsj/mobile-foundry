# Forms and create-note mutations

Status: Established for the first write exemplar, 2026-10-08.

## Values and ports

CreateNote is an immutable command admitted by its factory before service work.
Its title must contain a character other than ASCII space/tab/CR/LF and contain
at most 200 Unicode code points (Swift Unicode scalars, Kotlin codePointCount).
Input is preserved without trimming. Blank input produces invalid/
notes.invalid_title with title="Enter a title."; excess length uses the same
kind/code with title="Use 200 characters or fewer.". The summary is
"Check the highlighted field.". This creation bound does not narrow existing
[Note read admission](notes-query.md).

NoteCreator exposes one asynchronous create(CreateNote) -> AppResult<Note>.
It is separate from the existing read port. Structured cancellation and
unexpected defects propagate. InMemoryNoteCreator also implements NotesService:
it appends newly identified notes atomically within its instance and returns
owned snapshots. IDs are native UUID strings. There is no disk persistence.

HTTPNoteCreator posts {"title": string} to relative `notes`, with no automatic
retry or invented idempotency guarantee. A successful entity must contain
`note` with valid string `id` and `title` under the existing Note invariant.
Server-normalized valid titles are accepted. Invalid success data produces
internal/notes.invalid_create_response with response request/correlation IDs.
HTTP failures pass through. Catalog wire responses are injected, not a deployed API.

## Mutation values and feature ownership

MutationState<Value> lives beside QueryState in the query module. Its states
are Idle, Submitting, Succeeded(value), Failed(failure). It exposes isSubmitting,
value (success only), failure (failed only), starting(), settled(result), and
reset(). Pure transitions own no execution, draft, cache, retry, or deduplication.
Starting drops an earlier receipt; reset returns Idle. Payloads must be owned
immutable snapshots. A failure does not imply that a remote write was undone.

CreateNoteStore/ViewModel owns draft, touched validation, task/job, and generation.
Blur validates; submit validates even if untouched. Invalid local input focuses
the title and never calls the creator. Submit marks Submitting synchronously
before launching work. Further submit, edit, reset, or provider/scenario changes
are refused while submitting. This blocks concurrent duplicate attempts only.

On expected failure the draft remains. Recognized server `title` errors appear
beside the field; the projected summary remains visible, including when field
keys are unknown. Editing clears the previous server result and revalidates a
touched field. Success keeps the input and receipt, disables further submission,
and requires New note to clear the form. Reset clears draft, touched/errors and
receipt without invoking a service. Changing an idle service preserves the draft
and touched status, clears server feedback, and changes only the dependency.

Leaving the screen cancels owned work and invalidates result admission. If an
attempt was submitting, the local state becomes Failed(canceled) with deliberate
copy. Late values and defects cannot publish or report into the abandoned scope.
No Cancel write action, automatic retry, rollback, persistence, query-cache
invalidation, or reconciliation mechanism is added. Timeout, canceled,
unavailable and internal feedback tells the user confirmation is missing and
to check notes before resubmitting; an explicit new attempt may duplicate a
previously committed remote write. No idempotency is claimed.

Current unexpected defects reach the injected diagnostic callback with their
original identity and produce a generic internal presentation failure. Public
feedback always uses kernel publicInfo. Old defects are ignored after invalidation.

## Native controls and catalog

Reusable controls live in Components/Forms/TextField, Components/Forms/SubmitButton
and Components/Feedback/Mutation, with lowercase Kotlin paths. FoundryTextField
renders a persistent label, native single-line input, help/error copy, and an
error indicator that does not rely on color. The caller owns focus and keyboard
submission policy. FoundrySubmitButton shows native progress and blocks action
while busy/disabled. MutationFeedback renders progress copy, projected failure,
or caller-supplied success content without invoking callbacks.

The Forms and mutations destination uses one renderer for Memory/HTTP and
Success, Field error, Unavailable, Slow, Timeout and Malformed scenarios. Changing
provider/scenario preserves the draft and is disabled during submission. The
form's outer panels use the floating surface role and follow Solid/Glass. The
catalog supplies a decorative background, including an explicit Compose backdrop
source on Android. Native input, text and feedback render above the material;
transparency reduction and unsupported blur retain the opaque fallback.
IME/Return and the button dispatch the
same guarded submit; invalid submission focuses Title, admitted submission
dismisses the keyboard. Layout scrolls with native keyboard insets and scalable
text; control dimensions are minimums, not fixed heights.

Shared command/response and mutation fixtures, native service/state-owner tests,
and Compose device checks establish the stated behaviors. iOS execution is
checked on the simulator. Builds alone do not establish screen-reader quality,
every large-text/keyboard configuration, or physical-device behavior.
