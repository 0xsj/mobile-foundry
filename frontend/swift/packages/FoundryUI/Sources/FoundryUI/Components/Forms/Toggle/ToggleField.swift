import SwiftUI

public struct ToggleField: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let help: String?
    @Binding private var isOn: Bool
    private let enabled: Bool
    public init(_ title: String, isOn: Binding<Bool>, help: String? = nil, enabled: Bool = true) {
        self.title = title; self._isOn = isOn; self.help = help; self.enabled = enabled
    }
    public var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: tokens.space.steps[1]) {
                Text(title).font(tokens.typography.label)
                if let help { Text(help).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color) }
            }
        }.toggleStyle(.switch).frame(minHeight: tokens.shape.minimumInteractive).disabled(!enabled)
    }
}
