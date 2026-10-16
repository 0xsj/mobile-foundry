# Product entry construction

Authority: [product entry behavior](../../contracts/behavior/product-entry.md)
and [starter readiness](../STARTER-READINESS.md).

| Files / symbols | Ownership |
| --- | --- |
| Swift apps/ProductApp/project.yml; Config/Product.xcconfig | Separate product target/scheme, local package dependency, identity/version/signing inputs and generated Info.plist. |
| Swift Sources/ProductApp.swift, ProductConfiguration.swift, ProductRootView.swift | Window state, runtime branding/default style, admitted tab identities and native theme/tab composition. |
| Swift Sources/Screens | Distinct placeholder screens and app-owned page presentation; Account projects a controlled theme switch. |
| Kotlin project/product/build.gradle.kts, product.properties, manifest/resources | Separate application module, editable application ID/version, runtime label and product-owned outline/icon resources. |
| Kotlin product source MainActivity, ProductConfiguration, ProductRoot, screens | Native entry, saved tab/theme owners, one inset policy and distinct placeholder screens. |
| templates/profiles/native-daily.json; overlays/kotlin/settings.gradle.kts | Candidate copy inventory preserving source/fixture depth, selected closure and product-only module registration. No generation executable. |
| scripts/check_starter_profile.py | Validate source existence, explicit module closure, product/catalog separation and overlay registration. |
| Product native tests | Real entry, four tabs, selected-screen/theme retention, Android recreation and native runtime branding. |
| Make targets, setup/readiness and learning notes | Reproducible entry commands, evidence and pending standalone milestone. |

Construction order: declare product behavior and file owners; wire minimal
application builds and identity inputs; compose native Daily tabs with app-owned
screens/state; record selected dependency/test closure; validate inventory;
build both products and exercise actual native entries; record evidence and
remaining CLI gate. Existing catalog source and developer signing configuration
are outside this change.

Verification uses separate iOS derived data and the Kotlin product module.
The dependency inventory check is static evidence; native execution is reference
product evidence. Neither substitutes for independently generated acceptance.
