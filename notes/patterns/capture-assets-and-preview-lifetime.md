# Capture assets and preview lifetime

Claim: a captured photograph can outlive its camera session without retaining
the session or coupling the editor to a camera framework.

## Origin and evidence

The 2026-10-08 Camera slice adds a second input to the existing native image
editor. Source inspection shows distinct camera, CPU asset and GPU resource
owners. Android emulator execution exercises capture, decoding, editing and
camera closure; iOS simulator execution can exercise the bundled image instead
of a physical lens. Compile evidence does not establish physical capture quality.

## What and why

Keep three lifetimes separate. A camera session owns access to a scarce native
device. A CPU photograph owns decoded, admitted pixels. A preview owns GPU
uploads that can be recreated from those pixels. Stopping the first must not
destroy the second; removing the editor must release the third.

Permission is an explicit user action rather than a side effect of navigation.
Visibility, foreground state and editing state then decide whether an authorized
session should run. A generation identifies the current attempt: cancellation
or disposal alone cannot prevent every native callback already in flight.
Check admission again before publishing a result into the current feature.

For example, capture → normalize orientation → downsample/flatten → admit pixels
→ stop camera → edit. A bundled photograph can enter at the admission boundary
and exercise the same renderer without pretending to test a physical camera.
Named filters are recipes for existing adjustment values, keeping camera-specific
types out of the preview API.

## Gotchas

- Camera permission does not imply permission to save to a photo library.
- A preview's mirrored front-facing view does not define saved-image orientation.
- Session disposal and ignoring obsolete callbacks solve different problems.
- Saving tab selection does not save decoded image bytes or edit recipes.
- Pixel checks should sample image content, excluding an intentionally tinted
  letterbox. A grayscale filter need not alter the canvas background.

## Used in and related

Follow the native camera sections in the [Swift app walkthrough](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#camera-and-shared-photo-editor)
and [Kotlin app walkthrough](../../frontend/kotlin/notes/modules/project/app/README.md#camera-and-shared-photo-editor).
Compare [editable values and resources](editable-values-and-renderer-resources.md),
[tabs and lifetime](tabs-and-feature-lifetime.md), and
[owned cancellation](../concepts/deadlines-and-owned-cancellation.md).

Next questions: which saved recipe and asset reference should survive process
death; and what device evidence is needed before adding live filtered capture?

## Selected-library input — 2026-10-08

Choose photo replaces the camera screen's bundled sample action. Native pickers
grant access to the chosen image rather than requiring full-library permission.
This input still follows decode → normalize → admit → edit, with no camera
session or GPU resource hidden in a library item/URI. Opening the picker and
decoding its result pause capture. A canceled selection is an ordinary no-op;
an unreadable item is a recoverable failure. Scope cancellation rejects obsolete
imports after navigation. Selected CPU pixels retain the existing transient
shell lifetime.

Metadata is a property of the input source. A camera callback can supply rotation
separately; a library file may encode rotation and reflection in EXIF. Reusing a
camera-only rotation argument for gallery files would silently display some
photos incorrectly. The native walkthroughs record how each adapter applies it
and distinguish injected picker-result checks from actual system-picker use.
