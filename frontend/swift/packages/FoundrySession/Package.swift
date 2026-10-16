// swift-tools-version: 6.2
import PackageDescription
let package = Package(
    name: "FoundrySession", platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundrySession", targets: ["FoundrySession"])],
    dependencies: [.package(path: "../FoundryKernel"), .package(path: "../FoundryHTTP")],
    targets: [
        .target(name: "FoundrySession", dependencies: ["FoundryKernel", "FoundryHTTP"], linkerSettings: [.linkedFramework("Security")]),
        .testTarget(name: "FoundrySessionTests", dependencies: ["FoundrySession"]),
    ]
)
