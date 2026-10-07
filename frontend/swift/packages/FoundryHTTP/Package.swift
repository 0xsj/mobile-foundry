// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryHTTP",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryHTTP", targets: ["FoundryHTTP"])],
    dependencies: [.package(path: "../FoundryKernel")],
    targets: [
        .target(name: "FoundryHTTP", dependencies: ["FoundryKernel"]),
        .testTarget(name: "FoundryHTTPTests", dependencies: ["FoundryHTTP"]),
    ]
)
