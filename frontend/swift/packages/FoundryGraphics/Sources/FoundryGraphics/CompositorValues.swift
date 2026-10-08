import Foundation

/// Owned top-left straight sRGB RGBA8. Transparent RGB is ignored during upload.
public final class AlphaImage: Sendable {
  public let width: Int
  public let height: Int
  let rgba: Data
  public init?(width: Int, height: Int, rgba: Data) {
    guard (1...4096).contains(width), (1...4096).contains(height), rgba.count == width * height * 4
    else { return nil }
    self.width = width
    self.height = height
    self.rgba = Data(rgba)
  }
}

/// Convert before interpolation: filtering straight-alpha or encoded RGB creates fringes.
func linearPremultiplied(_ source: Data) -> Data {
  let transfer = (0...255).map { value -> Double in
    let c = Double(value) / 255
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
  }
  var bytes = [UInt8](source)
  for i in stride(from: 0, to: bytes.count, by: 4) {
    let alpha = Double(bytes[i + 3])
    for channel in 0..<3 {
      bytes[i + channel] = UInt8((transfer[Int(bytes[i + channel])] * alpha).rounded())
    }
  }
  return Data(bytes)
}

public enum CompositeBlend: String, CaseIterable, Sendable {
  case normal, multiply, screen
  public var shaderIndex: Float {
    switch self {
    case .normal: 0
    case .multiply: 1
    case .screen: 2
    }
  }
}

/// Bounded edits. Scale/radius/feather use canvas height; blur uses target pixels.
/// Centers use normalized top-left coordinates. Comparison 1 is original, 0 composed.
public struct CompositeSettings: Equatable, Sendable {
  public let opacity, scale, radius, feather, blur, glow, comparison: Float
  public let layerCenter, maskCenter: EffectPoint
  public let maskEnabled: Bool
  public let blend: CompositeBlend
  public init(
    opacity: Float = 0.85, scale: Float = 0.65,
    layerCenter: EffectPoint = .init(), maskCenter: EffectPoint = .init(),
    radius: Float = 0.32, feather: Float = 0.12, blur: Float = 0,
    glow: Float = 0.35, comparison: Float = 0.5,
    maskEnabled: Bool = true, blend: CompositeBlend = .normal
  ) {
    func bound(_ v: Float, _ lo: Float, _ hi: Float, _ fallback: Float) -> Float {
      v.isFinite ? min(hi, max(lo, v)) : fallback
    }
    self.opacity = bound(opacity, 0, 1, 0.85)
    self.scale = bound(scale, 0.15, 1, 0.65)
    self.layerCenter = layerCenter
    self.maskCenter = maskCenter
    self.radius = bound(radius, 0.05, 0.75, 0.32)
    self.feather = bound(feather, 0, 0.3, 0.12)
    self.blur = bound(blur, 0, 24, 0)
    self.glow = bound(glow, 0, 1, 0.35)
    self.comparison = bound(comparison, 0, 1, 0.5)
    self.maskEnabled = maskEnabled
    self.blend = blend
  }
}

/// Texture payload estimates exclude driver overhead and native depth/display buffers.
public struct GraphicsProfile: Sendable {
  public let frame, width, height, passes, uploads, targetAllocations: Int
  public let inputTextureBytes, offscreenTextureBytes: Int
  public let cpuEncodeMilliseconds: Double
  public let gpuMilliseconds: Double?
  public let gpuTiming: String
  public init(
    frame: Int, width: Int, height: Int, passes: Int = 4, uploads: Int,
    targetAllocations: Int, inputTextureBytes: Int, offscreenTextureBytes: Int,
    cpuEncodeMilliseconds: Double, gpuMilliseconds: Double?, gpuTiming: String
  ) {
    self.frame = frame
    self.width = width
    self.height = height
    self.passes = passes
    self.uploads = uploads
    self.targetAllocations = targetAllocations
    self.inputTextureBytes = inputTextureBytes
    self.offscreenTextureBytes = offscreenTextureBytes
    self.cpuEncodeMilliseconds = cpuEncodeMilliseconds
    self.gpuMilliseconds = gpuMilliseconds
    self.gpuTiming = gpuTiming
  }
}
