package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class IdentitySettingsState(val busy: Boolean = false, val message: String? = null, val failure: Failure? = null, val contact: RecoveryContact? = null)

/** No password/code fields, saved state or automatic write retries. */
class IdentitySettingsModel(private val scope: CoroutineScope, private val controller: SessionController, private val publicService: IdentityRecovery) {
    private val mutable = MutableStateFlow(IdentitySettingsState())
    val state = mutable.asStateFlow()
    private var service: IdentityRecovery? = null
    private var lease: SessionLease? = null
    private var passwordLease: SessionLease? = null
    private var task: Job? = null
    private var generation = 0L
    fun bind(service: IdentityRecovery?, lease: SessionLease?) { cancel(); this.service = service; this.lease = lease; mutable.value = IdentitySettingsState(message = state.value.message) }
    fun cancel() {
        generation++; task?.cancel(); task = null
        mutable.value = IdentitySettingsState(message = state.value.message, contact = state.value.contact)
        val abandoned = passwordLease; passwordLease = null
        if (abandoned != null && controller.isCurrent(abandoned)) {
            mutable.value = IdentitySettingsState(message = "Password change was interrupted. Sign in again; use recovery if needed.")
            controller.signOut()
        }
    }
    fun requestReset(email: String) {
        if (controller.lease != null || controller.isBusy) return
        perform("If an eligible address exists, recovery instructions may arrive.") { publicService.requestReset(email) }
    }
    fun reset(token: String, password: String) {
        if (controller.lease != null || controller.isBusy) return
        perform("Password reset. Sign in with your new password.") { publicService.reset(token, password) }
    }
    fun requestVerification(email: String, password: String) {
        val service = service ?: return; val lease = lease ?: return
        if (controller.isCurrent(lease)) perform("Verification requested. Enter the code from your email.") { service.requestVerification(email, password) }
    }
    fun verify(token: String) {
        val lease = lease ?: return
        if (controller.isCurrent(lease)) perform("Email verified. Refresh the verified address to confirm.") { publicService.verify(token) }
    }
    fun change(currentPassword: String, newPassword: String) {
        val service = service ?: return; val lease = lease ?: return
        if (state.value.busy || !controller.isCurrent(lease)) return
        passwordLease = lease
        perform("Password changed. All sessions were revoked; sign in again.") { service.change(currentPassword, newPassword) }
    }
    fun loadContact() {
        val service = service ?: return; val lease = lease ?: return
        if (!controller.isCurrent(lease)) return
        perform("Verified address refreshed.") {
            val answer = service.contact(); currentCoroutineContext().ensureActive()
            if (!controller.isCurrent(lease)) Outcome.Err(SessionFailures.scopeChanged)
            else answer.map { mutable.value = IdentitySettingsState(busy = true, contact = it) }
        }
    }
    private fun perform(success: String, action: suspend () -> AppResult<Unit>) {
        if (state.value.busy) return
        val current = generation
        mutable.value = IdentitySettingsState(busy = true, contact = state.value.contact)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val answer = action(); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                when (answer) {
                    is Outcome.Ok -> mutable.value = IdentitySettingsState(message = success, contact = state.value.contact)
                    is Outcome.Err -> {
                        mutable.value = IdentitySettingsState(failure = answer.error.publicInfo(), contact = state.value.contact)
                        if (answer.error is Failure.Invalid) passwordLease = null
                    }
                }
            } catch (_: CancellationException) {
                if (current != generation) return@launch
                mutable.value = IdentitySettingsState(failure = Failure.Canceled(FailureMeta("The action stopped without confirmation.")), contact = state.value.contact)
            }
            catch (_: Exception) {
                if (current != generation) return@launch
                mutable.value = IdentitySettingsState(failure = Failure.Unavailable(FailureMeta("The action could not be confirmed. Refresh or request a new code; it was not retried.")), contact = state.value.contact)
            }
            if (current != generation) return@launch
            task = null
            val changed = passwordLease; passwordLease = null
            if (changed != null && controller.isCurrent(changed)) {
                if (state.value.failure != null) mutable.value = IdentitySettingsState(message = "Password change could not be confirmed. Sign in again; use recovery if needed.", failure = state.value.failure)
                controller.signOut()
            }
        }
        task = job; job.start()
    }
}
