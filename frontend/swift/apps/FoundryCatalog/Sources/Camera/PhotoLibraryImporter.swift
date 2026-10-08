import Foundation
import FoundryGraphics
import FoundryKernel
import OSLog
import PhotosUI
import SwiftUI

enum PhotoLibraryImporter {
    static func load(_ item: PhotosPickerItem) async throws -> AppResult<RasterImage> {
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                return .failure(PhotoDecoder.invalid)
            }
            try Task.checkCancellation()
            guard data.count <= 32 * 1024 * 1024 else {
                return .failure(.invalid(.init(message: "Choose a photo smaller than 32 MB.",
                    code: "photo.too-large"), fields: [:]))
            }
            let result = await Task.detached(priority: .userInitiated) { PhotoDecoder.decode(data) }.value
            try Task.checkCancellation()
            return result
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "photos")
                .error("Photo import failed: \(String(reflecting: error), privacy: .private)")
            return .failure(.unavailable(.init(message: "This photo could not be opened. Please choose another.",
                code: "photo.import")))
        }
    }
}
