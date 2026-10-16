# Clean local product entry

This slice separates a small Daily product from the kitchen-sink catalog on
Swift and Kotlin. It is a buildable reference entry, not a generated starter.

| Boundary | Behavior / owner |
| --- | --- |
| Product application | Owns entry point, display name/identity inputs, theme selection, navigation and placeholder screens. |
| Daily navigation | Starts at Today; Today, Library, Activity and Account have stable distinct identities and outlined icons. |
| Theme | Account exposes Solid/Glass selection. Changing theme keeps the selected destination. Native accessibility reductions still apply. |
| Lifetime | Swift keeps state above the themed view subtree for the window lifetime. Kotlin saves selection/material preference through Activity recreation. Neither promises durable preferences across a fresh launch. |
| Dependencies | Product imports UI; its closure is UI → Query → Kernel. Catalog, camera, graphics, session, domain examples and backend setup are optional future capabilities. |
| Branding | Product metadata is editable without modifying shared components. No developer signing team, permission requests or credential namespace is inherited from the catalog. |
| Extraction | A candidate inventory records source, dependency closure, fixture paths, Gradle settings overlay and identity edit locations. A checker verifies this inventory against current source. |

Native tab chrome owns iOS navigation safe areas; Android composition consumes
safe drawing and keyboard insets once. Each screen owns vertical scrolling.
The placeholders perform no authentication, disk writes or network requests.
Swift tab chrome follows the platform; Solid/Glass selection controls reusable
floating surfaces and does not replace native iOS tab rendering.

Independent generation, product renaming, generated build/run, destination
safety and CLI/toolchain diagnostics remain the next starter acceptance gate.
