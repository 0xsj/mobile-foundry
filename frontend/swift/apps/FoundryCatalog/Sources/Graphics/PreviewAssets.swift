import CoreGraphics
import Foundation
import FoundryGraphics
import FoundryKernel
import ImageIO

/// Catalog assets only. Other features inject their admitted image or mesh.
enum PreviewAssets {
  /// Code-authored straight-alpha fixture: a translucent disc and orbit ring.
  static func overlay() -> AlphaImage {
    let size = 256
    var bytes = [UInt8](repeating: 0, count: size * size * 4)
    for y in 0..<size {
      for x in 0..<size {
        let u = (Double(x) + 0.5) / Double(size)
        let v = (Double(y) + 0.5) / Double(size)
        let distance = hypot(u - 0.5, v - 0.5)
        let ring = max(0, 1 - abs(distance - 0.37) / 0.035)
        let disc = min(1, max(0, (0.26 - hypot(u - 0.43, v - 0.43)) / 0.018)) * 0.82
        let satellite = min(1, max(0, (0.09 - hypot(u - 0.77, v - 0.72)) / 0.012))
        let alpha = max(ring, max(disc, satellite))
        let i = (y * size + x) * 4
        bytes[i] = UInt8((70 + 175 * v).rounded())
        bytes[i + 1] = UInt8((120 + 55 * v).rounded())
        bytes[i + 2] = UInt8((245 - 100 * v).rounded())
        bytes[i + 3] = UInt8((alpha * 255).rounded())
      }
    }
    // Dimensions and byte count are fixed here; a failure is a programmer defect.
    return AlphaImage(width: size, height: size, rgba: Data(bytes))!
  }
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
