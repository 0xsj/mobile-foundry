# Shared graphics assets

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
