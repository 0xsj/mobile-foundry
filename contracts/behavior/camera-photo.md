# Camera and photo preview

The shell orders five peer tabs: Home, Library, Camera, Studio, Account. Camera
is the middle destination. It owns a native live viewfinder, permission admission,
front/back selection, still capture, and a capture-to-edit transition. Camera
access starts only after Enable camera; leaving the destination or foreground,
opening the photo picker, importing, or entering the editor stops capture work.
Permission denial and missing hardware leave Choose photo usable. It opens the
native single-image library picker without requesting full-library permission
or camera permission. It replaces the original Sample photo action; bundled
samples remain in Image studio within the catalog.

Captured and selected photos are normalized for orientation (including library
reflection metadata), converted to opaque sRGB RGBA8,
and downsampled to a maximum 2048-pixel edge before entering the existing
RasterImage boundary. Camera handles and platform image objects never enter
FoundryGraphics/core:graphics. Library import accepts encoded images up to 32 MiB;
unreadable, unsupported and oversized selections receive recoverable copy.
Canceling the picker leaves the current screen intact. Imported/captured pixels
remain in memory. Selected cloud photos may download through the system picker;
the app adds no upload, photo-library write, export or durable edit behavior.

The photo editor reuses Image studio's GPU preview and controls: exposure,
saturation, vignette, before/after comparison, pan, zoom, quality and reset.
Natural, Mono, Vivid and Soft are presets of those same adjustment values,
applied after capture; the live camera viewfinder is unfiltered. Both camera
and the original catalog use one native editor implementation per platform.

Only one capture/import runs at a time. Late completions from an inactive camera
are ignored. Busy and unavailable controls expose accessible labels and states.
Permission changes are rechecked on foreground activation. Expected permission,
hardware and capture failures receive deliberate copy; unexpected exceptions
retain diagnostic identity. The native session is unbound/stopped on disposal.
The camera image is a transient app-shell draft across tab switches; editor
controls may reset when that editor is recreated. Process restoration does not
restore image bytes or imply that a pending capture completed.

Simulator/emulator library checks establish layout and editing. Android virtual
capture can additionally exercise native CameraX; physical lens, interruptions,
orientation and capture quality need device evidence.
