import CoreGraphics
import Foundation
import FoundryKernel
import ImageIO
import Testing
import UniformTypeIdentifiers

@testable import FoundryCatalog

@Test func cameraDecodeBoundsAndNormalizesOrientation() throws {
    let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
    let context = try #require(CGContext(data: nil, width: 3000, height: 1500,
        bitsPerComponent: 8, bytesPerRow: 3000 * 4, space: space,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
    context.setFillColor(CGColor(red: 0.8, green: 0.2, blue: 0.1, alpha: 0.5))
    context.fill(CGRect(x: 0, y: 0, width: 3000, height: 1500))
    let image = try #require(context.makeImage())
    let encoded = NSMutableData()
    let destination = try #require(CGImageDestinationCreateWithData(encoded, UTType.png.identifier as CFString, 1, nil))
    CGImageDestinationAddImage(destination, image, [kCGImagePropertyOrientation: 6] as CFDictionary)
    #expect(CGImageDestinationFinalize(destination))
    let photo = try PhotoDecoder.decode(encoded as Data).get()
    #expect(photo.width <= 2048 && photo.height <= 2048)
    #expect(photo.height == photo.width * 2)
    // RasterImage admission also establishes that formerly transparent pixels are opaque.
}

@Test func cameraDecodeRefusesMalformedBytes() {
    switch PhotoDecoder.decode(Data([1, 2, 3])) {
    case .failure(let failure): #expect(failure.kind == .invalid)
    case .success: Issue.record("Malformed bytes were admitted")
    }
}
