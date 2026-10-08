import SwiftUI

/// Passive supplied detail copy. Long values and accessibility text sizes stack instead of truncating.
public struct KeyValueRow: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var typeSize
    private let title: String
    private let value: String
    private let detail: String?
    public init(_ title: String, value: String, detail: String? = nil) {
        self.title = title; self.value = value; self.detail = detail
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            if typeSize.isAccessibilitySize { stacked }
            else {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: t.space.stack) {
                        label.fixedSize(); valueText.fixedSize()
                    }
                    stacked
                }
            }
            if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }.frame(maxWidth: .infinity, alignment: .leading).accessibilityElement(children: .combine)
    }
    private var label: some View { Text(title).foregroundStyle(t.colors.inkSecondary.color) }
    private var valueText: some View { Text(value).font(t.typography.label) }
    private var stacked: some View {
        VStack(alignment: .leading, spacing: t.space.inline) { label; valueText }
    }
}
