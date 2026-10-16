import FoundryHTTP
import FoundryKernel
import FoundrySession
import FoundryUI
import SwiftUI

struct GoSessionCatalogView: View {
  @AppStorage("catalog.go_api_base") private var apiBase = "http://127.0.0.1:8080/v1"
  @State private var provider = SessionProvider.device
  @State private var allowLocalHTTP = true
  @State private var model: GoSessionCatalogModel?
  @State private var configurationFailure: Failure?
  @State private var login = ""
  @State private var password = ""
  @FocusState private var passwordFocused: Bool
  @FocusState private var apiFocused: Bool
  @FocusState private var loginFocused: Bool
  @Environment(\.foundry) private var t
  @Environment(\.scenePhase) private var scenePhase
  private func connect() {
    let transport = URLSessionHTTPTransport()
    switch GoSessionAuthority.create(
      baseURL: apiBase, transport: transport, allowLocalHTTP: allowLocalHTTP)
    {
    case .failure(let failure):
      transport.invalidate()
      configurationFailure = failure.publicInfo()
    case .success(let authority):
      model?.close()
      password = ""
      passwordFocused = false
      configurationFailure = nil
      let next = GoSessionCatalogModel(
        authority: authority, transport: transport,
        vault: SessionComposition.goVault(provider, authorityID: authority.id), provider: provider,
        allowLocalHTTP: allowLocalHTTP)
      model = next
      next.controller.restore()
    }
  }
  var body: some View {
    ScrollView {
      VerticalStack(spacing: t.space.section) {
        configurationCard
        if let model { connectedContent(model) }
      }.padding(t.space.section)
    }
    .navigationTitle("Go identity and accounts").navigationBarTitleDisplayMode(.inline)
    .onChange(of: model?.controller.lease) { _, _ in
      model?.refreshScope()
    }
    .onChange(of: scenePhase) { _, phase in
      if phase == .active { model?.controller.expireIfNeeded() }
    }
    .onDisappear {
      password = ""
      passwordFocused = false
      model?.close()
      model = nil
    }
  }
  private var configurationCard: some View {
    Card(.floating) {
      SectionHeader(
        "Go backend",
        subtitle:
          "Use an account registered with the Go Identity API. This example sends real HTTP requests."
      )
      LabeledTextField(
        "API base", text: $apiBase, enabled: model?.controller.isBusy != true, focus: $apiFocused
      )
      .textInputAutocapitalization(.never).autocorrectionDisabled().accessibilityIdentifier(
        "go.api-base")
      SelectField(
        "Credentials", selection: $provider, options: SessionProvider.allCases,
        enabled: model?.controller.isBusy != true, label: { $0.rawValue })
      Toggle("Allow loopback HTTP", isOn: $allowLocalHTTP).disabled(
        model?.controller.isBusy == true)
      ActionButton(
        model == nil ? "Connect" : "Reconnect", enabled: model?.controller.isBusy != true,
        action: connect
      ).accessibilityIdentifier("go.connect")
      if let failure = configurationFailure {
        Text(failure.meta.message).font(t.typography.caption)
      }
    }
  }
  @ViewBuilder private func connectedContent(_ model: GoSessionCatalogModel) -> some View {
    let controller = model.controller
    sessionCard(model, controller)
    signInCard(controller)
    controlsCard(controller)
    if let lease = controller.lease {
      directoryCard(model, controller)
      RemoteNotesCatalogCard(model:model.notes).id(lease.scope)
    }
  }
  private func sessionCard(_ model: GoSessionCatalogModel, _ controller: SessionController)
    -> some View
  {
    Card {
      SectionHeader("Session", subtitle: "State: \(controller.snapshot.phase.rawValue)")
      Text(controller.snapshot.phase.rawValue).accessibilityIdentifier("go.phase")
      Text(controller.snapshot.principal?.displayName ?? "No active identity")
        .accessibilityIdentifier("go.principal")
      if let account = controller.snapshot.account {
        Text("Selected: \(account.name)").accessibilityIdentifier("go.selected-account")
        Text(account.id).font(t.typography.caption).accessibilityIdentifier("go.account-id")
      }
      Text("Credentials: \(model.provider.rawValue)").font(t.typography.caption)
      if let failure = controller.snapshot.failure {
        Text(failure.meta.message).font(t.typography.caption).accessibilityIdentifier("go.failure")
      }
    }
  }
  private func signInCard(_ controller: SessionController) -> some View {
    Card {
      SectionHeader(
        "Sign in",
        subtitle:
          "The first sign-in bootstraps a personal account. Passwords stay in this draft only.")
      LabeledTextField("Login", text: $login, enabled: !controller.isBusy, focus: $loginFocused)
        .textInputAutocapitalization(.never).autocorrectionDisabled().accessibilityIdentifier(
          "go.login")
      PasswordField(
        "Password", text: $password, enabled: !controller.isBusy, focus: $passwordFocused
      ).accessibilityIdentifier("go.password")
      ActionButton(
        "Sign in", isBusy: controller.snapshot.phase == .signingIn, enabled: !controller.isBusy
      ) {
        if controller.signIn(login: login, password: password) != nil {
          password = ""
          passwordFocused = false
        }
      }.accessibilityIdentifier("go.sign-in")
    }
  }
  private func controlsCard(_ controller: SessionController) -> some View {
    Card {
      SectionHeader(
        "Session controls", subtitle: "Renewal and reopening preserve your selected account.")
      ActionButton("Restore saved session", variant: .secondary, enabled: !controller.isBusy) {
        controller.restore()
      }.accessibilityIdentifier("go.restore")
      ActionButton("Renew access", variant: .secondary, enabled: controller.canRenew) {
        controller.renew()
      }.accessibilityIdentifier("go.renew")
      ActionButton(
        "Sign out", variant: .destructive, enabled: controller.snapshot.phase != .signingOut
      ) {
        password = ""
        passwordFocused = false
        controller.signOut()
      }.accessibilityIdentifier("go.sign-out")
    }
  }
  private func directoryCard(_ model: GoSessionCatalogModel, _ controller: SessionController)
    -> some View
  {
    Card {
      SectionHeader(
        "Owned accounts",
        subtitle: "Selection stays on this device. Other devices can use another account.")
      ActionButton(
        "Refresh accounts", variant: .secondary, isBusy: model.isLoading, enabled: !model.isLoading
      ) { model.load() }.accessibilityIdentifier("go.accounts-refresh")
      ForEach(model.accounts, id: \.id) { account in
        ActionButton(
          "Use \(account.name)", variant: .quiet,
          enabled: controller.canSelectAccount && account.id != controller.snapshot.account?.id
        ) { controller.selectAccount(account.id) }
        .accessibilityIdentifier("go.select.\(account.id)")
      }
      if let cursor = model.nextCursor {
        ActionButton("Next accounts", variant: .quiet, enabled: !model.isLoading) {
          model.load(cursor: cursor)
        }
      }
      if let failure = model.directoryFailure {
        Text(failure.meta.message).font(t.typography.caption)
      }
      Text(
        "Remote notes below use the selected account. Offline synchronization is a separate capability."
      ).font(t.typography.caption)
    }
  }
}
