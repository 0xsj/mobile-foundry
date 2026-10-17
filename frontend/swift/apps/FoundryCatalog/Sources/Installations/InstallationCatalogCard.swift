import FoundryServices
import FoundryUI
import SwiftUI

struct InstallationCatalogCard: View {
    let model: InstallationController
    @State private var token = ""
    @State private var environment = PushEnvironment.sandbox
    @Environment(\.foundry) private var t
    private var route: PushRoute { PushRoute(provider: .apns, application: Bundle.main.bundleIdentifier ?? "dev.mobilefoundry.catalog", environment: environment)! }
    var body: some View {
        PushTokenCatalogCard(installations: model)
        Card {
            SectionHeader("Manual device registration", subtitle: "Alternate registry probe. Use the collected token above for a real provider address; neither control sends a push.")
            if let id = model.installationID { Text(id).font(t.typography.code) }
            if let record = model.record {
                Text(record.registered ? "Registered target · revision \(record.version)" : "Disabled target · revision \(record.version)")
                Text(record.currentBinding ? "Bound to this session" : "Bound to another session; register to transfer ownership")
                Text("\(record.route.provider.rawValue) · \(record.route.environment.rawValue) · \(record.route.application)").font(t.typography.caption)
                Text("Session binding expires \(record.expiresAt.formatted())").font(t.typography.caption)
            } else if !model.needsRefresh { Text("No saved target on this backend") }
            Picker("APNs environment", selection: $environment) {
                Text("Sandbox").tag(PushEnvironment.sandbox)
                Text("Production").tag(PushEnvironment.production)
            }.disabled(model.busy)
            SecureField("Opaque token or registry probe address", text: $token)
                .textInputAutocapitalization(.never).autocorrectionDisabled().disabled(model.busy)
            ActionButton(model.record == nil ? "Register target" : "Replace target / bind this session", enabled: model.canRegister && DeliveryTarget(route: route, token: token) != nil) {
                if let target = DeliveryTarget(route: route, token: token) { model.register(target); token = "" }
            }
            ActionButton("Refresh registration", variant: .secondary, isBusy: model.busy, enabled: !model.busy) { model.refresh() }
            ActionButton("Unregister target", variant: .destructive, enabled: model.canUnregister) { model.unregister() }
            if model.needsRefresh { Text("Refresh before another change. The previous request may have committed.").font(t.typography.caption) }
            if let failure = model.failure { Text(failure.meta.message).font(t.typography.caption) }
            Text("The device key stays in secure storage when you sign out. Server session revocation retires its target. No automatic registration or write retry.").font(t.typography.caption)
        }.onDisappear { token = "" }
    }
}
