package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegistrationState(val busy: Boolean = false, val registered: RegisteredIdentity? = null, val loginHint: String = "",
    val message: String? = null, val failure: Failure? = null, val uncertain: Boolean = false)

/** No password/command fields or saved state. Registration never issues a session. */
class RegistrationModel(private val scope: CoroutineScope, private val service: IdentityRegistration, private val controller: SessionController) {
    val canSubmit get() = !state.value.busy && state.value.registered == null && controller.state.value.phase == SessionPhase.SIGNED_OUT
    private val mutable = MutableStateFlow(RegistrationState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var generation = 0L
    companion object { const val interrupted = "Account creation was not confirmed. The account may exist. Try signing in before submitting again." }
    fun open() {
        cancel()
        mutable.value = if (state.value.uncertain) state.value.copy(registered = null, failure = null) else RegistrationState()
    }
    fun reset() { cancel(); mutable.value = RegistrationState() }
    fun cancel() {
        generation++; task?.cancel(); task = null
        mutable.value = if (state.value.busy) state.value.copy(busy = false, uncertain = true, registered = null, failure = null, message = interrupted)
            else state.value.copy(busy = false)
    }
    fun register(login: String, displayName: String, password: String) {
        if (!canSubmit) return
        val command = when (val admitted = RegisterIdentity.make(login, displayName, password)) {
            is Outcome.Err -> { mutable.value = state.value.copy(failure = admitted.error); return }
            is Outcome.Ok -> admitted.value
        }
        mutable.value = RegistrationState(busy = true, loginHint = command.login)
        val current = generation; val epoch = controller.state.value.epoch
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                currentCoroutineContext().ensureActive()
                val answer = service.register(command); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                if (controller.state.value.epoch != epoch || controller.state.value.phase != SessionPhase.SIGNED_OUT) { this@RegistrationModel.cancel(); return@launch }
                mutable.value = when (answer) {
                    is Outcome.Ok -> if (answer.value.confirms(command)) state.value.copy(busy = false, registered = answer.value, message = "Account created. Sign in to open your personal workspace.")
                        else state.value.copy(busy = false, uncertain = true, message = interrupted)
                    is Outcome.Err -> {
                        val uncertain = answer.error.kind in setOf(FailureKind.UNAVAILABLE, FailureKind.TIMEOUT, FailureKind.CANCELED, FailureKind.INTERNAL)
                        state.value.copy(busy = false, failure = answer.error.publicInfo(), uncertain = uncertain, message = if (uncertain) interrupted else null)
                    }
                }
            } catch (_: Exception) {
                if (current != generation) return@launch
                mutable.value = state.value.copy(busy = false, uncertain = true, message = interrupted)
            }
            if (current == generation) task = null
        }
        task = job; job.start()
    }
}
