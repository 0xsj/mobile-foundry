# Compose backdrop layers

A Compose backdrop host can replay a separately recorded background underneath
sharp foreground controls using coordinates relative to the same root.

## Origin and evidence

Inspected 2026-10-08 with Kotlin 2.3.20, Compose UI 1.10.6 (BOM 2026.03.01),
compile SDK 36 and min SDK 24. [Compose graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
document recording and replaying GraphicsLayers. [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)
is available from API 31. Runtime outcomes belong to the UI module walkthrough;
this implementation does not establish physical-device performance.

## What and why

`rememberGraphicsLayer` owns a layer with composition lifetime and releases it
when disposed. The background draw modifier records a sharp layer, records a
second layer replaying it, applies BlurEffect to the second, and draws the sharp
one in the scene. Floating surfaces replay the blurred layer through a rounded
clip and tint, then draw their content sharply. Solid/reduced themes bypass
recording; they retain the same host and content slots.

The two BoxScope slots give callers alignment modifiers. Only the foreground
receives the source through a composition local, preventing source recursion.
`onGloballyPositioned` supplies source and surface positions in root coordinates.
Translation uses source origin minus surface origin. This host supports layout
translation and scrolling, not arbitrary rotated/scaled ancestor transforms.

## Example and Kotlin mechanics

```kotlin
// Excerpt: the outer receiver owns the source content; the inner receiver records a layer.
sharp.record { this@drawWithContent.drawContent() }
blurred.record { drawLayer(sharp) }
```

The qualified `this@drawWithContent` names the outer DrawScope receiver despite
the nested receiver lambda. An unqualified call would obscure which draw scope
owns the original content. This is Kotlin's labeled receiver syntax, not a
coroutine or asynchronous capture. Recording happens during drawing.

## Gotchas

- Modifier.blur blurs a composable's own content; it does not automatically
  sample siblings behind it. Applying it to controls also blurs their text.
- The source excludes overlays; child controls are never recursively sampled.
- API below 31 or a missing host uses an opaque raised surface. No extra
  dependency or hidden system setting is used for blur.
- Android has an explicit reduction flag here. No system-wide transparency
  observer is claimed. Higher-opacity tint bounds backdrop variation, while
  content panels keep the palette's ordinary contrast guarantees.
- A SurfaceView/GL renderer is not captured by these Compose drawing commands.
  A future graphics integration must deliberately provide a compatible source.
- Full-scene effects allocate GPU/offscreen resources. Keep the first use bounded;
  frame cost and battery behavior need device measurement before broader usage.

## Used in and related

Read [the UI walkthrough](../modules/project/core/ui/README.md) for implementation
and device test links. Compare [theme scope and touch bounds](compose-token-theme-and-touch-bounds.md)
and [shared material ownership](../../../../notes/patterns/material-themes-and-backdrops.md).
