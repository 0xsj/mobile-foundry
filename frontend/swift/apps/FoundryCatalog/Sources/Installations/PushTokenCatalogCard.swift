import FoundryServices
import FoundryUI
import OSLog
import SwiftUI

struct PushTokenCatalogCard: View {
    var installations: InstallationController? = nil
    @State private var model = PushTokenController(tokens: PushTokenComposition.tokens, port: PushTokenComposition.provider, onUnexpectedError: { _ in
        Logger(subsystem: "dev.mobilefoundry.catalog", category: "push-tokens").error("Unexpected push token failure.")
    })
    @Environment(\.foundry) private var t

    var body: some View {
        Card {
            SectionHeader("Provider token", subtitle: "Explicit APNs collection. Permission and backend registration are separate steps.")
            Text(model.target == nil ? "No collected token" : "Token ready · local revision \(model.tokens.revision)")
                .accessibilityIdentifier("push.status")
            if let target = model.target {
                Text("\(target.route.provider.rawValue) · \(target.route.environment.rawValue) · \(target.route.application)")
                    .font(t.typography.caption)
            }
            ActionButton("Collect device token", isBusy: model.busy, enabled: !model.busy) { model.collect() }
                .accessibilityIdentifier("push.collect")
            if let installations {
                ActionButton("Register collected token", variant: .secondary, enabled: installations.canRegister && model.target != nil) {
                    if let target = model.target { installations.register(target) }
                }.accessibilityIdentifier("push.register")
            }
            if let failure = model.failure {
                Text(failure.meta.message).font(t.typography.caption).accessibilityIdentifier("push.failure")
            }
            Text("Token values stay hidden. Rotation updates this candidate; it never writes to the backend automatically. APNs signing setup is required. Collection does not prove push delivery.").font(t.typography.caption)
        }.onDisappear { model.close() }
    }
}
