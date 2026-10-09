package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.components.feedback.transfer.TransferPhase
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.feedback.alert.InlineAlert
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.feedback.progress.ProgressIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.searchfield.SearchField
import dev.mobilefoundry.ui.components.overlays.sheet.SheetPanel
import dev.mobilefoundry.ui.components.overlays.dialog.ConfirmationDialog
import dev.mobilefoundry.ui.components.layout.surface.Backdrop
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.tabs.Tabs
import dev.mobilefoundry.ui.components.forms.timepicker.ClockTime
import dev.mobilefoundry.ui.components.navigation.tablesortheader.TableSortOrder
import dev.mobilefoundry.ui.components.patterns.pageheader.PageHeader
import dev.mobilefoundry.ui.components.patterns.selectioncard.SelectionCard
import dev.mobilefoundry.ui.components.patterns.settingssection.SettingsSection
import dev.mobilefoundry.ui.styles.tokens.FoundryAppearance
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

private enum class ComponentGroup(val label: String) {
    ACTIONS("Actions"), CONTENT("Content"), PATTERNS("Patterns"), CONTROLS("Controls"), OVERLAYS("Overlays"),
    DISPLAY("Display"), FEEDBACK("Feedback"), COLLECTIONS("Collections"), CONTEXT("Context"), LAYOUT("Layout"), DETAILS("Details"), JOURNEYS("Journeys"), ACTIVITY("Activity"), MEDIA("Media"), COMMUNICATION("Communication"), EDITING("Editing"), INSIGHTS("Insights"), SCHEDULING("Scheduling"), WORKSPACE("Workspace"), TABLES("Tables"), ACCOUNT("Account"), DISCOVERY("Discovery"), COMMERCE("Commerce"), NOTIFICATIONS("Notifications"), PLANS("Plans"), FILES("Files"), SHARING("Sharing"), PLAYBACK("Playback"), VERIFICATION("Verification")
}

