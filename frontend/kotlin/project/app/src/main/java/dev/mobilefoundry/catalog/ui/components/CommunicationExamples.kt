package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.avatar.Avatar
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.messagebubble.MessageBubble
import dev.mobilefoundry.ui.components.display.messagebubble.MessageDirection
import dev.mobilefoundry.ui.components.feedback.transfer.TransferPhase
import dev.mobilefoundry.ui.components.feedback.transfer.TransferStatus
import dev.mobilefoundry.ui.components.feedback.typing.TypingIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.patterns.attachmentrow.AttachmentRow
import dev.mobilefoundry.ui.components.patterns.conversationrow.ConversationRow
import dev.mobilefoundry.ui.components.patterns.messagecomposer.MessageComposer
import dev.mobilefoundry.ui.theme.FoundryTheme

internal data class CommunicationValues(val draft: TextFieldState, val attached: Boolean = false,
    val phase: TransferPhase = TransferPhase.WAITING, val enabled: Boolean = true, val typing: Boolean = true,
    val messages: List<String> = emptyList(), val messageAttachments: List<Boolean> = emptyList(), val inspected: Int = 0) {
    val canSend get() = enabled && (!attached || phase == TransferPhase.COMPLETE) && (draft.text.isNotBlank() || attached)
    fun sent(): CommunicationValues {
        if (!canSend) return this
        val next = copy(messages = messages + draft.text.toString(), messageAttachments = messageAttachments + attached,
            attached = false, phase = TransferPhase.WAITING)
        draft.edit { replace(0, length, "") }
        return next
    }
    fun addAttachment() = if (enabled && !attached) copy(attached = true, phase = TransferPhase.WAITING) else this
    fun removeAttachment() = if (enabled) copy(attached = false, phase = TransferPhase.WAITING) else this
    fun transition(next: TransferPhase): CommunicationValues {
        if (!enabled || !attached) return this
        val allowed = when (phase) {
            TransferPhase.WAITING, TransferPhase.PAUSED, TransferPhase.FAILED -> next == TransferPhase.TRANSFERRING
            TransferPhase.TRANSFERRING -> next in listOf(TransferPhase.PAUSED, TransferPhase.FAILED, TransferPhase.COMPLETE)
            TransferPhase.COMPLETE -> false
        }
        return if (allowed) copy(phase = next) else this
    }
    val transferCopy get() = when (phase) {
        TransferPhase.WAITING -> "Waiting to start. Local preview only."
        TransferPhase.TRANSFERRING -> "Transferring preview: 35%."
        TransferPhase.PAUSED -> "Transfer paused. Your draft is still here."
        TransferPhase.FAILED -> "Transfer failed. Your draft is still here."
        TransferPhase.COMPLETE -> "Attachment ready. Local preview only."
    }
}

@Composable
internal fun CommunicationExamples(values: CommunicationValues, onChange: (CommunicationValues) -> Unit, onOpen: () -> Unit) {
    ToggleField("Enable communication controls", values.enabled, { onChange(values.copy(enabled = it)) })
    ToggleField("Show typing preview", values.typing, { onChange(values.copy(typing = it)) })
    Card {
        ConversationRow("Design room", values.messages.lastOrNull()?.takeIf { it.isNotEmpty() }
            ?: "Jordan: Share the next study when it is ready.", "Just now", onOpen,
            unreadLabel = "3 unread", enabled = values.enabled, leading = { Avatar("Design room", "DR") })
    }
    Text("Open Design room for a conversation with a persistent composer. Everything stays in this preview.", style = FoundryTheme.tokens.typography.caption)
    ConversationMessages(values, onChange)
    if (values.attached) TransferPreview(values, onChange)
    ConversationComposer(values, onChange)
}

