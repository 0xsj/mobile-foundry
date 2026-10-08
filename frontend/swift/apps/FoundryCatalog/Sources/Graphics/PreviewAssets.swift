import CoreGraphics
import Foundation
import FoundryGraphics
import FoundryKernel
import ImageIO

/// Catalog assets only. Other features inject their admitted image or mesh.
enum PreviewAssets {
  static func image() -> AppResult<RasterImage> {
    guard let url = Bundle.main.url(forResource: "studio-still-life", withExtension: "png"),
      let source = CGImageSourceCreateWithURL(url as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
    else { return .failure(missing) }
    guard (1...4096).contains(image.width), (1...4096).contains(image.height) else {
      return .failure(invalid)
    }
    var rgba = Data(count: image.width * image.height * 4)
    let drawn = rgba.withUnsafeMutableBytes { bytes -> Bool in
      guard let space = CGColorSpace(name: CGColorSpace.sRGB),
        let context = CGContext(
          data: bytes.baseAddress, width: image.width, height: image.height, bitsPerComponent: 8,
          bytesPerRow: image.width * 4, space: space,
          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            | CGBitmapInfo.byteOrder32Big.rawValue)
      else { return false }
      context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
      return true
    }
    guard drawn, let value = RasterImage(width: image.width, height: image.height, rgba: rgba)
    else { return .failure(invalid) }
    return .success(value)
  }
  static func mesh() -> AppResult<PreviewMesh> {
    struct File: Decodable {
      let version: Int
      let layout: String
      let vertices: [Float]
    }
    guard let url = Bundle.main.url(forResource: "studio-lamp", withExtension: "json"),
      let data = try? Data(contentsOf: url)
    else { return .failure(missing) }
    guard let file = try? JSONDecoder().decode(File.self, from: data), file.version == 1,
      file.layout == "position3-normal3-slot1", let mesh = PreviewMesh(vertices: file.vertices)
    else { return .failure(invalid) }
    return .success(mesh)
  }
  private static let missing = Failure.notFound(
    .init(message: "The bundled preview asset could not be loaded.", code: "graphics.asset-missing")
  )
  private static let invalid = Failure.invalid(
    .init(message: "The preview asset is invalid.", code: "graphics.asset-invalid"), fields: [:])
}
