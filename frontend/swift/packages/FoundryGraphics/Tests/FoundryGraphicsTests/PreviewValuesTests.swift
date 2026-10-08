import Foundation
import Testing

@testable import FoundryGraphics

private struct PreviewCases: Decodable {
  struct Values: Decodable { let input, expected: [Float] }
  struct Mesh: Decodable {
    let name: String
    let valid: Bool
    let vertices: [Float]
  }
  let adjustments, viewports, cameras: [Values]
  let meshes: [Mesh]
}
private func fixtures() throws -> PreviewCases {
  var root = URL(fileURLWithPath: #filePath)
  for _ in 0..<7 { root.deleteLastPathComponent() }
  return try JSONDecoder().decode(
    PreviewCases.self,
    from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/graphics/previews.json"))
  )
}
@Test func previewInputsAdmitBoundedValuesAcrossPlatforms() throws {
  let f = try fixtures()
  for row in f.adjustments {
    let v = ImageAdjustments(
      exposure: row.input[0], saturation: row.input[1], vignette: row.input[2])
    #expect([v.exposure, v.saturation, v.vignette] == row.expected)
  }
  for row in f.viewports {
    let v = ImageViewport(zoom: row.input[0], x: row.input[1], y: row.input[2])
    #expect([v.zoom, v.x, v.y] == row.expected)
  }
  for row in f.cameras {
    let v = OrbitCamera(yaw: row.input[0], pitch: row.input[1], distance: row.input[2])
    #expect(zip([v.yaw, v.pitch, v.distance], row.expected).allSatisfy { abs($0 - $1) < 0.00001 })
  }
  #expect(ImageAdjustments(exposure: .nan, saturation: .infinity, vignette: .nan) == .init())
  #expect(ImageViewport(zoom: .nan, x: .infinity, y: .nan) == .init())
  #expect(OrbitCamera(yaw: .nan, pitch: .infinity, distance: .nan) == .init())
  #expect(ImageViewport().scaled(by: -1) == .init())
  #expect(OrbitCamera().scaled(by: 0) == .init())
  #expect(ImageViewport().moved(dx: 9, dy: -9) == .init(x: 0.75, y: -0.75))
}
@Test func previewAssetsRejectMalformedDataAndOwnTheirBytes() throws {
  for row in try fixtures().meshes {
    #expect((PreviewMesh(vertices: row.vertices) != nil) == row.valid, "\(row.name)")
  }
  var bytes = Data([80, 120, 160, 255])
  let image = try #require(RasterImage(width: 1, height: 1, rgba: bytes))
  bytes[0] = 0
  #expect(image.rgba[0] == 80)
  #expect(RasterImage(width: 1, height: 1, rgba: Data([0, 0, 0, 0])) == nil)
  #expect(RasterImage(width: 4097, height: 1, rgba: Data()) == nil)
  #expect(RasterImage(width: 0, height: 1, rgba: Data()) == nil)
  #expect(RasterImage(width: 1, height: 1, rgba: Data()) == nil)
  var vertices = try #require(try fixtures().meshes.first).vertices
  let mesh = try #require(PreviewMesh(vertices: vertices))
  vertices[0] = 99
  #expect(mesh.vertices[0] == -1)
  vertices[0] = .nan
  #expect(PreviewMesh(vertices: vertices) == nil)
}
