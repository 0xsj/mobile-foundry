import Foundation

private func bounded(_ value: Float, _ lower: Float, _ upper: Float, _ fallback: Float) -> Float {
  value.isFinite ? min(upper, max(lower, value)) : fallback
}

/// Owned, opaque top-left sRGB RGBA8 pixels. Identity changes only with the asset.
public final class RasterImage: Sendable {
  public let width: Int
  public let height: Int
  let rgba: Data
  public init?(width: Int, height: Int, rgba: Data) {
    guard (1...4096).contains(width), (1...4096).contains(height), rgba.count == width * height * 4,
      stride(from: 3, to: rgba.count, by: 4).allSatisfy({ rgba[rgba.startIndex + $0] == 255 })
    else { return nil }
    self.width = width
    self.height = height
    self.rgba = Data(rgba)
  }
}

/// Packed triangle vertices: position xyz, normal xyz, material slot.
public final class PreviewMesh: Sendable {
  let vertices: [Float]
  public var vertexCount: Int { vertices.count / 7 }
  public init?(vertices: [Float]) {
    guard !vertices.isEmpty, vertices.count % 21 == 0, vertices.count / 7 <= 100000,
      vertices.allSatisfy(\.isFinite)
    else { return nil }
    for i in stride(from: 0, to: vertices.count, by: 7) {
      guard vertices[i..<i + 3].allSatisfy({ abs($0) <= 10 }),
        vertices[i + 3..<i + 6].allSatisfy({ abs($0) <= 10 }),
        vertices[i + 3..<i + 6].reduce(Float(0), { $0 + $1 * $1 }) > 0.000001,
        [Float(0), 1, 2].contains(vertices[i + 6])
      else { return nil }
    }
    self.vertices = vertices
  }
}

public struct ImageAdjustments: Equatable, Sendable {
  public let exposure: Float
  public let saturation: Float
  public let vignette: Float
  public init(exposure: Float = 0, saturation: Float = 1, vignette: Float = 0) {
    self.exposure = bounded(exposure, -2, 2, 0)
    self.saturation = bounded(saturation, 0, 2, 1)
    self.vignette = bounded(vignette, 0, 1, 0)
  }
}

public struct ImageViewport: Equatable, Sendable {
  public let zoom: Float
  public let x: Float
  public let y: Float
  public init(zoom: Float = 1, x: Float = 0, y: Float = 0) {
    self.zoom = bounded(zoom, 1, 4, 1)
    self.x = bounded(x, -0.75, 0.75, 0)
    self.y = bounded(y, -0.75, 0.75, 0)
  }
  public func moved(dx: Float, dy: Float) -> Self { .init(zoom: zoom, x: x + dx, y: y + dy) }
  public func scaled(by factor: Float) -> Self {
    .init(zoom: zoom * (factor.isFinite && factor > 0 ? factor : 1), x: x, y: y)
  }
}

public struct OrbitCamera: Equatable, Sendable {
  public let yaw: Float
  public let pitch: Float
  public let distance: Float
  public init(yaw: Float = 0.35, pitch: Float = 0.15, distance: Float = 4.5) {
    let angle = yaw.isFinite ? yaw : 0.35
    let period = Float.pi * 2
    let wrapped = (angle + .pi).truncatingRemainder(dividingBy: period)
    self.yaw = (wrapped < 0 ? wrapped + period : wrapped) - .pi
    self.pitch = bounded(pitch, -0.8, 0.8, 0.15)
    self.distance = bounded(distance, 2.5, 7, 4.5)
  }
  public func rotated(dx: Float, dy: Float) -> Self {
    .init(yaw: yaw + dx * .pi * 2, pitch: pitch + dy * .pi, distance: distance)
  }
  public func scaled(by factor: Float) -> Self {
    .init(yaw: yaw, pitch: pitch, distance: distance / (factor.isFinite && factor > 0 ? factor : 1))
  }
}

public enum ProductFinish: String, CaseIterable, Sendable { case porcelain, cobalt, bronze }
public enum CanvasGesture: Sendable {
  case drag(x: Float, y: Float, dx: Float, dy: Float)
  case zoom(Float)
}
public enum PreviewContent: Equatable, Sendable {
  case image(RasterImage, ImageAdjustments, ImageViewport, comparison: Float)
  case product(PreviewMesh, OrbitCamera, ProductFinish)
  public static func == (lhs: Self, rhs: Self) -> Bool {
    switch (lhs, rhs) {
    case (.image(let a, let ae, let av, let ac), .image(let b, let be, let bv, let bc)):
      a === b && ae == be && av == bv && ac == bc
    case (.product(let a, let ac, let af), .product(let b, let bc, let bf)):
      a === b && ac == bc && af == bf
    default: false
    }
  }
}
