# Placeholder app shell

The catalog starts in a five-destination shell: Home, Library, Camera, Studio and
Account. Camera is the middle destination and implements
[capture and photo preview](camera-photo.md). The other destinations are empty
feature placeholders with their own title and icon. Tabs select peer
destinations; they do not push a growing back stack.
Selection survives native saved-state restoration. No service, account session,
storage or domain behavior is implied by these placeholders.

Studio opens the existing catalog as a separate presentation. Closing it
returns to Studio. Catalog detail back navigation stays inside the catalog;
Android system Back at its root closes it without removing the last entry.
Leaving that presentation releases its feature owners and native renderers.

The shell starts with Glass selected for visualization. Account and the
catalog expose the same app-session Solid/Glass choice. FoundryTheme itself
still defaults to Solid. Android's floating bottom bar uses Backdrop
and Surface, with their existing opaque fallback. iOS uses native
TabView chrome: Liquid Glass on iOS 26 and system material on older supported
versions. The material switch controls Foundry surfaces, while iOS system
chrome remains platform-owned. Native accessibility and reduced-transparency
behavior take precedence over decoration.

Bottom navigation has five labeled, selected-state-accessible targets, honors
system safe areas and allows native text scaling. Screen content avoids the
bar. Icons use their outline variants in both selected and unselected states;
selection is conveyed by the native highlight, color and accessible state.
Placeholder backgrounds contain a static, subtle accent wash to make
translucency visible; they do not run an animation loop.

Reusable navigation controls receive item values, selection and callbacks.
Shared AppShell supplies bounded background/content/navigation slots; shared
TabBar adapts native chrome. The app owns destination identity, presentation,
safe-area/keyboard policy and feature lifetime. Swift puts native TabBar content
inside AppShell with an empty external navigation slot; Compose supplies its
floating bar to the bottom slot and applies system insets in the app host.
Neither primitive owns feature state or keeps an inactive camera running.
