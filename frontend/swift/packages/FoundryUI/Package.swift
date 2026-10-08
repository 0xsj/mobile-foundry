// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryUI",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryUI", targets: ["FoundryUI"])],
    dependencies: [.package(path: "../FoundryKernel"), .package(path: "../FoundryQuery")],
    targets: [.target(name: "FoundryUI", dependencies: ["FoundryKernel", "FoundryQuery"])]
)
