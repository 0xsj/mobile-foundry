# Graphics profiling and measurement

Claim: label what a graphics measurement observes, then reproduce a fixed
physical-device workload before drawing performance conclusions.

## Origin and evidence

The compositor's four passes made submission counters insufficient. Source
inspection establishes the timing boundaries and payload arithmetic. Tests
establish callback lifetime and resource reuse, not a phone's sustained GPU cost.
As of 2026-10-08, the repo has simulator/emulator rendering evidence and an actual
host Metal execution check; no physical-device profiling results are recorded.

## What and why

| Observation | Establishes | Does not establish |
| --- | --- | --- |
| Submitted frames | CPU submitted work | Presented FPS, latency or GPU duration |
| CPU encode duration | Time in the adapter's encoding path | Time the GPU executes commands |
| Completed Metal timestamp interval | That command buffer's GPU interval when available | Display latency, per-pass cost or sustained thermal behavior |
| Texture payload estimate | Dimensions × bytes per pixel for named owned textures | Measured total VRAM, residency or driver overhead |
| External sustained trace | Tool/device-specific workload observations | A universal budget on all phones |

Metal timing is read in command completion, never immediately after commit.
Zero/missing timestamps stay unavailable. The current GLES 2 bridge has no GPU
timer adapter and explicitly reports unavailable; substituting a CPU stopwatch
would make the number misleading. The shader workload still runs normally.
See [Apple's timestamp contract](https://developer.apple.com/documentation/metal/mtlcommandbuffer/gpustarttime).

## Example method

Freeze assets, settings, orientation and target dimensions. Warm up uploads and
pipelines; check upload count stays two and target allocations stay three. Run
identical redraws, capture a sustained trace, then repeat at another quality.
Report build/tool/device/driver versions, power and thermal conditions, trace
interval and percentiles from **all trace samples**, not the UI's one-per-second
latest snapshot. Repeat runs and retain trace files beside the run record.
The [device protocol](../../docs/GRAPHICS-PROFILING.md) supplies exact catalog
workloads, sampling steps and an unfilled record format.

## Gotchas

- Swift uploads during preparation, before the reported draw encoding interval;
  Kotlin's first draw interval includes texture preparation/upload but excludes
  program creation. Neither first-frame
  number is a portable benchmark. Warm-state costs still measure different APIs.
- A static edit reports completion; continuous reporting is throttled. The UI
  panel is an observer, not a percentile recorder or adaptive quality controller.
- A profiler can change the renderer/driver. AGI's OpenGL ES frame capture uses
  ANGLE translation; label that path and compare with an ordinary native run.
  Availability depends on device/driver. Current Android docs recommend APA
  for system profiling; consult its current support before capture.
  Sources: [AGI setup](https://developer.android.com/agi/start),
  [frame capture](https://developer.android.com/agi/frame-trace/frame-profiler).
- Never add a synchronous GPU wait to production frames merely to obtain a
  timing. Waiting in a deterministic readback test is a different boundary.

## Used in and related

Used by Compositor studio's optional GraphicsProfile callback and workload
toggle. Read [compositing passes](../patterns/premultiplied-compositing-and-render-passes.md),
[native adapter evidence](shared-fixtures-and-native-adapters.md) and
[frame ownership](../patterns/renderer-frame-ownership.md).

Next: measure a representative physical iPhone and Android device; only then
choose a budget and investigate adaptive quality or a timer-query adapter.
