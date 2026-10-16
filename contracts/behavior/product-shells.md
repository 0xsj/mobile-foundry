# Product shell variations

Status: native product-shell examples, 2026-10-10. Existing shell primitives
remain generic layouts; app-owned compositions define navigation and page state.

Five profiles illustrate reusable product structures:

| ID | Profile | Layout and working interaction |
| --- | --- | --- |
| daily | Daily app | Four native bottom destinations; each retains an independent check-in count. |
| workspace | Workspace | Compact list/detail navigation; expanded list and detail together; item completion survives selection changes. |
| browse | Browse and collect | Local search, collection detail, saved/unsaved action and return to filtered results. |
| canvas | Canvas studio | Bounded canvas, tool controls, layer insertion and an explicit local export receipt. |
| setup | Guided setup | Three steps with back/continue/review/finish, a retained name and chosen focus. |

Profile previews fill their available viewport and use existing UI tokens and
shell primitives. Solid/Glass selection changes material independently of profile
state. Native tab icons stay outlined. Controls/labels identify local preview
effects honestly; saving a collection and exporting a canvas are local fixture
state, not disk/network/file operations.

The gallery owns state above preview branches. Returning to its chooser and
reopening a profile retains selected destinations, check-ins, item state, browse
filter/saved items, canvas title/tools/layers/receipts, and setup draft/progress.
Different profiles do not reuse one another's selection. Android deliberately
saveable state/input survives Activity recreation. Closing the gallery discards
its fixture state; process/disk persistence is not promised.

Only admitted fixture destination/item/tool/focus IDs can change state. Repeated
selection and unknown IDs are no-ops. Incremental actions read the current owner
state, rather than an old composed snapshot. Canvas layers are bounded from one
through eight; actions at the bound are disabled and refused. A finished setup
requires explicit Start again to reset its progress/name/focus.

Workspace uses two panes when the available width is at least 700 native points/dp
and native large-text policy permits it. Compact Back returns to the list while
retaining selection/completion. Expanded selection replaces the detail. Rotation
does not erase the selected item. Layout is based on the preview's available
geometry, not a phone/tablet model label. Hinge/fold posture, draggable dividers,
deep links and a full navigation back-stack implementation are separate capabilities.

The host owns system insets once. Body content scrolls independently of fixed
header/actions; keyboard-visible form content remains reachable. Navigation and
guided controls remain separate from passive backdrops/canvas drawing. These
examples add no backend, authentication, GPU renderer or new shared UI family.

Catalog compositions remain app-owned examples. Daily has an independently
verified generated starter; Workspace/Browse extraction is defined separately in
[shell starter behavior](shell-starters.md). Catalog fixture actions are not copied
as product requirements. Canvas/Setup remain extraction candidates. Follow
[readiness](../../docs/STARTER-READINESS.md) for actual generated-product evidence.