@Composable
private fun ConversationMessages(values: CommunicationValues, onChange: (CommunicationValues) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MessageBubble("Jordan", "Share the next study when it is ready. Attachments can be sent on their own, too.", "10:42 · Preview message")
        // Append-only local fixtures: positions remain stable because this preview never deletes or reorders messages.
        values.messages.forEachIndexed { index, text ->
            MessageBubble("You", text, "Saved locally · Preview message", direction = MessageDirection.OUTGOING) {
                if (values.messageAttachments[index]) {
                    AttachmentRow("Study brief.pdf", "PDF · 240 KB · Preview file", preview = { Text("PDF") }, actions = {
                        ActionButton({ onChange(values.copy(inspected = values.inspected + 1)) }, variant = ButtonVariant.QUIET,
                            enabled = values.enabled) { Text("Inspect sent attachment") }
                    })
                }
            }
        }
        if (values.typing) TypingIndicator("Jordan is typing…")
        Text("Sent previews: ${values.messages.size} · Inspected files: ${values.inspected}")
    }
}
@Composable
private fun TransferPreview(values: CommunicationValues, onChange: (CommunicationValues) -> Unit) {
    Card {
        TransferStatus("Attachment transfer", values.transferCopy, values.phase, fraction = .35f) {
            fun move(next: TransferPhase) { onChange(values.transition(next)) }
            when (values.phase) {
                TransferPhase.WAITING -> ActionButton({ move(TransferPhase.TRANSFERRING) }, enabled = values.enabled,
                    variant = ButtonVariant.SECONDARY) { Text("Start transfer preview") }
                TransferPhase.TRANSFERRING -> {
                    ActionButton({ move(TransferPhase.PAUSED) }, enabled = values.enabled, variant = ButtonVariant.SECONDARY) { Text("Pause transfer preview") }
                    ActionButton({ move(TransferPhase.FAILED) }, enabled = values.enabled, variant = ButtonVariant.QUIET) { Text("Fail transfer preview") }
                    ActionButton({ move(TransferPhase.COMPLETE) }, enabled = values.enabled) { Text("Finish transfer preview") }
                }
                TransferPhase.PAUSED -> ActionButton({ move(TransferPhase.TRANSFERRING) }, enabled = values.enabled) { Text("Resume transfer preview") }
                TransferPhase.FAILED -> ActionButton({ move(TransferPhase.TRANSFERRING) }, enabled = values.enabled) { Text("Retry transfer preview") }
                TransferPhase.COMPLETE -> Unit
            }
            if (values.phase != TransferPhase.COMPLETE) ActionButton({ onChange(values.removeAttachment()) }, enabled = values.enabled,
                variant = ButtonVariant.QUIET) { Text("Cancel transfer preview") }
        }
    }
}
@Composable
private fun ConversationComposer(values: CommunicationValues, onChange: (CommunicationValues) -> Unit) {
    MessageComposer("Message draft", values.draft, "Send preview", values.canSend, { onChange(values.sent()) },
        Modifier.testTag("message-composer"), enabled = values.enabled, help = "Return adds a line. Send saves locally.", attachments = { interactive ->
            if (values.attached) AttachmentRow("Study brief.pdf", "PDF · 240 KB · Preview file", preview = { Text("PDF") }, actions = {
                IconAction("Remove draft attachment", { onChange(values.removeAttachment()) }, enabled = interactive,
                    variant = ButtonVariant.QUIET) { Text("×") }
            })
        }, actions = { interactive ->
            IconAction("Add preview attachment", { onChange(values.addAttachment()) }, enabled = interactive && !values.attached,
                variant = ButtonVariant.SECONDARY) { Text("+") }
        })
}
@Composable
internal fun ConversationPreviewScreen(values: CommunicationValues, onChange: (CommunicationValues) -> Unit, onBack: () -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    val back = { focus.clearFocus(); onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().imePadding()) {
        TextButton(onClick = back) { Text("Back to components") }
        Text("Design room", style = t.typography.heading, modifier = Modifier.padding(horizontal = 16.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("conversation-content"),
            verticalArrangement = Arrangement.spacedBy(t.space.stack)) {
            Text("Local conversation preview", style = t.typography.label)
            ConversationMessages(values, onChange)
            if (values.attached) TransferPreview(values, onChange)
        }
        Box(Modifier.padding(12.dp)) { ConversationComposer(values, onChange) }
    }
}
