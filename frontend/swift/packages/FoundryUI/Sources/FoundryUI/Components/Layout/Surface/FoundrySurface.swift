import SwiftUI

public enum FoundrySurfaceRole: Sendable { case content, floating }

/// Content panels stay opaque. Floating controls follow the scoped material theme.
/// Padding belongs to the caller; the surface never owns interaction or state.
public struct FoundrySurface<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let role: FoundrySurfaceRole
    private let content: Content

    public init(_ role: FoundrySurfaceRole = .content, @ViewBuilder content: () -> Content) {
        self.role = role
        self.content = content()
    }

    public var body: some View {
        content.background { surfaceBackground }
    }

    @ViewBuilder private var surfaceBackground: some View {
        let shape = RoundedRectangle(cornerRadius: tokens.shape.panel, style: .continuous)
        if role == .floating && tokens.materials.floating == .glass {
            if #available(iOS 26.0, macOS 26.0, *) {
                shape.fill(.clear).glassEffect(.regular, in: shape)
            } else {
                shape.fill(.regularMaterial)
                    .overlay(shape.stroke(tokens.colors.lineStrong.color))
            }
        } else {
            shape.fill(role == .floating ? tokens.colors.surfaceRaised.color : tokens.colors.surfacePanel.color)
                .overlay(shape.stroke(tokens.colors.line.color))
        }
    }
}
