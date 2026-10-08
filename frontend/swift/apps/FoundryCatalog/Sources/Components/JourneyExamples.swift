import FoundryUI
import SwiftUI

struct JourneyValues {
    var password = ""
    var bio = ""
    var enabled = true
    var updates = true
    var step = 0
    var finished = false
    var accountPreviews = 0
    var finishedPreviews = 0
    var hasNote: Bool { !bio.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var checklist: [ValidationItem] {
        [ValidationItem(id: "password", title: "Password entered", satisfied: !password.isEmpty,
                        stateDescription: password.isEmpty ? "Needed" : "Satisfied"),
         ValidationItem(id: "note", title: "Profile note added", satisfied: hasNote,
                        stateDescription: hasNote ? "Satisfied" : "Needed")]
    }
    var steps: [StepItem] {
        ["Profile", "Preferences", "Review"].enumerated().map { index, title in
            let status: StepStatus = finished || index < step ? .completed : index == step ? .current : .upcoming
            return StepItem(id: title, title: title, status: status,
                            stateDescription: status == .completed ? "Completed" : status == .current ? "Current" : "Upcoming")
        }
    }
}

struct JourneyExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: JourneyValues
    @FocusState private var noteFocused: Bool
    var body: some View {
        Card {
            Text("Richer input").font(t.typography.heading).accessibilityAddTraits(.isHeader)
            JourneyPassword(values: $values)
            MultilineField("About you", text: $values.bio, help: "A short introduction for your preview.",
                           enabled: values.enabled, focus: $noteFocused)
            ValidationChecklist(values.checklist)
            ToggleField("Enable preview inputs", isOn: $values.enabled)
            ActionButton("Done editing", variant: .quiet) { noteFocused = false }
        }
        Card {
            NavLink("Open account preview", subtitle: "A readable account-form layout") {
                JourneyDestination(values: $values, account: true, appearance: t.appearance, style: t.materials.style)
            }
            NavLink("Open onboarding preview", subtitle: "Three steps with a shared draft") {
                JourneyDestination(values: $values, account: false, appearance: t.appearance, style: t.materials.style)
            }
            Text("Account previews: \(values.accountPreviews)").font(t.typography.caption)
            Text("Finished previews: \(values.finishedPreviews)").font(t.typography.caption)
        }
    }
}

private struct JourneyPassword: View {
    @Binding var values: JourneyValues
    @FocusState private var focused: Bool
    var body: some View {
        PasswordField("Preview password", text: $values.password, help: "Use any example text.",
                      enabled: values.enabled, focus: $focused, onSubmit: { focused = false })
        ActionButton("Done editing password", variant: .quiet) { focused = false }
    }
}

struct JourneyDestination: View {
    @Binding var values: JourneyValues
    let account: Bool
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            if account { AccountPreviewContent(values: $values) }
            else { OnboardingPreviewContent(values: $values) }
        }
        .navigationTitle(account ? "Account preview" : "Onboarding preview").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}

private struct AccountPreviewContent: View {
    @Binding var values: JourneyValues
    var body: some View {
        AuthShell {
            PageHeader("Welcome back", subtitle: "Explore an account form layout.")
        } content: {
            Card {
                KeyValueRow("Account", value: "atlas@example.com")
                JourneyPassword(values: $values)
                ValidationChecklist(values.checklist)
            }
        } footer: {
            ActionButton(enabled: values.enabled && !values.password.isEmpty, action: { values.accountPreviews += 1 }) {
                Text("Continue account preview").frame(maxWidth: .infinity)
            }
            Text("Account previews: \(values.accountPreviews)")
        }
    }
}

private struct OnboardingPreviewContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: JourneyValues
    @FocusState private var focused: Bool
    private var titles: [String] { ["Make it yours", "Choose your updates", "Ready to explore"] }
    var body: some View {
        OnboardingPage(titles[values.step], message: "Build a local profile preview.") {
            MediaFrame(ratio: 3) {
                RoundedRectangle(cornerRadius: t.shape.panel)
                    .fill(LinearGradient(colors: [t.colors.accentTint.color, t.colors.accent.color], startPoint: .topLeading, endPoint: .bottomTrailing))
            }.accessibilityHidden(true)
        } content: {
            StepIndicator(values.finished ? "Preview complete" : "Step \(values.step + 1) of 3", steps: values.steps)
            Card {
                switch values.step {
                case 0:
                    MultilineField("Profile introduction", text: $values.bio, help: "Add a note to continue.", focus: $focused)
                    ActionButton("Done editing introduction", variant: .quiet) { focused = false }
                case 1: ToggleField("Include activity updates", isOn: $values.updates)
                default:
                    KeyValueRow("Introduction", value: values.bio)
                    KeyValueRow("Activity updates", value: values.updates ? "Included" : "Off")
                    if values.finished { Badge("Preview complete", tone: .info) }
                }
            }
        } actions: {
            ActionBar {
                ActionButton(enabled: !values.finished && (values.step != 0 || values.hasNote), action: {
                    focused = false
                    if values.step < 2 { values.step += 1 }
                    else { values.finished = true; values.finishedPreviews += 1 }
                }) { Text(values.step == 2 ? "Finish preview" : "Next step").frame(maxWidth: .infinity) }
                ActionButton("Previous step", variant: .secondary, enabled: values.step > 0) {
                    focused = false; values.step -= 1; values.finished = false
                }
                ActionButton("Restart steps", variant: .quiet) { focused = false; values.step = 0; values.finished = false }
            }
        }.id(values.step)
    }
}
