// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryPhysics",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryPhysics", targets: ["FoundryPhysics"])],
    targets: [
        .target(name: "FoundryPhysics"),
        .testTarget(name: "FoundryPhysicsTests", dependencies: ["FoundryPhysics"]),
    ]
)
