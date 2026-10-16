import FoundryUI
import SwiftUI

struct ProductContent: View {
    @Binding var state: ProductState
    @Environment(\.foundry) private var t
    @FocusState private var searching: Bool

    var body: some View {
        if let collection = state.selectedCollection {
            DetailShell(header: {
                VStack(alignment: .leading, spacing: t.space.inline) {
                    ActionButton("Back to collections", variant: .quiet) { state.back() }
                        .accessibilityIdentifier("browse-back")
                    SectionHeader(collection.title, subtitle: "Collection overview")
                }.padding(t.space.page)
            }, content: {
                ScrollView {
                    Card(.floating) {
                        Text("Your collection starts here.").font(t.typography.heading)
                        Text("Add your products, media or directory entries to this screen.").font(t.typography.body)
                    }.padding(t.space.page)
                }
            }, actions: { EmptyView() })
        } else {
            ScrollView {
                VerticalStack(spacing: t.space.section) {
                    SectionHeader("Collections", subtitle: "Find a place to explore.")
                    SearchField("Find collections", text: $state.filter, clearLabel: "Clear search")
                        .focused($searching)
                    if state.visibleCollections.isEmpty {
                        Text("No collections match your search.").font(t.typography.body)
                    }
                    ForEach(state.visibleCollections) { collection in
                        Card(.floating) {
                            ActionButton("Open \(collection.title)", variant: .quiet) {
                                searching = false
                                state.select(collection.id)
                            }.accessibilityIdentifier(collection.id)
                            Text("Replace this placeholder with your collection.").font(t.typography.caption)
                        }
                    }
                }.padding(t.space.page)
            }
        }
    }
}
