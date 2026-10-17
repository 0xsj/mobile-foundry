import FoundryKernel
import FoundryServices
import FoundryUI
import ImageIO
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct RemoteFilesCatalogCard: View {
    let model: RemoteFilesCatalogModel
    @State private var pickerPresented = false
    @State private var selection: PhotosPickerItem?
    @State private var ticket = 0
    @State private var importing = false
    @Environment(\.foundry) private var t
    var body: some View {
        Card {
            SectionHeader("Account files", subtitle: "Upload a gallery photo, download it, then open it in the image editor. Maximum 10 MiB per file.")
            ActionButton("Choose photo to upload", enabled: model.canUpload && !importing) { ticket = model.selectionTicket; pickerPresented = true }
                .accessibilityIdentifier("go.files.choose")
            ActionButton("Refresh files", variant: .secondary, enabled: !model.busy && !importing) { model.refresh() }
            if model.busy || importing { ProgressView("Working with files…") }
            if model.pending {
                Text("An unconfirmed operation is retained for this account while this screen's owner is alive.").font(t.typography.caption)
                ActionButton("Retry same file operation", variant: .secondary, enabled: !model.busy && !importing) { model.retry() }
            }
            if let failure = model.failure { Text(failure.meta.message).font(t.typography.caption).accessibilityIdentifier("go.files.failure") }
            if model.files.isEmpty && !model.busy { Text("No files in this account.").font(t.typography.caption) }
            ForEach(model.files) { file in
                VStack(alignment: .leading, spacing: t.space.stack) {
                    Text(file.spec.name).font(t.typography.label)
                    Text("\(file.status.rawValue) · \(file.spec.byteSize) bytes · v\(file.version)").font(t.typography.caption)
                    if file.status == .available && file.spec.contentType.hasPrefix("image/") {
                        ActionButton("Download and edit preview", variant: .quiet, enabled: !model.busy && !importing) { model.open(file) }
                    }
                    ActionButton("Delete file", variant: .destructive, enabled: model.canUpload && !importing) { model.delete(file) }
                }
            }
            if model.hasMore { Text("Showing the first 20 files.").font(t.typography.caption) }
            Text("Editor adjustments are a local preview. Uploading an edited render is a separate export capability.").font(t.typography.caption)
        }
        .photosPicker(isPresented: $pickerPresented, selection: $selection, matching: .images, preferredItemEncoding: .compatible)
        .task(id: selection) {
            guard let item = selection else { return }; let current = ticket; importing = true
            defer { importing = false; selection = nil }
            do {
                guard let bytes = try await item.loadTransferable(type: Data.self), !bytes.isEmpty, bytes.count <= 10 * 1024 * 1024 else {
                    model.importFailed(.invalid(.init(message: "Choose a photo smaller than 10 MiB."), fields: [:]), ticket: current); return
                }
                let prepared = await Task.detached(priority: .userInitiated) { () -> AppResult<UploadSpec> in
                    guard let source = CGImageSourceCreateWithData(bytes as CFData, nil), let identifier = CGImageSourceGetType(source),
                          let type = UTType(identifier as String)?.preferredMIMEType else { return .failure(PhotoDecoder.invalid) }
                    return UploadSpec.prepare(name: "Photo." + (UTType(identifier as String)?.preferredFilenameExtension ?? "image"), contentType: type, bytes: bytes)
                }.value
                try Task.checkCancellation()
                switch prepared {
                case .success(let spec): model.upload(spec: spec, bytes: bytes, ticket: current)
                case .failure(let value): model.importFailed(value, ticket: current)
                }
            } catch is CancellationError {} catch { model.importFailed(.unavailable(.init(message: "The selected photo could not be opened.")), ticket: current) }
        }
        .sheet(isPresented: Binding(get: { model.preview != nil }, set: { if !$0 { model.preview = nil } })) {
            if let image = model.preview { NavigationStack { ImageEditorView(image: image, title: "Downloaded photo", startsEdited: true) } }
        }
    }
}
