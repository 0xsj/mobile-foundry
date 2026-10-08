import Foundation
import Testing

@testable import FoundryGraphics

private struct CompositeCases: Decodable {
  struct Values: Decodable { let input, expected: [Float] }
  struct Pixel: Decodable { let input, expected: [UInt8] }
  let settings: [Values]
  let pixels: [Pixel]
}
private func compositeFixtures() throws -> CompositeCases {
  var root = URL(fileURLWithPath: #filePath)
  for _ in 0..<7 { root.deleteLastPathComponent() }
  return try JSONDecoder().decode(
    CompositeCases.self,
    from: Data(
      contentsOf: root.appendingPathComponent("contracts/fixtures/graphics/compositor.json")))
}
@Test func sharedCompositeBoundsAndPremultipliedLinearPixels() throws {
  let f = try compositeFixtures()
  for row in f.settings {
    let i = row.input
    let s = CompositeSettings(
      opacity: i[0], scale: i[1], radius: i[2], feather: i[3], blur: i[4], glow: i[5],
      comparison: i[6])
    #expect(
      [s.opacity, s.scale, s.radius, s.feather, s.blur, s.glow, s.comparison] == row.expected)
  }
  for row in f.pixels { #expect([UInt8](linearPremultiplied(Data(row.input))) == row.expected) }
  #expect(
    CompositeSettings(
      opacity: .nan, scale: .infinity, radius: .nan, feather: .nan, blur: .infinity, glow: .nan,
      comparison: .nan) == .init())
  #expect(CompositeBlend.allCases.map(\.shaderIndex) == [0, 1, 2])
}
@Test func transparentAssetsOwnBytesAndPreservePreviewIdentity() throws {
  var bytes = Data([255, 0, 200, 0])
  let a = try #require(AlphaImage(width: 1, height: 1, rgba: bytes))
  bytes[0] = 0
  #expect(a.rgba[0] == 255)
  #expect(AlphaImage(width: 0, height: 1, rgba: bytes) == nil)
  #expect(AlphaImage(width: 4097, height: 1, rgba: bytes) == nil)
  #expect(AlphaImage(width: 1, height: 1, rgba: Data()) == nil)
  let base = try #require(RasterImage(width: 1, height: 1, rgba: Data([128, 128, 128, 255])))
  let b = try #require(AlphaImage(width: 1, height: 1, rgba: a.rgba))
  #expect(PreviewContent.composite(base, a, .init()) == .composite(base, a, .init()))
  #expect(PreviewContent.composite(base, a, .init()) != .composite(base, b, .init()))
}
