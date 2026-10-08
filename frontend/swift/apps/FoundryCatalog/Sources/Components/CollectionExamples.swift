import FoundryUI
import SwiftUI

struct CollectionValues {
    var search = ""
    var favorites = false
    var sort = "Name"
    var selected: Set<String> = []
}
private struct CollectionExampleItem: Identifiable {
    let id: String; let title: String; let favorite: Bool; let recency: Int
}

struct CollectionExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: CollectionValues
    private let items = [CollectionExampleItem(id: "atlas", title: "Atlas workspace", favorite: true, recency: 2),
                         CollectionExampleItem(id: "orbit", title: "Orbit study", favorite: false, recency: 3),
                         CollectionExampleItem(id: "field", title: "Field notes", favorite: true, recency: 1)]
    private var visible: [CollectionExampleItem] {
        items.filter { (!values.favorites || $0.favorite) && (values.search.isEmpty || $0.title.localizedCaseInsensitiveContains(values.search)) }
            .sorted { values.sort == "Name" ? $0.title < $1.title : $0.recency > $1.recency }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                CollectionToolbar("Your collection", summary: "\(visible.count) visible · \(values.selected.count) selected", filters: {
                    SearchField("Filter collection", text: $values.search, clearLabel: "Clear collection search")
                    ToggleField("Favorites only", isOn: $values.favorites)
                    SelectField("Sort projects", selection: $values.sort, options: ["Name", "Newest"], label: { $0 })
                }, actions: {
                    ViewThatFits(in: .horizontal) {
                        HStack { selectionActions }
                        VStack(alignment: .leading) { selectionActions }
                    }
                })
            }
            Card {
                if visible.isEmpty {
                    EmptyState("Nothing matches these filters", message: "Change the search or include all projects.", artwork: { EmptyView() }, actions: {
                        ActionButton("Reset collection filters", variant: .secondary) { values.search = ""; values.favorites = false }
                    })
                } else {
                    ForEach(visible) { item in
                        Checkbox(item.title, state: values.selected.contains(item.id) ? .on : .off,
                                 stateDescription: values.selected.contains(item.id) ? "Selected" : "Not selected") {
                            if values.selected.contains(item.id) { values.selected.remove(item.id) }
                            else { values.selected.insert(item.id) }
                        }
                    }
                }
            }
            Text("Filtering keeps selected IDs. Select visible adds the current matches; Clear selection removes every selection.")
                .font(t.typography.caption)
        }
    }
    @ViewBuilder private var selectionActions: some View {
        ActionButton("Select visible", variant: .secondary, enabled: !visible.isEmpty) { values.selected.formUnion(visible.map(\.id)) }
        ActionButton("Clear selection", variant: .quiet, enabled: !values.selected.isEmpty) { values.selected.removeAll() }
    }
}
