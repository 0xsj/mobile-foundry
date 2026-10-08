// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryGraphics",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryGraphics", targets: ["FoundryGraphics"])],
    dependencies: [.package(path: "../FoundryKernel")],
    targets: [
        .target(name: "FoundryGraphics", dependencies: ["FoundryKernel"], resources: [.copy("Shaders")]),
        .testTarget(name: "FoundryGraphicsTests", dependencies: ["FoundryGraphics"]),
    ]
)
