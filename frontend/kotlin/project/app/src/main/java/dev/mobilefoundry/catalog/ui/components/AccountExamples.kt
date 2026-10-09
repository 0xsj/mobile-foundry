package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.ui.components.display.avatar.Avatar
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.accountswitcher.AccountOption
import dev.mobilefoundry.ui.components.navigation.accountswitcher.AccountSwitcher
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.overlays.dialog.ConfirmationDialog
import dev.mobilefoundry.ui.components.patterns.permissioncard.PermissionCard
import dev.mobilefoundry.ui.components.patterns.profileheader.ProfileHeader
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.patterns.sessionrow.SessionRow
import dev.mobilefoundry.ui.theme.FoundryTheme

internal enum class PermissionScenario(val label: String) { ASK("Ask"), ALLOWED("Allowed"), DENIED("Denied") }
internal data class PreviewSession(val id: String, val title: String, val detail: String, val activity: String, val current: Boolean) {
    companion object { val all = listOf(PreviewSession("phone", "iPhone 17", "iOS · This device", "Active now", true),
        PreviewSession("desktop", "MacBook Pro", "macOS · Desktop browser", "Last active yesterday", false),
        PreviewSession("tablet", "iPad Air", "iPadOS · Tablet app", "Last active October 5", false)) }
}
internal data class AccountValues(val accountID: String = "personal", val enabled: Boolean = true, val removed: List<String> = emptyList(),
    val permission: PermissionScenario = PermissionScenario.ASK, val profilePreviews: Int = 0, val settingsPreviews: Int = 0) {
    companion object { val options = listOf(AccountOption("personal", "Personal", "Mira Chen"), AccountOption("studio", "Studio team", "Shared workspace"),
        AccountOption("invited", "Invited workspace", "Invitation pending", false)) }
    val accountTitle get() = options.firstOrNull { it.id == accountID }?.title ?: "Choose an account"
    val sessions get() = PreviewSession.all.filter { "$accountID:${it.id}" !in removed }
    fun select(id: String) = if (enabled && options.any { it.id == id && it.enabled }) copy(accountID = id) else this
    fun removeSession(id: String, expectedAccountID: String) =
        if (enabled && accountID == expectedAccountID && sessions.any { it.id == id && !it.current }) copy(removed = removed + "$accountID:$id") else this
    fun previewProfile() = if (enabled) copy(profilePreviews = profilePreviews + 1) else this
    fun allowPhotos() = if (enabled && permission == PermissionScenario.ASK) copy(permission = PermissionScenario.ALLOWED) else this
    fun previewSettings() = if (enabled && permission == PermissionScenario.DENIED) copy(settingsPreviews = settingsPreviews + 1) else this
    fun setPermission(value: PermissionScenario) = if (enabled) copy(permission = value) else this
}
@Composable
internal fun AccountExamples(values: AccountValues, onChange: (AccountValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Identity and access", subtitle = "Profiles, account choices, devices and clear permission rationale.")
        NavLink("Open account center", onPreview, subtitle = "Explore a local account and access preview")
    }
    AccountContent(values, onChange)
}
@Composable
internal fun AccountPreviewScreen(values: AccountValues, onChange: (AccountValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Account center", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("account-scroll").padding(20.dp)) {
            AccountContent(values, onChange)
        }
    }
}
private data class SessionRemoval(val accountID: String, val sessionID: String, val title: String)
@Composable
internal fun AccountContent(values: AccountValues, onChange: (AccountValues) -> Unit) {
    val t = FoundryTheme.tokens
    var removal by remember(values.accountID, values.enabled) { mutableStateOf<SessionRemoval?>(null) }
    var showPermission by remember(values.accountID, values.enabled, values.permission) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable account actions", values.enabled, { onChange(values.copy(enabled = it)) })
            Text("Local preview. Device removal and photo access only change this example.", style = t.typography.caption)
            AccountSwitcher("Switch account", AccountValues.options, values.accountID, "Choose an account", { onChange(values.select(it)) }, enabled = values.enabled)
            Text("Active account: ${values.accountTitle}", style = t.typography.caption)
        }
        Card {
            ProfileHeader("Mira Chen", detail = "mira@example.test", avatar = { Avatar("Mira Chen", "MC", size = 64.dp) },
                status = { Badge(if (values.accountID == "studio") "Team member" else "Personal account", tone = MessageTone.INFO) },
                actions = { ActionButton({ onChange(values.previewProfile()) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled) { Text("Preview profile") } })
            Text("Profile previews: ${values.profilePreviews}", style = t.typography.caption)
        }
        PermissionCard("Photos", "Choose a photo for an editor preview. Photo access is shared by these preview accounts.",
            icon = { Icon(painterResource(R.drawable.ic_access_photos), null) },
            status = { Badge("Preview: ${values.permission.label}", tone = if (values.permission == PermissionScenario.DENIED) MessageTone.WARNING else MessageTone.INFO) },
            actions = {
                when (values.permission) {
                    PermissionScenario.ASK -> ActionButton({ showPermission = true }, enabled = values.enabled) { Text("Try photo access") }
                    PermissionScenario.DENIED -> ActionButton({ onChange(values.previewSettings()) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled) { Text("Preview settings") }
                    PermissionScenario.ALLOWED -> Text("The preview can use photos.", style = t.typography.caption)
                }
            })
        Card {
            SelectField("Photo access scenario", PermissionScenario.entries, values.permission, { onChange(values.setPermission(it)) }, enabled = values.enabled, label = { it.label })
            Text("Settings previews: ${values.settingsPreviews}", style = t.typography.caption)
        }
        Card {
            SectionHeader("Devices", subtitle = "Only other devices can be removed in this preview.")
            Text("Devices: ${values.sessions.size}", style = t.typography.caption)
            values.sessions.forEach { session -> key(session.id) {
                SessionRow(session.title, session.activity, detail = session.detail,
                    icon = { Icon(painterResource(when (session.id) { "phone" -> R.drawable.ic_device_phone; "desktop" -> R.drawable.ic_device_desktop; else -> R.drawable.ic_device_tablet }), null) },
                    status = { Badge(if (session.current) "Current device" else "Other device") },
                    actions = {
                        ActionButton({ removal = SessionRemoval(values.accountID, session.id, session.title) },
                            Modifier.semantics { contentDescription = "Remove ${session.title}" }, variant = ButtonVariant.DESTRUCTIVE,
                            enabled = values.enabled && !session.current) { Text("Remove device") }
                    })
            } }
        }
    }
    val intent = removal
    ConfirmationDialog("Remove device?", "Remove ${intent?.title ?: "this device"} from this account preview?", intent != null,
        onDismissRequest = { removal = null }, confirmLabel = "Remove preview device", cancelLabel = "Keep device", destructive = true,
        onConfirm = { if (intent != null) onChange(values.removeSession(intent.sessionID, intent.accountID)) })
    ConfirmationDialog("Allow photo preview?", "This changes the local preview only; it does not request system photo access.", showPermission,
        onDismissRequest = { showPermission = false }, confirmLabel = "Allow preview", cancelLabel = "Not now", onConfirm = { onChange(values.allowPhotos()) })
}
