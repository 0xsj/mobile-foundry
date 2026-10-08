import SwiftUI
import FoundryUI

private enum PreviewAppearance: String, CaseIterable, Identifiable {
    case system = "System", light = "Light", dark = "Dark"
    var id: Self { self }
    var appearance: FoundryAppearance? {
        switch self { case .system: nil; case .light: .light; case .dark: .dark }
    }
}

struct TokenCatalogView: View {
    @Environment(\.foundry) private var tokens
    @State private var appearance = PreviewAppearance.system
    @State private var reduceMotion = false
    @State private var style: FoundryThemeStyle?
    @State private var reduceTransparency = false

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: tokens.space.inline) {
                HStack {
                    Text("Appearance")
                    Spacer()
                    Picker("Appearance", selection: $appearance) {
                        ForEach(PreviewAppearance.allCases) { Text($0.rawValue).tag($0) }
                    }.pickerStyle(.menu).accessibilityIdentifier("token-appearance")
                }
                HStack {
                    Text("Surface theme")
                    Spacer()
                    Picker("Surface theme", selection: $style) {
                        Text("App theme").tag(Optional<FoundryThemeStyle>.none)
                        Text("Solid").tag(Optional(FoundryThemeStyle.solid))
                        Text("Glass").tag(Optional(FoundryThemeStyle.glass))
                    }.pickerStyle(.menu).accessibilityIdentifier("token-surface-theme")
                }
                Toggle("Reduce transparency preview", isOn: $reduceTransparency)
                Toggle("Reduce motion preview", isOn: $reduceMotion)
                Text("Preview settings apply only to the examples below.")
                    .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
            }.padding(tokens.space.page)
            Divider()
            FoundryTheme(appearance: appearance.appearance, reduceMotion: reduceMotion,
                         style: style, reduceTransparency: reduceTransparency) { TokenExamples() }
        }
        .navigationTitle("Tokens").navigationBarTitleDisplayMode(.inline)
    }
}

private struct TokenExamples: View {
    @Environment(\.foundry) private var tokens
    @State private var shifted = false
    @State private var actions = 0

    private var swatches: [(String, TokenColor)] {
        let c = tokens.colors
        return [("surfaceGround", c.surfaceGround), ("surfaceSunk", c.surfaceSunk),
                ("surfacePanel", c.surfacePanel), ("surfaceRaised", c.surfaceRaised),
                ("ink", c.ink), ("inkSecondary", c.inkSecondary), ("inkMuted", c.inkMuted),
                ("line", c.line), ("lineStrong", c.lineStrong), ("accent", c.accent),
                ("accentTint", c.accentTint), ("fill", c.fill), ("fillInk", c.fillInk),
                ("info", c.info), ("warn", c.warn), ("crit", c.crit)]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: tokens.space.section) {
                Text("V1 · \(tokens.appearance.rawValue.capitalized)").font(tokens.typography.heading)
                Text("Foundry Studio · Porcelain, graphite, and cobalt.")
                    .foregroundStyle(tokens.colors.inkSecondary.color)
                MaterialExample()
                TokenSection("Colors") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: tokens.space.inline)], spacing: tokens.space.stack) {
                        ForEach(swatches, id: \.0) { name, color in
                            VStack(alignment: .leading, spacing: tokens.space.inline) {
                                RoundedRectangle(cornerRadius: tokens.shape.radii[1])
                                    .fill(color.color).frame(height: 40)
                                    .overlay(RoundedRectangle(cornerRadius: tokens.shape.radii[1]).stroke(tokens.colors.lineStrong.color))
                                    .accessibilityHidden(true)
                                Text(name).font(tokens.typography.caption)
                                Text("\(color.hex) · \(color.alpha.formatted())")
                                    .font(tokens.typography.caption.monospaced()).foregroundStyle(tokens.colors.inkSecondary.color)
                            }
                        }
                    }
                }
                TokenSection("Typography") {
                    Text("Title · Native ideas").font(tokens.typography.title)
                    Text("Heading · Clear hierarchy").font(tokens.typography.heading)
                    Text("Body · System text scales with your preferences.").font(tokens.typography.body)
                    Text("Label · Continue").font(tokens.typography.label)
                    Text("Caption · Supporting context").font(tokens.typography.caption)
                    Text("Code · let idea = 1").font(tokens.typography.code)
                }
                TokenSection("Spacing") {
                    Text("Points · inline \(Int(tokens.space.inline)) · stack \(Int(tokens.space.stack)) · section \(Int(tokens.space.section)) · page \(Int(tokens.space.page))")
                        .font(tokens.typography.caption)
                    ForEach(Array(tokens.space.steps.enumerated()), id: \.offset) { index, size in
                        HStack(spacing: tokens.space.inline) {
                            Text("\(index + 1) · \(Int(size))").font(tokens.typography.code)
                            Rectangle().fill(tokens.colors.accent.color).frame(width: size, height: tokens.space.inline)
                                .accessibilityHidden(true)
                        }
                    }
                }
                TokenSection("Shape and actions") {
                    ForEach(tokens.shape.radii, id: \.self) { radius in
                        Text("Radius \(Int(radius))").padding(tokens.space.inline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(tokens.colors.surfaceSunk.color, in: RoundedRectangle(cornerRadius: radius))
                            .overlay(RoundedRectangle(cornerRadius: radius).stroke(tokens.colors.lineStrong.color))
                    }
                    Text("Minimum touch target: 44 pt. Content can grow.").font(tokens.typography.caption)
                    Button { actions += 1 } label: {
                        Text("Primary action").frame(minHeight: tokens.shape.minimumInteractive)
                            .contentShape(Rectangle())
                    }
                        .buttonStyle(.borderedProminent).tint(tokens.colors.fill.color)
                        .foregroundStyle(tokens.colors.fillInk.color)
                    Button { actions += 1 } label: {
                        Text("Secondary action").frame(minHeight: tokens.shape.minimumInteractive)
                            .contentShape(Rectangle())
                    }.buttonStyle(.bordered)
                    Text("Actions: \(actions)").font(tokens.typography.caption)
                }
                TokenSection("Motion") {
                    Text("Durations: \(tokens.motion.milliseconds.map(String.init).joined(separator: " / ")) ms")
                    Text(tokens.motion.reduced ? "Reduced motion is active." : "Standard motion is active.")
                        .font(tokens.typography.caption)
                    Circle().fill(tokens.colors.accent.color).frame(width: 24, height: 24)
                        .offset(x: shifted ? tokens.space.steps[9] : 0)
                        .frame(maxWidth: .infinity, alignment: .leading).frame(height: 48)
                        .animation(tokens.motion.standardAnimation, value: shifted).accessibilityHidden(true)
                    Button("Toggle position") { shifted.toggle() }.buttonStyle(.bordered)
                    Text(shifted ? "Position: end" : "Position: start").font(tokens.typography.caption)
                }
            }.padding(tokens.space.page).frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(tokens.colors.surfaceGround.color)
    }
}

