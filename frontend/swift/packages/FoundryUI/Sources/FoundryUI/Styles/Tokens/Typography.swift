import SwiftUI

/// Native semantic text styles preserve Dynamic Type; no fixed web font sizes.
public struct FoundryTypography: Sendable {
    public var title: Font { .largeTitle.weight(.semibold) }
    public var heading: Font { .title2.weight(.semibold) }
    public var body: Font { .body }
    public var label: Font { .headline }
    public var caption: Font { .caption }
    public var code: Font { .system(.body, design: .monospaced) }
}
