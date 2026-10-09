package dev.mobilefoundry.catalog.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.avatar.Avatar
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.inlineaction.InlineActionField
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.dialog.ConfirmationDialog
import dev.mobilefoundry.ui.components.patterns.memberrow.MemberRow
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.patterns.sharelinkcard.ShareLinkCard
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale

internal enum class MemberRole(val label: String) { VIEWER("Viewer"), EDITOR("Editor") }
internal enum class LinkAccess(val label: String, val detail: String) {
    OFF("Off", "Only invited members have access in this preview."),
    VIEW("Can view", "Anyone with the example link can view in this preview."),
    EDIT("Can edit", "Anyone with the example link can edit in this preview.")
}
internal data class SharingContact(val id: String, val name: String, val email: String, val initials: String,
    val owner: Boolean = false, val available: Boolean = true) {
    companion object {
        val all = listOf(
            SharingContact("alex", "Alex Morgan", "alex@example.test", "AM", owner = true),
            SharingContact("jamie", "Jamie Park", "jamie@example.test", "JP"),
            SharingContact("sam", "Sam Chen", "sam@example.test", "SC"),
            SharingContact("river", "River Vale", "river@example.test", "RV"),
            SharingContact("lena", "Lena Hart", "lena@example.test", "LH", available = false))
        fun find(id: String) = all.firstOrNull { it.id == id }
    }
}
internal data class MemberRemoval(val id: String, val revision: Int)
/** Local fixture membership. A confirmation identifies membership revision, not merely a visible row index. */
internal data class SharingValues(val members: List<String> = listOf("alex", "jamie", "sam"),
    val roles: Map<String, MemberRole> = mapOf("jamie" to MemberRole.EDITOR, "sam" to MemberRole.VIEWER),
    val draft: String = "", val inviteRole: MemberRole = MemberRole.VIEWER, val error: String? = null,
    val linkAccess: LinkAccess = LinkAccess.VIEW, val enabled: Boolean = true, val pending: Boolean = false,
    val revision: Int = 0, val invites: Int = 0, val roleChanges: Int = 0, val removals: Int = 0, val copies: Int = 0) {
    val canEdit get() = enabled && !pending
    val canInvite get() = canEdit && draft.trim().isNotEmpty()
    val canCopy get() = canEdit && linkAccess != LinkAccess.OFF
    val contacts get() = members.mapNotNull(SharingContact::find)
    fun role(id: String) = roles[id] ?: MemberRole.VIEWER
    fun canManage(id: String) = canEdit && id in members && SharingContact.find(id)?.let { !it.owner && it.available } == true
    fun editDraft(value: String) = if (canEdit) copy(draft = value, error = null) else this
    fun chooseInviteRole(value: MemberRole) = if (canEdit) copy(inviteRole = value) else this
    fun invite(): SharingValues {
        if (!canInvite) return this
        val address = draft.trim().lowercase(Locale.ROOT)
        val contact = SharingContact.all.firstOrNull { it.email == address } ?: return copy(error = "Use river@example.test in this preview.")
        if (!contact.available) return copy(error = "This teammate is unavailable.")
        if (contact.id in members) return copy(error = "Already a member.")
        return copy(members = members + contact.id, roles = roles + (contact.id to inviteRole), revision = revision + 1, invites = invites + 1, draft = "", error = null)
    }
    fun changeRole(id: String, value: MemberRole) = if (canManage(id) && role(id) != value)
        copy(roles = roles + (id to value), revision = revision + 1, roleChanges = roleChanges + 1) else this
    fun removal(id: String) = if (canManage(id)) MemberRemoval(id, revision) else null
    fun canRemove(request: MemberRemoval) = request.revision == revision && canManage(request.id)
    fun remove(request: MemberRemoval) = if (canRemove(request)) copy(members = members - request.id, roles = roles - request.id, revision = revision + 1, removals = removals + 1) else this
    fun chooseLinkAccess(value: LinkAccess) = if (canEdit) copy(linkAccess = value) else this
    fun copyLink() = if (canCopy) copy(copies = copies + 1) else this
    fun resetMembers() = if (canEdit) copy(members = listOf("alex", "jamie", "sam"), roles = mapOf("jamie" to MemberRole.EDITOR, "sam" to MemberRole.VIEWER), error = null, revision = revision + 1) else this
    companion object { const val LINK = "https://example.test/share/atlas" }
}
@Composable
internal fun SharingExamples(values: SharingValues, onChange: (SharingValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Sharing and access", subtitle = "People, role controls, guarded invitations and selectable links.")
        NavLink("Open sharing preview", onPreview, subtitle = "Try local workspace members and link access")
    }
    SharingContent(values, onChange)
}
@Composable
internal fun SharingPreviewScreen(values: SharingValues, onChange: (SharingValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Sharing preview", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("sharing-scroll").padding(20.dp)) { SharingContent(values, onChange) }
    }
}
@Composable
internal fun SharingContent(values: SharingValues, onChange: (SharingValues) -> Unit, copyText: ((String) -> Unit)? = null) {
    val t = FoundryTheme.tokens
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var removal by remember { mutableStateOf<MemberRemoval?>(null) }
    LaunchedEffect(values.canEdit, values.revision) { if (removal?.let(values::canRemove) == false) removal = null }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable sharing actions", values.enabled, { onChange(values.copy(enabled = it)) })
            ToggleField("Show pending access update", values.pending, { onChange(values.copy(pending = it)) }, enabled = values.enabled)
            Text("Local sharing preview. Invite river@example.test to add River; no invitation is sent.", style = t.typography.caption)
            ActionButton({ onChange(values.resetMembers()) }, variant = ButtonVariant.QUIET, enabled = values.canEdit) { Text("Reset members") }
            Text("Invites: ${values.invites} · Role changes: ${values.roleChanges} · Removals: ${values.removals}", style = t.typography.caption)
        }
        Card {
            SectionHeader("Invite a teammate", subtitle = "Choose the new member's access before adding them.")
            SelectField("Invite as", MemberRole.entries, values.inviteRole, { onChange(values.chooseInviteRole(it)) }, enabled = values.canEdit, label = { it.label })
            InlineActionField("Invite address", values.draft, { onChange(values.editDraft(it)) }, "Add preview member", values.canInvite,
                onSubmit = { val next = values.invite(); onChange(next); if (next.invites > values.invites) focus.clearFocus() },
                enabled = values.enabled, isBusy = values.pending, error = values.error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            ActionButton({ onChange(values.editDraft("river@example.test")) }, variant = ButtonVariant.QUIET, enabled = values.canEdit) { Text("Use River address") }
        }
        ShareLinkCard("Workspace link", if (values.linkAccess == LinkAccess.OFF) null else SharingValues.LINK, "Link access is off", detail = values.linkAccess.detail,
            status = { Badge(values.linkAccess.label, tone = if (values.linkAccess == LinkAccess.OFF) MessageTone.WARNING else MessageTone.INFO) }, actions = {
                SelectField("Link access", LinkAccess.entries, values.linkAccess, { onChange(values.chooseLinkAccess(it)) }, enabled = values.canEdit, label = { it.label })
                ActionButton({
                    if (values.canCopy) {
                        if (copyText != null) copyText(SharingValues.LINK)
                        else context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Workspace link", SharingValues.LINK))
                        onChange(values.copyLink())
                    }
                }, variant = ButtonVariant.SECONDARY, enabled = values.canCopy) { Text("Copy workspace link") }
                Text("Copy requests: ${values.copies}", style = t.typography.caption)
            })
        Card {
            SectionHeader("Workspace members", subtitle = "${values.members.size} ${if (values.members.size == 1) "member" else "members"}")
            values.contacts.forEach { contact -> key(contact.id) {
                MemberRow(contact.name, "${contact.name}, ${contact.email}", detail = contact.email,
                    avatar = { Avatar(contact.name, contact.initials, size = 48.dp) }, access = {
                        if (contact.owner) Badge("Owner · Protected")
                        else SelectField("Access for ${contact.name}", MemberRole.entries, values.role(contact.id), { onChange(values.changeRole(contact.id, it)) },
                            enabled = values.canManage(contact.id), label = { it.label })
                    }, actions = {
                        ActionButton({ removal = values.removal(contact.id); focus.clearFocus() }, variant = ButtonVariant.DESTRUCTIVE, enabled = values.canManage(contact.id)) { Text("Remove ${contact.name}") }
                    })
            } }
        }
    }
    val removalRequest = removal
    ConfirmationDialog("Remove member?", "Remove ${removalRequest?.id?.let(SharingContact::find)?.name ?: "this member"} from the preview workspace?",
        removalRequest != null, { removal = null }, "Remove preview member", "Keep member", destructive = true,
        onConfirm = { removalRequest?.let { onChange(values.remove(it)) }; removal = null })
}
