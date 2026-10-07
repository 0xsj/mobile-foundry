# Product projects

Create one directory per product. A connected product can use this shape:

```text
projects/<name>/
  frontend/swift/
  frontend/kotlin/
  backend/
  contracts/
  infra/
  README.md
```

Include the surfaces and services that the product actually uses. Product
features and domain rules live here; reusable infrastructure and UI behavior
belong to the foundry's native libraries.

Local package or module references are useful during foundry development.
Independent generated products will receive their selected source and tooling
when project generation is implemented.
