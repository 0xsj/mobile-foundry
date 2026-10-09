import SwiftUI

/// Supplied branch state and an independent native disclosure action. No descendant traversal or expansion ownership.
public struct TreeDisclosure {
    public let expanded: Bool
    public let actionLabel: String
    public let stateLabel: String
    public let enabled: Bool
    public let onToggle: () -> Void
    public init(expanded: Bool, actionLabel: String, stateLabel: String, enabled: Bool = true, onToggle: @escaping () -> Void) {
        self.expanded = expanded; self.actionLabel = actionLabel; self.stateLabel = stateLabel; self.enabled = enabled; self.onToggle = onToggle
    }
}

/// One native selection/open target, optional independent disclosure, passive artwork and sibling actions.
/// Depth is nonnegative; finite nonnegative indentation is capped to preserve readable bounds. Enabled gates opening only.
public struct TreeRow<Leading: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var typeSize
    private let title: String
    private let subtitle: String?
    private let accessibilityLabel: String
    private let depth: Int
    private let indentation: CGFloat
    private let maximumIndentation: CGFloat
    private let selected: Bool
    private let enabled: Bool
    private let disclosure: TreeDisclosure?
    private let onOpen: () -> Void
    private let leading: Leading
    private let actions: Actions
    public init(_ title: String, subtitle: String? = nil, accessibilityLabel: String, depth: Int = 0,
                indentation: CGFloat = 16, maximumIndentation: CGFloat = 48, selected: Bool = false, enabled: Bool = true,
                disclosure: TreeDisclosure? = nil, onOpen: @escaping () -> Void,
                @ViewBuilder leading: () -> Leading, @ViewBuilder actions: () -> Actions) {
        precondition(depth >= 0 && indentation.isFinite && indentation >= 0 && maximumIndentation.isFinite && maximumIndentation >= 0)
        self.title = title; self.subtitle = subtitle; self.accessibilityLabel = accessibilityLabel; self.depth = depth
        self.indentation = indentation; self.maximumIndentation = maximumIndentation; self.selected = selected
        self.enabled = enabled; self.disclosure = disclosure; self.onOpen = onOpen; self.leading = leading(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            HStack(alignment: .top, spacing: t.space.inline) {
                if let disclosure {
                    IconAction(disclosure.actionLabel, enabled: disclosure.enabled, action: { if disclosure.enabled { disclosure.onToggle() } }) {
                        Image(systemName: disclosure.expanded ? "chevron.down" : "chevron.forward")
                    }.accessibilityValue(disclosure.stateLabel)
                } else { Color.clear.frame(width: t.shape.minimumInteractive, height: 1).accessibilityHidden(true) }
                Button { if enabled { onOpen() } } label: {
                    Group {
                        if typeSize.isAccessibilitySize { VStack(alignment: .leading, spacing: t.space.inline) { leading.accessibilityHidden(true); copy } }
                        else { HStack(alignment: .top, spacing: t.space.inline) { leading.accessibilityHidden(true); copy } }
                    }.frame(maxWidth: .infinity, minHeight: t.shape.minimumInteractive, alignment: .leading).contentShape(Rectangle())
                }.buttonStyle(.plain).foregroundStyle(t.colors.ink.color).disabled(!enabled)
                    .padding(t.space.steps[1]).background(selected ? t.colors.accentTint.color : .clear, in: RoundedRectangle(cornerRadius: t.shape.radii[1]))
                    .accessibilityElement(children: .combine).accessibilityLabel(accessibilityLabel).accessibilityAddTraits(selected ? .isSelected : [])
            }
            actions
        }.frame(maxWidth: .infinity, alignment: .leading).padding(.leading, min(CGFloat(depth) * indentation, maximumIndentation))
    }
    private var copy: some View {
        VStack(alignment: .leading, spacing: t.space.steps[1]) {
            Text(title).font(t.typography.body.weight(selected ? .semibold : .regular))
            if let subtitle { Text(subtitle).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension TreeRow where Leading == EmptyView, Actions == EmptyView {
    public init(_ title: String, subtitle: String? = nil, accessibilityLabel: String, depth: Int = 0,
                indentation: CGFloat = 16, maximumIndentation: CGFloat = 48, selected: Bool = false, enabled: Bool = true,
                disclosure: TreeDisclosure? = nil, onOpen: @escaping () -> Void) {
        self.init(title, subtitle: subtitle, accessibilityLabel: accessibilityLabel, depth: depth, indentation: indentation,
                  maximumIndentation: maximumIndentation, selected: selected, enabled: enabled, disclosure: disclosure, onOpen: onOpen,
                  leading: { EmptyView() }, actions: { EmptyView() })
    }
}
