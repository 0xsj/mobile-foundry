import FoundryUI
import Observation
import SwiftUI

struct ActivityRecord: Identifiable {
    let id: Int
    var title: String { ["A new direction", "Material study", "Ready for review"][(id - 1) % 3] + " · \(id)" }
    var timestamp: String { "Today · Update \(id)" }
    var note: String { "We explored softer surfaces and a quieter palette for this collection. The latest study brings the image, motion, and supporting details together. There is room to adjust the rhythm as the collection grows, while keeping the important actions easy to find. Share your observations with the team before the next review." }
}

/// Local fixture policy, deliberately outside FoundryUI. A destination owns work; leaving invalidates pending results.
@Observable @MainActor final class ActivityPreviewValues {
    var records = (1...3).map(ActivityRecord.init)
    var expanded: Set<Int> = []
    var summaryExpanded = false
    var failNextPage = false
    private(set) var phase: LoadMorePhase = .idle
    private(set) var refreshing = false
    private(set) var refreshes = 0
    private(set) var pageRequest = 0
    private var revision = 0
    @ObservationIgnored private let pause: @Sendable () async throws -> Void
    init(pause: @escaping @Sendable () async throws -> Void = { try await Task.sleep(for: .milliseconds(450)) }) { self.pause = pause }
    func requestPage() {
        guard !refreshing, phase == .idle || phase == .failed else { return }
        phase = .loading; pageRequest += 1
    }
    func completePage() async {
        guard phase == .loading else { return }
        let revision = revision
        do { try await pause(); try Task.checkCancellation() }
        catch { if self.revision == revision { phase = .idle }; return }
        guard self.revision == revision else { return }
        if failNextPage { failNextPage = false; phase = .failed; return }
        let start = records.count + 1
        records += (start...(start + 2)).map(ActivityRecord.init)
        phase = records.count >= 9 ? .exhausted : .idle
    }
    func refresh() async {
        guard !refreshing, phase != .loading else { return }
        refreshing = true
        let revision = revision
        defer { if self.revision == revision { refreshing = false } }
        do { try await pause(); try Task.checkCancellation() } catch { return }
        guard self.revision == revision else { return }
        records = (1...3).map(ActivityRecord.init); phase = .idle; refreshes += 1
        expanded = expanded.intersection(Set(records.map(\.id)))
    }
    func cancelPending() {
        revision += 1; refreshing = false
        if phase == .loading { phase = .idle }
    }
    var message: String {
        switch phase {
        case .idle: "More updates are available."
        case .loading: "Loading more updates…"
        case .failed: "Could not load more. Your updates are still here."
        case .exhausted: "You’re all caught up."
        }
    }
}

private struct ActivityMember: Identifiable { let id: String; let initials: String }
struct ActivityExamples: View {
    @Environment(\.foundry) private var t
    @Bindable var values: ActivityPreviewValues
    private let members = [ActivityMember(id: "Jordan", initials: "JL"), ActivityMember(id: "Sam", initials: "SK"),
                           ActivityMember(id: "Alex", initials: "AC"), ActivityMember(id: "Mika", initials: "MT"), ActivityMember(id: "Rae", initials: "RS")]
    var body: some View {
        Card {
            SectionHeader("Collection activity", subtitle: "Small updates, shared progress.")
            AvatarGroup(members, label: "5 collaborators: Jordan, Sam, Alex, Mika, Rae", overflowText: "+2", maximumVisible: 3) {
                Avatar($0.id, fallback: $0.initials, size: 40)
            }
            TimelineItem("A new direction", timestamp: "Today · Jordan", showsConnector: false) {
                ExpandableText(ActivityRecord(id: 1).note, expanded: $values.summaryExpanded, moreLabel: "Read update", lessLabel: "Collapse update")
            }
            NavLink("Open activity preview", subtitle: "Pull to refresh, load pages, and retry.") {
                ActivityPreviewView(values: values, appearance: t.appearance, style: t.materials.style)
            }
        }
    }
}

struct ActivityPreviewView: View {
    let values: ActivityPreviewValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ActivityPreviewContent(values: values) }
            .navigationTitle("Activity preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
private struct ActivityPreviewContent: View {
    @Environment(\.foundry) private var t
    @Bindable var values: ActivityPreviewValues
    var body: some View {
        RefreshContainer(onRefresh: { await values.refresh() }) {
            List {
                Section {
                    SectionHeader("Atlas activity", subtitle: "Pull down to refresh. Local preview only.") {
                        ActionButton("Refresh updates", variant: .quiet, enabled: !values.refreshing && values.phase != .loading) {
                            refreshRequest += 1
                        }
                    }
                    Text("\(values.records.count) updates · Refreshes: \(values.refreshes)").font(t.typography.caption)
                    Toggle("Fail next page", isOn: $values.failNextPage)
                }.listRowBackground(t.colors.surfacePanel.color)
                Section {
                    ForEach(values.records) { record in
                        TimelineItem(record.title, timestamp: record.timestamp, showsConnector: record.id != values.records.last?.id) {
                            ExpandableText(record.note, expanded: Binding(get: { values.expanded.contains(record.id) }, set: {
                                if $0 { values.expanded.insert(record.id) } else { values.expanded.remove(record.id) }
                            }), moreLabel: "Read update \(record.id)", lessLabel: "Collapse update \(record.id)")
                        }
                    }.listRowBackground(t.colors.surfacePanel.color)
                }
                Section {
                    LoadMoreFooter(values.phase, message: values.message,
                                   actionLabel: values.phase == .failed ? "Retry page" : "Load more updates",
                                   enabled: !values.refreshing, onLoad: values.requestPage)
                }.listRowBackground(t.colors.surfacePanel.color)
            }.scrollContentBackground(.hidden)
        }
        .task(id: values.pageRequest) { await values.completePage() }
        .task(id: refreshRequest) { if refreshRequest > 0 { await values.refresh() } }
        .onDisappear { values.cancelPending() }
    }
    @State private var refreshRequest = 0
}
