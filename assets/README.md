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
