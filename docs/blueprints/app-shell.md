# Native app shell construction

Authority: [shell behavior](../../contracts/behavior/app-shell.md) and
[component map](../COMPONENTS.md).

1. Compose shared AppShell slots and controlled TabBar items. UI leaves are
   implemented; application routes and feature owners stay outside the UI package.
2. Swift app composition owns saved tab selection and a full-screen catalog
   presentation. Shared TabBar adapts native TabView, which owns tab semantics and system glass. CatalogView
   accepts an optional close action and retains its existing NavigationStack.
3. Kotlin app composition owns saveable selection/presentation. Only the active
   placeholder is composed. A reusable value-driven tab bar renders Material
   navigation items inside an existing floating Surface/backdrop.
   MainNavigation accepts an optional exit action and guards its root entry.
4. Add no service integrations or fictional content. Account hosts the shared
   material switch; Studio hosts the catalog entry. Keep all existing examples.
5. Build both apps; exercise all tabs, style switching, catalog detail/back/close
   and saved-state restoration. Run relevant Android navigation regressions and
   native layout checks. Record actual evidence and limits in learning notes.

Future feature stacks, deep links, authentication gates, badges and adaptive
tablet navigation require their own behavior and consumers.

## Completion evidence

Completed 2026-10-08. Both native builds and Android host regressions pass;
all sixteen existing iOS app/layout checks pass. Manual iOS execution covers
all four tabs, material switching and catalog detail/back/close. Both shell
tests passed in the complete Android run; 35 of 36 checks passed, with the old
root/theme assumption corrected in the existing token test. The focused final
shell/theme rerun passes all eight checks. Both Home screenshots were inspected.
Notes/assets checks and diff checks pass. See the native app walkthroughs for
saved-state, screenshot, platform and physical-device limits.

## Shared layout completion

On 2026-10-09 the five-tab app was migrated to shared AppShell and TabBar
adapters. Swift retains native TabView chrome and app-owned saved selection;
Compose retains floating Surface/Backdrop rendering, active-content composition,
app-owned insets and catalog-scoped ViewModelStore disposal. Camera admission
and inactive capture lifetime remain in the existing camera feature.

All originally reserved UI leaves now contain implementation. The additional
Shells gallery is a second consumer with caller-owned page values. Both builds,
64 iOS app cases, five focused Android shell/gallery cases including both existing
app-shell regressions, and nine Swift/eight Kotlin UI package cases pass.
See [the usage guide](ui-components.md#stacks-separators-and-shells) and learning
notes for execution boundaries. This batch does not publish a new TestFlight build.
