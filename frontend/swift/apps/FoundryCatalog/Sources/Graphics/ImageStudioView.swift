import FoundryGraphics
import FoundryKernel
import FoundryUI
import SwiftUI

struct ImageStudioView: View {
  @State private var image: RasterImage?
  @State private var failure: Failure?
  @State private var attempt = 0

  var body: some View {
    Group {
      if let image {
        ImageEditorView(image: image)
      } else if let failure {
        VStack {
          Text(failure.publicInfo().meta.message)
          Button("Try again") { self.failure = nil; attempt += 1 }
        }
      } else {
        ProgressView("Loading photograph…")
      }
    }
    .navigationTitle("Image studio").navigationBarTitleDisplayMode(.inline)
    .task(id: attempt) {
      let result = await Task.detached(priority: .userInitiated) { PreviewAssets.image() }.value
      guard !Task.isCancelled else { return }
      switch result {
      case .success(let value): image = value
      case .failure(let value): failure = value
      }
    }
  }
}