private struct TokenSection<Content: View>: View {
    @Environment(\.foundry) private var tokens
    let title: String
    let content: Content
    init(_ title: String, @ViewBuilder content: () -> Content) { self.title = title; self.content = content() }
    var body: some View {
        FoundrySurface {
            VStack(alignment: .leading, spacing: tokens.space.stack) {
                Text(title).font(tokens.typography.heading).accessibilityAddTraits(.isHeader)
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(tokens.space.page)
        }
    }
}

private struct MaterialExample: View {
    @Environment(\.foundry) private var tokens
    @State private var moved = false
    @State private var selections = 0

    var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.stack) {
            Text("Floating surfaces").font(tokens.typography.heading)
            Text("Theme: \(tokens.materials.style.rawValue.capitalized) · Floating: \(tokens.materials.floating.rawValue.capitalized)")
                .font(tokens.typography.caption)
            ZStack(alignment: .bottom) {
                GeometryReader { geometry in
                    ZStack {
                        LinearGradient(colors: [tokens.colors.surfaceSunk.color, tokens.colors.accentTint.color], startPoint: .topLeading, endPoint: .bottomTrailing)
                        RoundedRectangle(cornerRadius: 32).fill(tokens.colors.accent.color)
                            .frame(width: geometry.size.width * 0.55, height: 180)
                            .rotationEffect(.degrees(moved ? 30 : -20))
                            .offset(x: moved ? 50 : -40, y: moved ? 20 : -30)
                        Circle().fill(tokens.colors.info.color).frame(width: 100, height: 100)
                            .offset(x: geometry.size.width * 0.3, y: -30)
                    }.frame(width: geometry.size.width, height: geometry.size.height)
                }.accessibilityHidden(true)
                FoundrySurface(.floating) {
                    VStack(alignment: .leading, spacing: tokens.space.inline) {
                        Text("Scene controls").font(tokens.typography.label)
                        ViewThatFits(in: .horizontal) {
                            HStack { sceneActions }
                            VStack(alignment: .leading) { sceneActions }
                        }
                        Text("Selections: \(selections) · Scene: \(moved ? "B" : "A")")
                            .font(tokens.typography.caption)
                    }.padding(tokens.space.stack)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .foregroundStyle(.primary)
                }.padding(tokens.space.stack)
            }
            .frame(minHeight: 300)
            .clipShape(RoundedRectangle(cornerRadius: tokens.shape.panel))
            Text("Move the scene to inspect the material. Content panels below stay opaque.")
                .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
        }
    }

    @ViewBuilder private var sceneActions: some View {
        Button { moved.toggle() } label: {
            Text("Move scene").frame(minHeight: tokens.shape.minimumInteractive)
        }.buttonStyle(.bordered)
        Button { selections += 1 } label: {
            Text("Select object").frame(minHeight: tokens.shape.minimumInteractive)
        }.buttonStyle(.bordered)
    }
}
