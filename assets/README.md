# Shared graphics and branding assets

- `source`: Original model, texture, environment, and other graphics inputs.
- `fixtures`: Small deterministic assets for the catalog and verification.
- `manifests`: Logical asset IDs, versions, and references to platform variants.

Keep platform shader source with its native rendering implementation. Asset
conversion scripts belong in root `scripts`; converted output belongs under
the ignored `.cache` directory until a distribution or app bundling step
selects it.

Record an asset's format and conversion settings with its manifest so both
platforms can reproduce the intended example.

The first product previews bundle [studio-still-life.png](source/studio-still-life.png)
and [studio-lamp.json](source/studio-lamp.json). The photograph was generated for
this repository using the built-in imagegen tool; the lamp is authored by the
repository's deterministic rotational-profile script. [The manifest](manifests/studio-previews.json)
records exact generation prompt/tool mode, formats, hashes and runtime conversion.
No external model, environment map or texture is required.

```sh
make assets-sync
make assets-check
```

Sync regenerates the lamp, copies both canonical assets into each app's bundle,
and updates manifest hashes. Check verifies the authored mesh, manifest and app
copies without writing. Generated app copies are intentional tracked resources;
temporary screenshots and build products remain in ignored `.cache`.

## Mobile Foundry app icon

The abstract cobalt ribbon follows Foundry Studio's porcelain/cobalt direction.
It was generated with the built-in imagegen tool on 2026-10-08, without reference
images. The [manifest](manifests/mobile-foundry-icon.json) records the exact prompt,
tool mode, source/derivative hashes and packaging command.

Keep [the original](source/mobile-foundry-icon-master-v1.png) at its generated
1254 × 1254 resolution. The [selected iOS derivative](source/mobile-foundry-icon-v1.png)
is an opaque RGB8 1024 × 1024 PNG, resized with macOS sips without changing the
composition. Its tracked copy is selected by the iOS AppIcon asset catalog.
The OS supplies the outside mask; the artwork has square corners and a full
background. This slice supplies the default iOS icon, with no separate appearance
variants or layered Icon Composer asset.

The existing sync/check commands now also verify icon provenance hashes,
PNG dimensions/color type, the bundled copy and its asset-catalog selection.
Changing artwork deliberately requires updating the manifest rather than letting
sync silently accept a new generated source. Asset packaging does not establish
device signing or a TestFlight upload.
