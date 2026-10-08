// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryQuery",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryQuery", targets: ["FoundryQuery"])],
    dependencies: [.package(path: "../FoundryKernel")],
    targets: [
        .target(name: "FoundryQuery", dependencies: ["FoundryKernel"]),
        .testTarget(name: "FoundryQueryTests", dependencies: ["FoundryQuery"]),
    ]
)
