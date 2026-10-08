# Reproducible graphics profiling

This is a workflow, not a benchmark result. No physical-device run has been
performed for the 2026-10-08 compositor slice. Simulator/emulator checks validate
rendering and lifetime; they do not establish mobile GPU, battery or thermal cost.

## Fixed catalog workload

Use **Compositor studio** with its bundled photograph and authored RGBA overlay.
Reset composition before each run, set Comparison to 0 (or tap Composed), and
leave the page open in a fixed portrait orientation. Do not drag during capture.

| Run | Changes after Reset | Purpose |
| --- | --- | --- |
| A | Composed; Balanced; Profile redraws on | Default layer, mask and glow workload |
| B | A plus blur 24, glow 1, Screen blend | Fixed blurred/glowing composition |
| C | B with Economy quality | Lower target resolution and submission budget |
| D | B with Profile redraws off | Static draw-on-change baseline |

Economy and Balanced change both target dimensions and submission budget. Record
both; this is not an experiment that isolates resolution alone. All compositor
workloads currently execute four passes, even with blur/glow zero. There is no
automatic pass elimination, timer-query fallback or adaptive quality.

1. Record device model, OS/GPU/driver, app revision and build optimization,
   profiler/version, refresh rate, brightness, battery/charging, power mode,
   initial thermal state and ambient conditions. Keep conditions consistent.
2. Launch, reset, configure, then warm up for 30 seconds. Verify uploads remain
   two, allocations remain three and dimensions remain unchanged. A rotation or
   quality change deliberately reallocates three targets. A new context resets
   its counters and uploads retained CPU assets again.
3. Capture at least 60 seconds of steady workload; inspect early and late windows
   for pacing, GPU/CPU work and thermal changes. For an energy investigation use
   longer equal-duration runs. Stop if the device/tool cannot supply the data.
4. Repeat at least three times per workload, returning to comparable conditions.
   Save traces; compare distributions from complete trace samples. Record p50,
   p95 and worst observed intervals only if the tool actually supplies them.
5. Stop redraws, background/resume and reopen. Check work stops while inactive,
   no callbacks appear after removal, and resources rebuild on Android resume.
   This is a separate correctness check, not part of the steady interval.

## iOS capture

Build/sign for a physical device in Xcode. Use an optimized configuration for
budget measurements; make a separately labeled diagnostic capture if validation
is enabled. Inspect the four passes with the Metal debugger, then capture a
timeline using Instruments' Metal System Trace or Game Performance tooling.
Review CPU/GPU overlap, resource allocation and pacing rather than just one
panel number. Apple documents these tools in
[Metal performance analysis](https://developer.apple.com/documentation/xcode/analyzing-the-performance-of-your-metal-app/)
and [Metal developer tools](https://developer.apple.com/metal/tools/).

The app's GPU interval covers its command buffer's four passes and presentation
submission. It is read after successful completion; zero timestamps are shown
as unavailable. It excludes the UI and other command buffers and does not
measure time to display. CPU encode excludes asset preparation/upload on Swift.

## Android capture

The catalog remains OpenGL ES 2 on API 24+. Its panel reports CPU encoding and
texture payload; **GPU timing unavailable** is the correct current result.
The first draw interval includes Kotlin's asset upload; program creation precedes
the timer.

Select tools based on the actual device/driver and metric. Current Android docs
recommend [Android Performance Analyzer](https://developer.android.com/agi/start)
for system profiling. Start with its linked setup/support requirements; system
tracing can investigate scheduling even when GPU counters are absent. For
[AGI frame inspection](https://developer.android.com/agi/frame-trace/frame-profiler),
validate a supported device and label OpenGL-on-ANGLE capture explicitly: that
path translates GLES to Vulkan. An unsupported capture is an unavailable result,
not grounds to report CPU time as GPU time. Restore any tool-selected driver or
debug configuration afterward and keep native and translated traces distinct.

## Run record

Copy this into the investigation's dated notes when a real run is performed:

```text
Date / revision / workload A, B, C or D:
Device / OS / GPU / driver / display refresh:
Build / optimization / validation:
Tool / version / native or translated driver:
Brightness / battery / charging / power / ambient / thermal start-end:
Target dimensions / submission budget / 4 passes:
Input payload / offscreen payload / uploads / allocations:
Warm-up / trace duration / repeat number / trace artifact:
Observed CPU encoding / GPU interval / presentation pacing (source for each):
Unavailable metrics / anomalies / thermal drift:
Conclusion supported by these measurements / next experiment:
```

Texture payload estimates exclude display/depth buffers, driver overhead and
other resources. One-per-second UI snapshots cannot supply a representative
percentile distribution. Keep [measurement distinctions](../notes/techniques/graphics-profiling-and-measurement.md)
and the [compositor contract](../contracts/behavior/compositor.md) beside results.
