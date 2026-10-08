# Camera and shared photo editor construction

Authority: [camera/photo behavior](../../contracts/behavior/camera-photo.md).

- Add Camera between Library and Studio in both native shells. Keep the native
  tab semantics and existing catalog presentation. Keep transient captured pixels
  at app-shell scope and no camera session alive behind another tab.
- Extract existing Image studio preview/controls into app-owned ImageEditorView
  and ImageEditorContent. Preserve the catalog's initial edits and renderer tests.
  Add four named presets that set existing bounded adjustment values.
- Swift uses a serial-queue AVFoundation adapter, a native preview-layer host,
  explicit permission admission and bounded ImageIO thumbnail decoding. Declare
  NSCameraUsageDescription in project.yml, then regenerate Xcode wiring.
- Kotlin uses CameraX PreviewView/ImageCapture bound to the current lifecycle,
  explicit runtime permission, scoped unbinding and JPEG decoding off main.
  Declare CAMERA as optional hardware and pin CameraX in the version catalog.
- Keep sample-photo entry available on unsupported/denied devices. Make retake
  return to the viewfinder and release the previous preview resource tree.
- Verify native builds, decode bounds/orientation, actual filter/edit pixels,
  tab positioning, capture permissions and camera disposal. Record physical
  device limits; update notes and indexes with actual checks.

## Completed evidence — 2026-10-08

Both shells have five destinations and a centered Camera tab. Each app has one
shared photo editor used by Camera and Image studio. Native sessions, permission
admission, orientation/downsampling and transient shell-owned pixels are wired.

Simulator/debug native builds and an unsigned generic-iOS build pass. The iOS
app suite passes eighteen checks, including two photo decoder tests. Android
host regressions and all nine focused camera/shell/preview checks pass; the latter
exercise synthetic native capture, real Mono/Vivid GPU pixels, tab placement,
renderer removal, Retake and camera closure. Manual iOS checks exercise sample
loading, individual filter buttons/selected states, photo retention and Retake.
CLI screenshots establish the observed layout and grayscale output. Notes and
canonical asset checks pass. Physical camera quality, sensor rotation,
interruptions and device performance require hardware execution; denied OS
permission dialogs and durable save/export remain outside this evidence.

Start the learning handoff at [capture ownership](../../notes/patterns/capture-assets-and-preview-lifetime.md).

## Gallery follow-up — 2026-10-08

Choose photo replaces Sample photo with PhotosPicker/PickVisualMedia. Importers
adapt selected handles/URIs to bounded upright opaque images, with a 32 MiB
encoded limit and recoverable loading errors. Picking/importing pauses capture.
Kotlin pins ExifInterface 1.4.2 for gallery rotation/reflection; camera callback
rotation stays separate. Swift tab labels clear inherited fill variants and use
outline symbols; Kotlin's existing tab vectors remain stroked.

Eighteen iOS checks, seven focused Android camera/shell checks and Android host
regressions pass. Native apps package. Actual iOS picker selection reaches the
editor. Android selection-result tests execute real import/filter paths but do
not establish system-picker UI. See the updated native walkthroughs for limits.
