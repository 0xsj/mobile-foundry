import SwiftUI

/// Controlled one-based pages. The feature supplies page copy, total pages and any loading/empty policy.
public struct PaginationBar: View {
    @Environment(\.foundry) private var t
    private let page: Int
    private let totalPages: Int
    private let pageLabel: String
    private let previousLabel: String
    private let nextLabel: String
    private let enabled: Bool
    private let onPageChange: (Int) -> Void
    public init(page: Int, totalPages: Int, pageLabel: String, previousLabel: String, nextLabel: String,
                enabled: Bool = true, onPageChange: @escaping (Int) -> Void) {
        precondition(totalPages >= 1 && page >= 1 && page <= totalPages)
        self.page = page; self.totalPages = totalPages; self.pageLabel = pageLabel
        self.previousLabel = previousLabel; self.nextLabel = nextLabel; self.enabled = enabled; self.onPageChange = onPageChange
    }
    public var body: some View {
        WrapLayout {
            ActionButton(previousLabel, variant: .secondary, enabled: enabled && page > 1) {
                guard enabled && page > 1 else { return }; onPageChange(page - 1)
            }
            Text(pageLabel).font(t.typography.caption).fixedSize(horizontal: false, vertical: true)
                .frame(minHeight: t.shape.minimumInteractive)
            ActionButton(nextLabel, variant: .secondary, enabled: enabled && page < totalPages) {
                guard enabled && page < totalPages else { return }; onPageChange(page + 1)
            }
        }
    }
}
