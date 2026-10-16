import FoundryUI
import SwiftUI

struct ProductContent: View {
    @Binding var state: ProductState
    @Environment(\.foundry) private var t

    var body: some View {
        SplitPane(detailPresented: state.detailPresented, primaryWidth: 280,
                  minimumDetailWidth: 420 - t.space.inline, primary: { _ in
            projectList
        }, detail: { mode in
            projectDetail(compact: mode == .single)
        })
    }

    private var projectList: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                SectionHeader("Projects", subtitle: "Choose a project to get started.")
                ForEach(ProductProject.allCases) { project in
                    Card(.floating) {
                        ActionButton(project.title, variant: state.selectedProject == project ? .secondary : .quiet) {
                            state.select(project.id)
                        }.accessibilityIdentifier(project.id)
                        Text("Replace this placeholder with your project.").font(t.typography.caption)
                    }
                }
            }.padding(t.space.page)
        }
    }

    @ViewBuilder private func projectDetail(compact: Bool) -> some View {
        if let project = state.selectedProject {
            DetailShell(header: {
                VStack(alignment: .leading, spacing: t.space.inline) {
                    if compact {
                        ActionButton("Back to projects", variant: .quiet) { state.back() }
                            .accessibilityIdentifier("workspace-back")
                    }
                    SectionHeader(project.title, subtitle: "Project overview")
                }.padding(t.space.page)
            }, content: {
                ScrollView {
                    Card(.floating) {
                        Text("Your project starts here.").font(t.typography.heading)
                        Text("Add your tools, documents or activity to this screen.").font(t.typography.body)
                    }.padding(t.space.page)
                }
            }, actions: { EmptyView() })
        } else {
            ScrollView {
                Card(.floating) {
                    SectionHeader("Select a project", subtitle: "Its overview will appear here.")
                }.padding(t.space.page)
            }
        }
    }
}
