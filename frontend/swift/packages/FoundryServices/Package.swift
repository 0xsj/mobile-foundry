// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryServices",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryServices", targets: ["FoundryServices"])],
    dependencies: [.package(path: "../FoundryKernel"), .package(path: "../FoundryHTTP")],
    targets: [
        .target(name: "FoundryServices", dependencies: ["FoundryKernel", "FoundryHTTP"]),
        .testTarget(name: "FoundryServicesTests", dependencies: ["FoundryServices"]),
    ]
)
