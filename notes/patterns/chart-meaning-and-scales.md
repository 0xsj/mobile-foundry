# Chart meaning and scales

Claim: a chart's readable meaning and comparison scale belong to its feature,
while reusable drawing adapters project admitted numbers and supplied copy.

## Origin and evidence

Added 2026-10-09 for the eleventh UI batch. The Insights fixture composes six
native summary components with period selection, empty-data UI and an independent
goal. Native walkthroughs record numerical, hosted layout, pixel and interaction
checks. There is no analytics provider, event tracking or statistical inference.

## What and why

A sparkline communicates shape with little space. Scaling each sequence to its
own minimum/maximum preserves variation, but two equally tall lines need not
represent comparable magnitudes. The caller must name units, describe the period
and provide exact values or another data view. Equal x spacing also assumes
equally spaced samples: an irregular timestamp sequence needs a time-aware chart.
Connecting adjacent samples with straight segments adds no invented smoothing.

Bars use one explicit positive maximum. The fixture uses 200 minutes for both
Week and Month, so an 84-minute and a 168-minute bar preserve their relative size.
Automatically choosing a different maximum per period would make that comparison
harder. Zero, missing and negative values are different states: these nonnegative
bars represent zero, while the feature projects missing data separately. Diverging
bars need another adapter; invalid external data must be admitted at a boundary.

Example: switch from Week's 210 minutes to Month's 420. The sparkline's shape and
sample count change, category values use the same scale, and the independent
14-of-20 session goal stays unchanged. Empty insight data removes focus plots,
not the separately owned goal. Disabling goal controls retains its value.

ProgressRing's fraction only controls geometry. Its copy tells the user what the
fraction measures. Trend direction is also not a value judgment: increasing
errors and increasing completed work can require different tones. Do not hide
that policy inside a generic arrow component or derive labels from an unknown unit.

## Gotchas and alternatives

- Finite numbers can still overflow during arithmetic. Dividing sparkline values
  by their largest magnitude before subtracting extrema bounds the normalization
  range. Native numerical and pixel checks include both finite Double extremes.
- A small chart needs explicit accessibility projection. Drawing a path creates
  no readable data tree by itself. Keep summaries, category/value copy and real
  controls separate; do not flatten an interactive footer into decorative content.
- Text belongs in native layout rather than fixed canvas glyph positions. At
  larger text, move ring value copy below the drawing and let bar labels wrap.
- Small eager adapters have no promised large-series budget. Dense history,
  axes, zoom, cursors, decimation and device profiling are later capabilities.
- Silent clamping can hide a wrong scale. Bars require values within their
  declared maximum. Rings explicitly clamp finite progress geometry to 0...1;
  the caller must still supply truthful copy for over-target states.

## Used in and related

Read [behavior](../../contracts/behavior/ui-components.md#insights-and-small-charts),
[usage](../../docs/blueprints/ui-components.md#insights-and-small-charts),
[Swift native drawing](../../frontend/swift/notes/substrate/swiftui-chart-drawing-and-summaries.md)
and [Compose native drawing](../../frontend/kotlin/notes/substrate/compose-chart-drawing-and-semantics.md).
Compare [caller-owned slots](component-slots-and-caller-owned-state.md) and
[graphics measurement](../techniques/graphics-profiling-and-measurement.md).
Next: what shared domain and timestamp policy should a multi-series chart expose?
