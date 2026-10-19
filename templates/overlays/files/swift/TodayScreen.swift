import FoundryKernel
import FoundryServices
import FoundrySession
import ImageIO
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct TodayScreen: View {
    let model: ConnectedSessionModel
    var body: some View {
        if model.controller.snapshot.phase == .signedIn, let lease = model.controller.lease {
            FilesForm(store: model.files).id(lease.scope)
        } else {
            ContentUnavailableView("Account files", systemImage: "photo.on.rectangle", description: Text("Sign in from Account to view your files."))
        }
    }
}

private struct PhotoDraft: Sendable { let spec: UploadSpec; let bytes: Data }
private struct FilesForm: View {
    @Bindable var store: FilesStore
    @State private var selection: PhotosPickerItem?
    @State private var draft: PhotoDraft?
    @State private var importing = false
    @State private var deleting: RemoteFile?
    @State private var reviewing: FileRecoveryReview?
    var body: some View {
        Form {
            Section("Account files") {
                Text("Showing \(store.files.count) files.").accessibilityIdentifier("files.loaded")
                PhotosPicker("Choose photo", selection: $selection, matching: .images, preferredItemEncoding: .compatible)
                    .disabled(!store.canEdit || importing).accessibilityIdentifier("files.choose")
                if importing { ProgressView("Preparing photo…") }
                if let draft {
                    Text("\(draft.spec.name) · \(draft.spec.byteSize) bytes").accessibilityIdentifier("files.draft")
                    Button("Upload photo") {
                        store.upload(draft.spec, bytes: draft.bytes, ticket: store.selectionTicket)
                        self.draft = nil; selection = nil
                    }.disabled(!store.canEdit || importing).accessibilityIdentifier("files.upload")
                    Button("Discard photo", role: .destructive) { self.draft = nil; selection = nil }
                }
                Button("Refresh files") { store.refresh() }.disabled(store.busy).accessibilityIdentifier("files.refresh")
                if store.busy { ProgressView("Updating files…") }
                if store.pending {
                    Text("An operation needs confirmation. Retry keeps the same photo and command.").accessibilityIdentifier("files.pending")
                    Button("Retry pending operation") { store.retry() }.disabled(store.busy).accessibilityIdentifier("files.retry")
                    Button("Review pending operation") { reviewing = store.reviewPending() }.disabled(store.busy).accessibilityIdentifier("files.review")
                }
                if store.blockedByOtherScope { Text("Return to the original account to settle its pending file operation before another change.") }
                if let failure = store.failure { Text(failure.meta.message).foregroundStyle(.red).accessibilityIdentifier("files.failure") }
                if store.nextCursor != nil {
                    Button("Load more files") { store.loadMore() }.disabled(!store.canLoadMore).accessibilityIdentifier("files.more")
                }
                if store.files.count >= 200 { Text("Showing up to 200 files. Refresh for the latest page.").font(.caption) }
            }
            Section("Files") {
                if store.ready && store.files.isEmpty { Text("Your account has no files.").accessibilityIdentifier("files.empty") }
                ForEach(store.files) { file in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(file.spec.name)
                        Text("\(file.spec.byteSize) bytes · \(file.status == .available ? "Available" : "Awaiting upload")").font(.caption)
                        if file.status == .available {
                            Button("Download preview") { store.open(file) }.buttonStyle(.borderless).disabled(store.busy).accessibilityIdentifier("files.open.\(file.id)")
                        }
                        Button("Delete file", role: .destructive) { deleting = file }.buttonStyle(.borderless).disabled(!store.canEdit).accessibilityIdentifier("files.delete.\(file.id)")
                    }.padding(.vertical, 4)
                }
            }
            Section { Text("Photos up to 10 MiB. Accepted operations are saved in private, backup-excluded recovery storage before transfer. Relaunch restores the original operation; retry or review it explicitly. Downloads are checked against the server's size and digest.").font(.caption) }
        }
        .confirmationDialog("Delete this file?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            if let deleting { Button("Confirm delete", role: .destructive) { store.delete(deleting); self.deleting = nil } }
        } message: { Text("This removes access for the whole account.") }
        .alert("Forget local recovery?", isPresented: Binding(get: { reviewing != nil }, set: { if !$0 { reviewing = nil } }), presenting: reviewing) { review in
            Button("Forget local recovery", role: .destructive) { store.forgetLocalRecovery(review); reviewing = nil }
            Button("Keep pending operation", role: .cancel) { reviewing = nil }
        } message: { review in
            Text("\(review.deleting ? "Delete" : "Upload"): \(review.name)\n\n\(review.consequence) Retry keeps the original command; forgetting requires a fresh file list before another change.")
        }
        .sheet(isPresented: Binding(get: { store.preview != nil }, set: { if !$0 { store.closePreview() } })) {
            if let bytes = store.preview {
                NavigationStack {
                    VerifiedPhotoPreview(bytes: bytes).navigationTitle(store.previewName ?? "File")
                        .toolbar { Button("Close preview") { store.closePreview() } }
                }
            }
        }
        .task(id: selection) {
            guard let selection else { return }; importing = true; draft = nil
            defer { importing = false }
            do {
                guard let bytes = try await selection.loadTransferable(type: Data.self), !bytes.isEmpty, bytes.count <= 10 * 1024 * 1024 else { throw PhotoImportError.invalid }
                let prepared = await Task.detached { () -> PhotoDraft? in
                    guard let source = CGImageSourceCreateWithData(bytes as CFData, nil), let identifier = CGImageSourceGetType(source),
                          let type = UTType(identifier as String), let mime = type.preferredMIMEType,
                          case .success(let spec) = UploadSpec.prepare(name: "Photo." + (type.preferredFilenameExtension ?? "img"), contentType: mime, bytes: bytes) else { return nil }
                    return PhotoDraft(spec: spec, bytes: bytes)
                }.value
                try Task.checkCancellation(); guard let prepared else { throw PhotoImportError.invalid }; draft = prepared
            } catch is CancellationError { } catch {
                if !Task.isCancelled { store.importFailed(ticket: store.selectionTicket) }
            }
        }
    }
}
private enum PhotoImportError: Error { case invalid }
private struct VerifiedPhotoPreview: View {
    let bytes: Data
    @State private var thumbnail: CGImage?
    @State private var loaded = false
    var body: some View {
        VStack(spacing: 16) {
            Text("Download verified").accessibilityIdentifier("files.verified")
            if let thumbnail { Image(decorative: thumbnail, scale: 1).resizable().scaledToFit() }
            else if loaded { Text("No image preview is available for this file.") }
            else { ProgressView() }
        }.padding().task {
            thumbnail = await Task.detached { () -> CGImage? in
                guard let source = CGImageSourceCreateWithData(bytes as CFData, nil) else { return nil }
                return CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 1600] as CFDictionary)
            }.value
            loaded = true
        }
    }
}
