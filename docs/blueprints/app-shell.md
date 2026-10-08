# Placeholder shell construction

Authority: [shell behavior](../../contracts/behavior/app-shell.md) and
[component map](../COMPONENTS.md).

1. Reserve mirrored component leaf folders with .gitkeep and list their intended
   contents. Keep current controls intact; do not add unused APIs or dependencies.
2. Swift app composition owns saved tab selection and a full-screen catalog
   presentation. Native TabView owns tab semantics and system glass. CatalogView
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
