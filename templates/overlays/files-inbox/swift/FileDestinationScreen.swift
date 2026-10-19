import FoundryKernel
import FoundryServices
import ImageIO
import SwiftUI

struct FileDestinationScreen: View {
    @Bindable var store: FileDestinationStore
    var body: some View {
        NavigationStack {
            Form {
                if store.busy { ProgressView("Opening file…") }
                if let file = store.file {
                    Section("Current file") {
                        Text(file.spec.name).accessibilityIdentifier("destination.name")
                        Text("\(file.spec.byteSize) bytes · \(file.spec.contentType)")
                        Text(file.status == .available ? "Available" : "Awaiting upload")
                        if file.status == .available {
                            Button("Download preview") { store.download() }.disabled(store.busy).accessibilityIdentifier("destination.download")
                        }
                    }
                }
                if let failure = store.failure {
                    Text(failure.kind == .notFound ? "This file is no longer available." : failure.meta.message)
                        .accessibilityIdentifier("destination.failure")
                }
                if let bytes = store.bytes { DestinationPhoto(bytes: bytes) }
                Button("Refresh file") { store.refresh() }.disabled(store.busy).accessibilityIdentifier("destination.refresh")
                Text("Opening a notification leaves it unread. File access is checked again; an upload notice does not guarantee the file is still available.").font(.caption)
            }
            .navigationTitle("File details")
            .toolbar { Button("Close file") { store.close() } }
        }
    }
}
private struct DestinationPhoto: View {
    let bytes: Data
    @State private var thumbnail: CGImage?
    @State private var loaded = false
    var body: some View {
        VStack {
            Text("Download verified").accessibilityIdentifier("destination.verified")
            if let thumbnail { Image(decorative: thumbnail, scale: 1).resizable().scaledToFit() }
            else if loaded { Text("No image preview is available for this file.") }
        }.task(id: bytes) {
            thumbnail = await Task.detached { () -> CGImage? in
                guard let source = CGImageSourceCreateWithData(bytes as CFData, nil) else { return nil }
                return CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 1600] as CFDictionary)
            }.value
            loaded = true
        }
    }
}
