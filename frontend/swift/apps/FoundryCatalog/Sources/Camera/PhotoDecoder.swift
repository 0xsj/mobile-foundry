import CoreGraphics
import Foundation
import FoundryGraphics
import FoundryKernel
import ImageIO

/// Normalize EXIF orientation and bound allocation before admitting camera pixels.
enum PhotoDecoder {
    static func decode(_ data: Data) -> AppResult<RasterImage> {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 2048,
                kCGImageSourceShouldCacheImmediately: true,
              ] as CFDictionary),
              (1...2048).contains(image.width), (1...2048).contains(image.height)
        else { return .failure(invalid) }
        var rgba = Data(count: image.width * image.height * 4)
        let drawn = rgba.withUnsafeMutableBytes { bytes -> Bool in
            guard let space = CGColorSpace(name: CGColorSpace.sRGB),
                  let context = CGContext(data: bytes.baseAddress, width: image.width,
                    height: image.height, bitsPerComponent: 8, bytesPerRow: image.width * 4,
                    space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
                        | CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
            context.setFillColor(CGColor(gray: 1, alpha: 1))
            context.fill(CGRect(x: 0, y: 0, width: image.width, height: image.height))
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return true
        }
        guard drawn, let value = RasterImage(width: image.width, height: image.height, rgba: rgba)
        else { return .failure(invalid) }
        return .success(value)
    }
    static let invalid = Failure.invalid(
        .init(message: "This photo could not be opened.", code: "camera.photo-invalid"), fields: [:])
}
