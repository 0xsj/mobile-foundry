// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "FoundryData",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "FoundryData", targets: ["FoundryData"])],
    dependencies: [.package(path: "../FoundryServices"), .package(path: "../FoundryKernel"), .package(path: "../FoundrySession")],
    targets: [
        .target(name: "FoundryData", dependencies: ["FoundryServices", "FoundryKernel", "FoundrySession"], linkerSettings: [.linkedLibrary("sqlite3")]),
        .testTarget(name: "FoundryDataTests", dependencies: ["FoundryData"]),
    ]
)
