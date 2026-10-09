import SwiftUI

public enum TableSortOrder: Sendable { case ascending, descending }

/// One native sort action. The caller chooses the next order and supplies localized state narration.
public struct TableSortHeader: View {
    private let title: String
    private let order: TableSortOrder?
    private let accessibilityValue: String
    private let enabled: Bool
    private let onSort: () -> Void
    public init(_ title: String, order: TableSortOrder?, accessibilityValue: String, enabled: Bool = true, onSort: @escaping () -> Void) {
        self.title = title; self.order = order; self.accessibilityValue = accessibilityValue; self.enabled = enabled; self.onSort = onSort
    }
    public var body: some View {
        ActionButton(variant: .quiet, enabled: enabled, action: onSort) {
            Text(title).fixedSize(horizontal: false, vertical: true)
            Image(systemName: order == .ascending ? "arrow.up" : order == .descending ? "arrow.down" : "arrow.up.arrow.down")
                .accessibilityHidden(true)
        }.accessibilityValue(accessibilityValue)
    }
}