@Composable
fun ComponentCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var glass by rememberSaveable { mutableStateOf(true) }
    var details by rememberSaveable { mutableStateOf(false) }
    var deliveryPreview by rememberSaveable { mutableStateOf(false) }
    var journeyRoute by rememberSaveable { mutableIntStateOf(0) }
    val journeyPassword = remember { TextFieldState() }
    val journeyBio = rememberTextFieldState()
    var journeyStep by rememberSaveable { mutableIntStateOf(0) }
    var journeyEnabled by rememberSaveable { mutableStateOf(true) }
    var journeyUpdates by rememberSaveable { mutableStateOf(true) }
    var journeyFinished by rememberSaveable { mutableStateOf(false) }
    var accountPreviews by rememberSaveable { mutableIntStateOf(0) }
    var finishedPreviews by rememberSaveable { mutableIntStateOf(0) }
    val journey = JourneyValues(journeyPassword, journeyBio, journeyStep, journeyEnabled, journeyUpdates,
        journeyFinished, accountPreviews, finishedPreviews)
    val onJourney: (JourneyValues) -> Unit = {
        journeyStep = it.step; journeyEnabled = it.enabled; journeyUpdates = it.updates
        journeyFinished = it.finished; accountPreviews = it.accountPreviews; finishedPreviews = it.finishedPreviews
    }
    var deliveryFormat by rememberSaveable { mutableStateOf("Full size") }
    var deliveryCopies by rememberSaveable { mutableIntStateOf(0) }
    var deliveryNote by rememberSaveable { mutableStateOf("Handle with care.") }
    var deliveryExpanded by rememberSaveable { mutableStateOf(false) }
    var deliveryEnabled by rememberSaveable { mutableStateOf(true) }
    var deliveryApplied by rememberSaveable { mutableIntStateOf(0) }
    val delivery = DeliveryValues(deliveryFormat, deliveryCopies, deliveryNote, deliveryExpanded, deliveryEnabled, deliveryApplied)
    val onDelivery: (DeliveryValues) -> Unit = {
        deliveryFormat = it.format; deliveryCopies = it.copies; deliveryNote = it.note
        deliveryExpanded = it.expanded; deliveryEnabled = it.enabled; deliveryApplied = it.applied
    }
    val activity = remember { ActivityPreviewValues() }
    var activityPreview by rememberSaveable { mutableStateOf(false) }
    var mediaPreview by rememberSaveable { mutableStateOf(false) }
    val mediaPager = rememberPagerState { MediaStudy.all.size }
    var mediaRatings by rememberSaveable { mutableStateOf(emptyMap<String, Int>()) }
    var mediaFavorites by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var mediaEnabled by rememberSaveable { mutableStateOf(true) }
    var mediaUsed by rememberSaveable { mutableIntStateOf(0) }
    val media = MediaValues(mediaRatings, mediaFavorites, mediaEnabled, mediaUsed)
    val onMedia: (MediaValues) -> Unit = {
        mediaRatings = it.ratings; mediaFavorites = it.favorites; mediaEnabled = it.enabled; mediaUsed = it.used
    }
    var communicationPreview by rememberSaveable { mutableStateOf(false) }
    val communicationDraft = rememberTextFieldState()
    var communicationAttached by rememberSaveable { mutableStateOf(false) }
    var communicationPhase by rememberSaveable { mutableStateOf(TransferPhase.WAITING) }
    var communicationEnabled by rememberSaveable { mutableStateOf(true) }
    var communicationTyping by rememberSaveable { mutableStateOf(true) }
    var communicationMessages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var communicationAttachments by rememberSaveable { mutableStateOf(emptyList<Boolean>()) }
    var communicationInspected by rememberSaveable { mutableIntStateOf(0) }
    val communication = CommunicationValues(communicationDraft, communicationAttached, communicationPhase, communicationEnabled,
        communicationTyping, communicationMessages, communicationAttachments, communicationInspected)
    val onCommunication: (CommunicationValues) -> Unit = {
        communicationAttached = it.attached; communicationPhase = it.phase; communicationEnabled = it.enabled
        communicationTyping = it.typing; communicationMessages = it.messages; communicationAttachments = it.messageAttachments
        communicationInspected = it.inspected
    }
    var editingPreview by rememberSaveable { mutableStateOf(false) }
    var editingTagDraft by rememberSaveable { mutableStateOf("") }
    var editingTags by rememberSaveable { mutableStateOf(listOf("Sketch", "Review")) }
    var editingSearch by rememberSaveable { mutableStateOf("") }
    var editingSelected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingArchived by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingRemoved by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingUndo by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingUndoSelection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingEnabled by rememberSaveable { mutableStateOf(true) }
    val editing = EditingValues(editingTagDraft, editingTags, editingSearch, editingSelected, editingArchived,
        editingRemoved, editingUndo, editingUndoSelection, editingEnabled)
    val onEditing: (EditingValues) -> Unit = {
        editingTagDraft = it.tagDraft; editingTags = it.tags; editingSearch = it.search; editingSelected = it.selected
        editingArchived = it.archived; editingRemoved = it.removed; editingUndo = it.undoIDs
        editingUndoSelection = it.undoSelection; editingEnabled = it.enabled
    }
    var insightsPreview by rememberSaveable { mutableStateOf(false) }
    var insightPeriod by rememberSaveable { mutableStateOf(InsightPeriod.WEEK) }
    var insightCompleted by rememberSaveable { mutableIntStateOf(14) }
    var insightEnabled by rememberSaveable { mutableStateOf(true) }
    var insightEmpty by rememberSaveable { mutableStateOf(false) }
    val insights = InsightsValues(insightPeriod, insightCompleted, insightEnabled, insightEmpty)
    val onInsights: (InsightsValues) -> Unit = {
        insightPeriod = it.period; insightCompleted = it.completed; insightEnabled = it.enabled; insightEmpty = it.empty
    }
    var schedulingPreview by rememberSaveable { mutableStateOf(false) }
    var scheduleSelected by rememberSaveable { mutableStateOf<String?>("0") }
    var scheduleStart by rememberSaveable { mutableLongStateOf(SchedulingValues.date(0)) }
    var scheduleEnd by rememberSaveable { mutableLongStateOf(SchedulingValues.date(6)) }
    var scheduleHour by rememberSaveable { mutableIntStateOf(9) }
    var scheduleMinute by rememberSaveable { mutableIntStateOf(30) }
    var scheduleEnabled by rememberSaveable { mutableStateOf(true) }
    var scheduleEmpty by rememberSaveable { mutableStateOf(false) }
    var scheduleAppliedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleAppliedHour by rememberSaveable { mutableIntStateOf(9) }
    var scheduleAppliedMinute by rememberSaveable { mutableIntStateOf(30) }
    var scheduleApplied by rememberSaveable { mutableIntStateOf(0) }
    val scheduling = SchedulingValues(scheduleSelected, scheduleStart, scheduleEnd, ClockTime(scheduleHour, scheduleMinute),
        scheduleEnabled, scheduleEmpty, scheduleAppliedDay, ClockTime(scheduleAppliedHour, scheduleAppliedMinute), scheduleApplied)
    val onScheduling: (SchedulingValues) -> Unit = {
        scheduleSelected = it.selected; scheduleStart = it.start; scheduleEnd = it.end
        scheduleHour = it.time.hour; scheduleMinute = it.time.minute; scheduleEnabled = it.enabled; scheduleEmpty = it.empty
        scheduleAppliedDay = it.appliedDay; scheduleAppliedHour = it.appliedTime.hour; scheduleAppliedMinute = it.appliedTime.minute
        scheduleApplied = it.applied
    }
    val stateHolder = rememberSaveableStateHolder()
    var verificationPreview by rememberSaveable { mutableStateOf(false) }
    var verifyChannel by rememberSaveable { mutableStateOf(VerificationChannel.EMAIL) }
    var verifyResponse by rememberSaveable { mutableStateOf(VerificationResponse.MATCH_CODE) }
    var verifyGeneration by rememberSaveable { mutableIntStateOf(1) }
    var verifyCooldown by rememberSaveable { mutableIntStateOf(30) }
    var verifyExpires by rememberSaveable { mutableIntStateOf(120) }
    var verifyEnabled by rememberSaveable { mutableStateOf(true) }
    var verifyAttempts by rememberSaveable { mutableIntStateOf(0) }
    var verifyResends by rememberSaveable { mutableIntStateOf(0) }
    // Code, attempt and result are transient. Recreation does not replay a check or restore a code.
    var verifyDraft by remember { mutableStateOf("") }
    var verifyRequest by remember { mutableStateOf<VerificationAttempt?>(null) }
    var verifyIssue by remember { mutableStateOf<VerificationIssue?>(null) }
    var verifyVerified by remember { mutableStateOf(false) }
    val verification = VerificationValues(verifyChannel, verifyDraft, verifyResponse, verifyGeneration, verifyCooldown,
        verifyExpires, verifyEnabled, verifyVerified, verifyRequest, verifyIssue, verifyAttempts, verifyResends)
    val onVerification: (VerificationValues) -> Unit = {
        verifyChannel = it.channel; verifyDraft = it.draft; verifyResponse = it.response; verifyGeneration = it.generation
        verifyCooldown = it.cooldown; verifyExpires = it.expiresIn; verifyEnabled = it.enabled; verifyVerified = it.verified
        verifyRequest = it.request; verifyIssue = it.issue; verifyAttempts = it.attempts; verifyResends = it.resends
    }
    var playbackPreview by rememberSaveable { mutableStateOf(false) }
    var playSelected by rememberSaveable { mutableStateOf("coast") }
    var playPositions by rememberSaveable { mutableStateOf(emptyMap<String, Double>()) }
    var playFavorites by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var playIsPlaying by rememberSaveable { mutableStateOf(false) }
    var playSpeed by rememberSaveable { mutableStateOf(PlaybackSpeed.NORMAL) }
    var playRepeat by rememberSaveable { mutableStateOf(false) }
    var playScenario by rememberSaveable { mutableStateOf(PlaybackScenario.READY) }
    var playEnabled by rememberSaveable { mutableStateOf(true) }
    var playEmpty by rememberSaveable { mutableStateOf(false) }
    var playTrackChanges by rememberSaveable { mutableIntStateOf(0) }
    var playSeeks by rememberSaveable { mutableIntStateOf(0) }
    var playAdvances by rememberSaveable { mutableIntStateOf(0) }
    val playback = PlaybackValues(playSelected, playPositions, playFavorites, playIsPlaying, playSpeed, playRepeat,
        playScenario, playEnabled, playEmpty, playTrackChanges, playSeeks, playAdvances)
    val onPlayback: (PlaybackValues) -> Unit = {
        playSelected = it.selectedID; playPositions = it.positions; playFavorites = it.favorites; playIsPlaying = it.isPlaying
        playSpeed = it.speed; playRepeat = it.repeatTrack; playScenario = it.scenario; playEnabled = it.enabled
        playEmpty = it.empty; playTrackChanges = it.trackChanges; playSeeks = it.seeks; playAdvances = it.advances
    }
    var sharingPreview by rememberSaveable { mutableStateOf(false) }
    var shareMembers by rememberSaveable { mutableStateOf(listOf("alex", "jamie", "sam")) }
    var shareRoles by rememberSaveable { mutableStateOf(mapOf("jamie" to MemberRole.EDITOR, "sam" to MemberRole.VIEWER)) }
    var shareDraft by rememberSaveable { mutableStateOf("") }
    var shareInviteRole by rememberSaveable { mutableStateOf(MemberRole.VIEWER) }
    var shareError by rememberSaveable { mutableStateOf<String?>(null) }
    var shareLinkAccess by rememberSaveable { mutableStateOf(LinkAccess.VIEW) }
    var shareEnabled by rememberSaveable { mutableStateOf(true) }
    var sharePending by rememberSaveable { mutableStateOf(false) }
    var shareRevision by rememberSaveable { mutableIntStateOf(0) }
    var shareInvites by rememberSaveable { mutableIntStateOf(0) }
    var shareRoleChanges by rememberSaveable { mutableIntStateOf(0) }
    var shareRemovals by rememberSaveable { mutableIntStateOf(0) }
    var shareCopies by rememberSaveable { mutableIntStateOf(0) }
    val sharing = SharingValues(shareMembers, shareRoles, shareDraft, shareInviteRole, shareError, shareLinkAccess,
        shareEnabled, sharePending, shareRevision, shareInvites, shareRoleChanges, shareRemovals, shareCopies)
    val onSharing: (SharingValues) -> Unit = {
        shareMembers = it.members; shareRoles = it.roles; shareDraft = it.draft; shareInviteRole = it.inviteRole; shareError = it.error
        shareLinkAccess = it.linkAccess; shareEnabled = it.enabled; sharePending = it.pending; shareRevision = it.revision
        shareInvites = it.invites; shareRoleChanges = it.roleChanges; shareRemovals = it.removals; shareCopies = it.copies
    }
    var filesPreview by rememberSaveable { mutableStateOf(false) }
    var fileQuery by rememberSaveable { mutableStateOf("") }
    var fileExpanded by rememberSaveable { mutableStateOf(listOf("atlas", "studies")) }
    var fileFavorites by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var fileSelected by rememberSaveable { mutableStateOf<String?>(null) }
    var fileOpens by rememberSaveable { mutableIntStateOf(0) }
    var fileEnabled by rememberSaveable { mutableStateOf(true) }
    var fileEmpty by rememberSaveable { mutableStateOf(false) }
    val files = FileBrowserValues(fileQuery, fileExpanded, fileFavorites, fileSelected, fileOpens, fileEnabled, fileEmpty)
    val onFiles: (FileBrowserValues) -> Unit = {
        fileQuery = it.query; fileExpanded = it.expanded; fileFavorites = it.favorites; fileSelected = it.selected
        fileOpens = it.opens; fileEnabled = it.enabled; fileEmpty = it.empty
    }
    var plansPreview by rememberSaveable { mutableStateOf(false) }
    var planSelected by rememberSaveable { mutableStateOf(PlanTier.STARTER) }
    var planCycle by rememberSaveable { mutableStateOf(PlanCycle.MONTHLY) }
    var planCurrent by rememberSaveable { mutableStateOf(PlanTier.STARTER) }
    var planCurrentCycle by rememberSaveable { mutableStateOf(PlanCycle.MONTHLY) }
    var planUsed by rememberSaveable { mutableIntStateOf(3) }
    var planEnabled by rememberSaveable { mutableStateOf(true) }
    var planPending by rememberSaveable { mutableStateOf(false) }
    var planReviewed by rememberSaveable { mutableStateOf<PlanTier?>(null) }
    var planReviewedCycle by rememberSaveable { mutableStateOf(PlanCycle.MONTHLY) }
    var planReviews by rememberSaveable { mutableIntStateOf(0) }
    var planApplied by rememberSaveable { mutableIntStateOf(0) }
    val plans = PlanValues(planSelected, planCycle, planCurrent, planCurrentCycle, planUsed, planEnabled, planPending,
        planReviewed, planReviewedCycle, planReviews, planApplied)
    val onPlans: (PlanValues) -> Unit = {
        planSelected = it.selected; planCycle = it.cycle; planCurrent = it.current; planCurrentCycle = it.currentCycle
        planUsed = it.used; planEnabled = it.enabled; planPending = it.pending; planReviewed = it.reviewed
        planReviewedCycle = it.reviewedCycle; planReviews = it.reviews; planApplied = it.applied
    }
    var inboxPreview by rememberSaveable { mutableStateOf(false) }
    var inboxFilter by rememberSaveable { mutableStateOf(InboxFilter.ALL) }
    var inboxRead by rememberSaveable { mutableStateOf(listOf("export")) }
    var inboxArchived by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var inboxUndo by rememberSaveable { mutableStateOf<String?>(null) }
    var inboxOpened by rememberSaveable { mutableStateOf<String?>(null) }
    var inboxOpens by rememberSaveable { mutableIntStateOf(0) }
    var inboxEnabled by rememberSaveable { mutableStateOf(true) }
    val inbox = NotificationValues(inboxFilter, inboxRead, inboxArchived, inboxUndo, inboxOpened, inboxOpens, inboxEnabled)
    val onInbox: (NotificationValues) -> Unit = {
        inboxFilter = it.filter; inboxRead = it.read; inboxArchived = it.archived; inboxUndo = it.undoID
        inboxOpened = it.opened; inboxOpens = it.opens; inboxEnabled = it.enabled
    }
    var commercePreview by rememberSaveable { mutableStateOf(false) }
    var cartKit by rememberSaveable { mutableIntStateOf(1) }
    var cartNotebook by rememberSaveable { mutableIntStateOf(0) }
    var cartDelivery by rememberSaveable { mutableStateOf(CartDelivery.SHIP) }
    var cartCode by rememberSaveable { mutableStateOf("") }
    var cartDiscounted by rememberSaveable { mutableStateOf(false) }
    var cartError by rememberSaveable { mutableStateOf<String?>(null) }
    var cartEnabled by rememberSaveable { mutableStateOf(true) }
    var cartCodeBusy by rememberSaveable { mutableStateOf(false) }
    var cartReviews by rememberSaveable { mutableIntStateOf(0) }
    var cartReviewedTotal by rememberSaveable { mutableStateOf<Int?>(null) }
    var cartReviewedItems by rememberSaveable { mutableStateOf<Int?>(null) }
    val commerce = CommerceValues(cartKit, cartNotebook, cartDelivery, cartCode, cartDiscounted, cartError, cartEnabled,
        cartCodeBusy, cartReviews, cartReviewedTotal, cartReviewedItems)
    val onCommerce: (CommerceValues) -> Unit = {
        cartKit = it.kit; cartNotebook = it.notebook; cartDelivery = it.delivery; cartCode = it.code; cartDiscounted = it.discounted
        cartError = it.error; cartEnabled = it.enabled; cartCodeBusy = it.codeBusy; cartReviews = it.reviews
        cartReviewedTotal = it.reviewedTotal; cartReviewedItems = it.reviewedItems
    }
    if (commercePreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            CommercePreviewScreen(commerce, onCommerce, onBack = { commercePreview = false })
        }
        return
    }
    var discoveryPreview by rememberSaveable { mutableStateOf(false) }
    var discoveryQuery by rememberSaveable { mutableStateOf("") }
    var discoveryTopic by rememberSaveable { mutableStateOf(DiscoveryTopic.ALL) }
    var discoveryArchived by rememberSaveable { mutableStateOf(false) }
    var discoveryEnabled by rememberSaveable { mutableStateOf(true) }
    var discoverySaved by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var discoveryRecent by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var discoveryOpened by rememberSaveable { mutableStateOf<String?>(null) }
    var discoveryOpens by rememberSaveable { mutableIntStateOf(0) }
    val discovery = DiscoveryValues(discoveryQuery, DiscoveryFilters(discoveryTopic, discoveryArchived), discoveryEnabled,
        discoverySaved, discoveryRecent, discoveryOpened, discoveryOpens)
    val onDiscovery: (DiscoveryValues) -> Unit = {
        discoveryQuery = it.query; discoveryTopic = it.filters.topic; discoveryArchived = it.filters.archived
        discoveryEnabled = it.enabled; discoverySaved = it.saved; discoveryRecent = it.recent
        discoveryOpened = it.opened; discoveryOpens = it.opens
    }
    if (discoveryPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            DiscoveryPreviewScreen(discovery, onDiscovery, onBack = { discoveryPreview = false })
        }
        return
    }
    var accountPreview by rememberSaveable { mutableStateOf(false) }
    var accountID by rememberSaveable { mutableStateOf("personal") }
    var accountEnabled by rememberSaveable { mutableStateOf(true) }
    var accountRemoved by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var accountPermission by rememberSaveable { mutableStateOf(PermissionScenario.ASK) }
    var accountProfilePreviews by rememberSaveable { mutableIntStateOf(0) }
    var accountSettingsPreviews by rememberSaveable { mutableIntStateOf(0) }
    val account = AccountValues(accountID, accountEnabled, accountRemoved, accountPermission, accountProfilePreviews, accountSettingsPreviews)
    val onAccount: (AccountValues) -> Unit = {
        accountID = it.accountID; accountEnabled = it.enabled; accountRemoved = it.removed; accountPermission = it.permission
        accountProfilePreviews = it.profilePreviews; accountSettingsPreviews = it.settingsPreviews
    }
    if (accountPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            AccountPreviewScreen(account, onAccount, onBack = { accountPreview = false })
        }
        return
    }
    var tablePreview by rememberSaveable { mutableStateOf(false) }
    var tableSort by rememberSaveable { mutableStateOf(TableSortKey.PROJECT) }
    var tableOrder by rememberSaveable { mutableStateOf(TableSortOrder.ASCENDING) }
    var tablePage by rememberSaveable { mutableIntStateOf(1) }
    var tableEnabled by rememberSaveable { mutableStateOf(true) }
    var tableEmpty by rememberSaveable { mutableStateOf(false) }
    var tableInspected by rememberSaveable { mutableStateOf<String?>(null) }
    var tableInspections by rememberSaveable { mutableIntStateOf(0) }
    val tables = TableValues(tableSort, tableOrder, tablePage, tableEnabled, tableEmpty, tableInspected, tableInspections)
    val onTables: (TableValues) -> Unit = {
        tableSort = it.sortKey; tableOrder = it.order; tablePage = it.page; tableEnabled = it.enabled
        tableEmpty = it.empty; tableInspected = it.inspected; tableInspections = it.inspections
    }
    if (tablePreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            TablePreviewScreen(tables, onTables, onBack = { tablePreview = false })
        }
        return
    }
    var workspacePreview by rememberSaveable { mutableStateOf(false) }
    var workspaceDestination by rememberSaveable { mutableStateOf(WorkspaceDestination.ALL) }
    var workspaceSelected by rememberSaveable { mutableStateOf<String?>(null) }
    var workspaceDetail by rememberSaveable { mutableStateOf(false) }
    var workspaceStars by rememberSaveable { mutableStateOf(listOf("atlas")) }
    var workspaceSingle by rememberSaveable { mutableStateOf(false) }
    val workspace = WorkspaceValues(workspaceDestination, workspaceSelected, workspaceDetail, workspaceStars, workspaceSingle)
    val onWorkspace: (WorkspaceValues) -> Unit = {
        workspaceDestination = it.destination; workspaceSelected = it.selected; workspaceDetail = it.detailPresented
        workspaceStars = it.starred; workspaceSingle = it.forceSingle
    }
    if (workspacePreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            WorkspacePreviewScreen(workspace, onWorkspace, onBack = { workspacePreview = false })
        }
        return
    }
    if (schedulingPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            SchedulingPreviewScreen(scheduling, onScheduling, onBack = { schedulingPreview = false })
        }
        return
    }
    if (insightsPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            InsightsPreviewScreen(insights, onInsights, onBack = { insightsPreview = false })
        }
        return
    }
    if (editingPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            LibraryEditingScreen(editing, onEditing, onBack = { editingPreview = false })
        }
        return
    }
    if (communicationPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            ConversationPreviewScreen(communication, onCommunication, onBack = { communicationPreview = false })
        }
        return
    }
    if (mediaPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            MediaPreviewScreen(media, onMedia, mediaPager, onBack = { mediaPreview = false })
        }
        return
    }
    if (activityPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            ActivityPreviewScreen(activity, onBack = { activityPreview = false })
        }
        return
    }
    if (journeyRoute != 0) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            JourneyDestination(journey, onJourney, account = journeyRoute == 1, onBack = { journeyRoute = 0 })
        }
        return
    }
    if (deliveryPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            DeliveryPreviewScreen(delivery, onDelivery, onBack = { deliveryPreview = false })
        }
        return
    }
    if (details) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            ComponentDetailScreen(onBack = { details = false })
        }
        return
    }
    // Keep all earlier family owners composed while the inbox destination is open.
    if (inboxPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            NotificationPreviewScreen(inbox, onInbox, onBack = { inboxPreview = false })
        }
        return
    }
    // Plan choices must not remove earlier family owners from composition.
    if (plansPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            PlanPreviewScreen(plans, onPlans, onBack = { plansPreview = false })
        }
        return
    }
    // Keep the other family owners composed during file navigation.
    if (filesPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            FilePreviewScreen(files, onFiles, onBack = { filesPreview = false })
        }
        return
    }
    // Sharing must retain earlier family state during its destination.
    if (sharingPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            SharingPreviewScreen(sharing, onSharing, onBack = { sharingPreview = false })
        }
        return
    }
    // Retain earlier family owners while the local player destination is open.
    if (playbackPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            PlaybackPreviewScreen(playback, onPlayback, onBack = { playbackPreview = false })
        }
        return
    }
    // Keep earlier owners composed; verification's sensitive draft/attempt stay transient.
    if (verificationPreview) {
        FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
            style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
            VerificationPreviewScreen(verification, onVerification, onBack = { verificationPreview = false })
        }
        return
    }
    stateHolder.SaveableStateProvider("componentExamples") {
        Column(modifier.fillMaxSize()) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Components", style = FoundryTheme.tokens.typography.title)
            Row {
                FilterChip(dark, { dark = !dark }, label = { Text("Dark preview") })
                Spacer(Modifier.width(FoundryTheme.tokens.space.inline))
                FilterChip(glass, { glass = !glass }, label = { Text("Glass preview") })
            }
            FoundryTheme(appearance = if (dark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
                style = if (glass) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
                ComponentExamples(onNavigate = { details = true }, delivery, onDelivery,
                    onDeliveryPreview = { deliveryPreview = true }, journey, onJourney,
                    onJourneyRoute = { journeyRoute = it }, activity = activity, onActivityPreview = { activityPreview = true },
                    media = media, onMedia = onMedia, mediaPager = mediaPager, onMediaPreview = { mediaPreview = true },
                    communication = communication, onCommunication = onCommunication, onCommunicationPreview = { communicationPreview = true },
                    editing = editing, onEditing = onEditing, onEditingPreview = { editingPreview = true },
                    insights = insights, onInsights = onInsights, onInsightsPreview = { insightsPreview = true },
                    scheduling = scheduling, onScheduling = onScheduling, onSchedulingPreview = { schedulingPreview = true },
                    workspace = workspace, onWorkspace = onWorkspace, onWorkspacePreview = { workspacePreview = true },
                    tables = tables, onTables = onTables, onTablePreview = { tablePreview = true },
                    account = account, onAccount = onAccount, onAccountPreview = { accountPreview = true },
                    discovery = discovery, onDiscovery = onDiscovery, onDiscoveryPreview = { discoveryPreview = true },
                    commerce = commerce, onCommerce = onCommerce, onCommercePreview = { commercePreview = true },
                    inbox = inbox, onInbox = onInbox, onInboxPreview = { inboxPreview = true },
                    plans = plans, onPlans = onPlans, onPlansPreview = { plansPreview = true },
                    files = files, onFiles = onFiles, onFilesPreview = { filesPreview = true },
                    sharing = sharing, onSharing = onSharing, onSharingPreview = { sharingPreview = true },
                    playback = playback, onPlayback = onPlayback, onPlaybackPreview = { playbackPreview = true },
                    verification = verification, onVerification = onVerification, onVerificationPreview = { verificationPreview = true }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentExamples(onNavigate: () -> Unit, delivery: DeliveryValues, onDelivery: (DeliveryValues) -> Unit,
    onDeliveryPreview: () -> Unit, journey: JourneyValues, onJourney: (JourneyValues) -> Unit,
    onJourneyRoute: (Int) -> Unit, activity: ActivityPreviewValues, onActivityPreview: () -> Unit, media: MediaValues, onMedia: (MediaValues) -> Unit,
    mediaPager: PagerState, onMediaPreview: () -> Unit, communication: CommunicationValues,
    onCommunication: (CommunicationValues) -> Unit, onCommunicationPreview: () -> Unit, editing: EditingValues,
    onEditing: (EditingValues) -> Unit, onEditingPreview: () -> Unit, insights: InsightsValues,
    onInsights: (InsightsValues) -> Unit, onInsightsPreview: () -> Unit, scheduling: SchedulingValues,
    onScheduling: (SchedulingValues) -> Unit, onSchedulingPreview: () -> Unit, workspace: WorkspaceValues,
    onWorkspace: (WorkspaceValues) -> Unit, onWorkspacePreview: () -> Unit, tables: TableValues,
    onTables: (TableValues) -> Unit, onTablePreview: () -> Unit, account: AccountValues,
    onAccount: (AccountValues) -> Unit, onAccountPreview: () -> Unit, discovery: DiscoveryValues,
    onDiscovery: (DiscoveryValues) -> Unit, onDiscoveryPreview: () -> Unit, commerce: CommerceValues,
    onCommerce: (CommerceValues) -> Unit, onCommercePreview: () -> Unit, inbox: NotificationValues,
    onInbox: (NotificationValues) -> Unit, onInboxPreview: () -> Unit, plans: PlanValues,
    onPlans: (PlanValues) -> Unit, onPlansPreview: () -> Unit, files: FileBrowserValues,
    onFiles: (FileBrowserValues) -> Unit, onFilesPreview: () -> Unit, sharing: SharingValues,
    onSharing: (SharingValues) -> Unit, onSharingPreview: () -> Unit, playback: PlaybackValues,
    onPlayback: (PlaybackValues) -> Unit, onPlaybackPreview: () -> Unit, verification: VerificationValues,
    onVerification: (VerificationValues) -> Unit, onVerificationPreview: () -> Unit, modifier: Modifier = Modifier) {
    val t = FoundryTheme.tokens
    var group by rememberSaveable { mutableStateOf(ComponentGroup.ACTIONS) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var quality by rememberSaveable { mutableStateOf("Balanced") }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var removed by rememberSaveable { mutableStateOf(false) }
    var activityUpdates by rememberSaveable { mutableStateOf(true) }
    var photos by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf("Original") }
    var destination by rememberSaveable { mutableStateOf("This device") }
    var intensity by rememberSaveable { mutableFloatStateOf(0.5f) }
    var reviewDate by rememberSaveable { mutableLongStateOf(1791417600000L) } // 2026-10-08 UTC calendar date
    var workspaceSheet by rememberSaveable { mutableStateOf(false) }
    var resetCopies by rememberSaveable { mutableStateOf(false) }
    var copies by rememberSaveable { mutableIntStateOf(0) }
    var lastAction by rememberSaveable { mutableStateOf("No action yet") }
    var artwork by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) } // Transient presentation is not replayed after restoration.
    var undos by rememberSaveable { mutableIntStateOf(0) }
    var retries by rememberSaveable { mutableIntStateOf(0) }
    var collectionSearch by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var collectionSort by rememberSaveable { mutableStateOf("Name") }
    var selectedProjects by rememberSaveable { mutableStateOf(listOf<String>()) }
    var workspaceTitle by rememberSaveable { mutableStateOf("Atlas") }
    var ownerName by rememberSaveable { mutableStateOf("Jordan") }
    var fieldsValidated by rememberSaveable { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var choices by rememberSaveable { mutableIntStateOf(0) }
    var narrow by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf("None") }
    val projects = listOf("Atlas workspace", "Orbit study", "Field notes")
    Backdrop(modifier.fillMaxWidth(), background = {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.surfaceGround.color,
            t.colors.accentTint.color, t.colors.surfaceGround.color))))
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(t.space.page),
            verticalArrangement = Arrangement.spacedBy(t.space.section)) {
            PageHeader("Everyday interfaces", "Simple controls, useful compositions, and room for your own content.") {
                Badge("108 building blocks", tone = MessageTone.INFO)
            }
            Tabs("Component families", ComponentGroup.entries, group,
                { group = it; notice = 0; help = false; options = false }, label = { it.label })
            when (group) {
                ComponentGroup.ACTIONS -> {
                    Card(role = SurfaceRole.FLOATING) {
                        Heading("Actions")
                        ActionButton({ count++ }, Modifier.fillMaxWidth(), isBusy = busy) { Text(if (busy) "Saving changes…" else "Save changes") }
                        ActionButton({ count++ }, variant = ButtonVariant.SECONDARY) { Text("Preview") }
                        ActionButton({ count++ }, variant = ButtonVariant.QUIET) { Text("Learn more") }
                        ActionButton({ count++ }, enabled = false) { Text("Unavailable") }
                        ActionButton({ confirmDelete = true }, variant = ButtonVariant.DESTRUCTIVE) { Text("Remove example") }
                        ListRow("Show busy state", trailing = {
                            Switch(busy, { busy = it }, Modifier.semantics { contentDescription = "Show busy state" })
                        })
                        Text("Actions performed: $count", style = t.typography.caption)
                        if (removed) Badge("Example item removed", tone = MessageTone.WARNING)
                    }
                    Card {
                        Heading("Search a collection")
                        SearchField("Search projects", search, { search = it }, "Clear search", Modifier.fillMaxWidth())
                        val matches = projects.filter { search.isEmpty() || it.contains(search, ignoreCase = true) }
                        if (matches.isEmpty()) {
                            EmptyState("No matching projects", "Try another name or clear your search.", actions = {
                                ActionButton({ search = "" }, variant = ButtonVariant.SECONDARY) { Text("Reset search") }
                            })
                        } else matches.forEach { ListRow(it, "Personal project") }
                    }
                }
                ComponentGroup.CONTENT -> {
                    Card {
                        Heading("Status and rows")
                        // A column lets long/localized labels grow without horizontal clipping.
                        Badge("Draft"); Badge("Ready", tone = MessageTone.INFO)
                        Badge("Pending", tone = MessageTone.WARNING); Badge("Needs attention", tone = MessageTone.CRITICAL)
                        HorizontalDivider()
                        ListRow("Atlas workspace", "A place for your next idea", Modifier.clickable { showDetails = true })
                        HorizontalDivider()
                        ListRow("Storage", "This device", trailing = { Badge("Local") })
                    }
                    InlineAlert("Changes stay on this device", "Connect an account when you are ready to share your workspace.") {
                        ActionButton({ showDetails = true }, variant = ButtonVariant.QUIET) { Text("Learn about accounts") }
                    }
                    InlineAlert("Review before continuing", "Some changes still need your attention.", tone = MessageTone.WARNING)
                    InlineAlert("Could not complete the action", "Your draft is still available. Try again when you are ready.", tone = MessageTone.CRITICAL)
                    Card {
                        ProgressIndicator("Preparing preview", fraction = 0.65f)
                        ProgressIndicator("Waiting for connection")
                    }
                    Card {
                        EmptyState("Your collection starts here", "Add an idea, an image, or a project to make it yours.", actions = {
                            ActionButton({ count++ }) { Text("Create something") }
                        })
                    }
                }
                ComponentGroup.PATTERNS -> {
                    SettingsSection("Preferences", footer = "Settings are examples and stay in this gallery.", role = SurfaceRole.FLOATING) {
                        ListRow("Notifications", trailing = {
                            Switch(notifications, { notifications = it }, Modifier.semantics { contentDescription = "Notifications" })
                        })
                        HorizontalDivider()
                        ListRow("Appearance", "Follows your preview settings", trailing = {
                            Badge(if (t.appearance == FoundryAppearance.DARK) "Dark" else "Light")
                        })
                    }
                    Heading("Preview quality")
                    listOf("Balanced", "Detailed").forEach { option ->
                        SelectionCard(quality == option, { quality = option }) {
                            Text(option, style = t.typography.label)
                            Text(if (option == "Balanced") "A lighter preview for everyday work." else "More detail when you need a closer look.", color = t.colors.inkSecondary.color)
                        }
                    }
                    Text("Selected quality: $quality", style = t.typography.caption)
                    Card(role = SurfaceRole.FLOATING) {
                        PageHeader("Atlas workspace", "A page header can carry your own actions.") {
                            ActionButton({ showDetails = true }, variant = ButtonVariant.SECONDARY) { Text("Open details") }
                        }
                    }
                }
                ComponentGroup.CONTROLS -> {
                    ControlExamples(activityUpdates, { activityUpdates = it },
                        photos, { photos = it }, notes, { notes = it }, format, { format = it },
                        destination, { destination = it }, intensity, { intensity = it }, reviewDate, { reviewDate = it })
                    FieldGroupExample(workspaceTitle, { workspaceTitle = it }, ownerName, { ownerName = it }, fieldsValidated, { fieldsValidated = true })
                }
                ComponentGroup.OVERLAYS -> OverlayExamples(copies, lastAction,
                    onShowSheet = { workspaceSheet = true }, onResetRequest = { resetCopies = true },
                    onDuplicate = { copies++; lastAction = "Workspace duplicated" })
                ComponentGroup.DISPLAY -> DisplayExamples(artwork, { artwork = it })
                ComponentGroup.FEEDBACK -> FeedbackExamples(loading, { loading = it }, reduced, { reduced = it },
                    notice, { notice = it }, undos, retries, { undos++ }, { retries++ })
                ComponentGroup.COLLECTIONS -> CollectionExamples(collectionSearch, { collectionSearch = it }, favorites, { favorites = it },
                    collectionSort, { collectionSort = it }, selectedProjects, { selectedProjects = it })
                ComponentGroup.CONTEXT -> ContextExamples(help, { help = it }, options, { options = it }, choices,
                    { choices++ }, { help = false; options = false; onNavigate() })
                ComponentGroup.LAYOUT -> LayoutExamples(narrow, { narrow = it }, picked, { picked = it })
                ComponentGroup.DETAILS -> DetailsExamples(delivery, onDelivery, onDeliveryPreview)
                ComponentGroup.JOURNEYS -> JourneyExamples(journey, onJourney, onJourneyRoute)
                ComponentGroup.ACTIVITY -> ActivityExamples(activity, onActivityPreview)
                ComponentGroup.MEDIA -> MediaExamples(media, onMedia, mediaPager, onMediaPreview)
                ComponentGroup.COMMUNICATION -> CommunicationExamples(communication, onCommunication, onCommunicationPreview)
                ComponentGroup.EDITING -> EditingExamples(editing, onEditing, onEditingPreview)
                ComponentGroup.INSIGHTS -> InsightsExamples(insights, onInsights, onInsightsPreview)
                ComponentGroup.SCHEDULING -> SchedulingExamples(scheduling, onScheduling, onSchedulingPreview)
                ComponentGroup.WORKSPACE -> WorkspaceExamples(workspace, onWorkspace, onWorkspacePreview)
                ComponentGroup.TABLES -> TableExamples(tables, onTables, onTablePreview)
                ComponentGroup.ACCOUNT -> AccountExamples(account, onAccount, onAccountPreview)
                ComponentGroup.DISCOVERY -> DiscoveryExamples(discovery, onDiscovery, onDiscoveryPreview)
                ComponentGroup.COMMERCE -> CommerceExamples(commerce, onCommerce, onCommercePreview)
                ComponentGroup.NOTIFICATIONS -> NotificationExamples(inbox, onInbox, onInboxPreview)
                ComponentGroup.PLANS -> PlanExamples(plans, onPlans, onPlansPreview)
                ComponentGroup.FILES -> FileExamples(files, onFiles, onFilesPreview)
                ComponentGroup.SHARING -> SharingExamples(sharing, onSharing, onSharingPreview)
                ComponentGroup.PLAYBACK -> PlaybackExamples(playback, onPlayback, onPlaybackPreview)
                ComponentGroup.VERIFICATION -> VerificationExamples(verification, onVerification, onVerificationPreview)
            }
        }
    }
    SheetPanel("Project details", showDetails, { showDetails = false }, "Close details") {
        Card { ListRow("Atlas workspace", "Updated just now") }
    }
    ConfirmationDialog("Remove this example item?", "This only changes the gallery example.",
        confirmDelete, { confirmDelete = false }, "Remove item", "Cancel", { removed = true }, destructive = true)
    SheetPanel("Workspace sheet", workspaceSheet, { workspaceSheet = false }, "Close workspace sheet") {
        Card { ListRow("Atlas workspace", "Custom content in a native presentation.") }
        Text("Closing this sheet does not change your selection.")
    }
    ConfirmationDialog("Reset workspace copies?", "Clear the example copy count. Your real projects are not affected.",
        resetCopies, { resetCopies = false }, "Confirm reset", "Keep copies",
        { copies = 0; lastAction = "Copies reset" }, destructive = true)
}

@Composable private fun Heading(text: String) {
    Text(text, style = FoundryTheme.tokens.typography.heading, modifier = Modifier.semantics { heading() })
}
