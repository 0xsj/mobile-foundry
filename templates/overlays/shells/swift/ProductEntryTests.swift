@testable import ProductApp
import Foundation
import Testing

@Test func runtimeProductBrandingAndPermissionsBelongToTheProduct() {
    #expect(ProductConfiguration.current.displayName == Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String)
    #expect(Bundle.main.bundleIdentifier != "dev.mobilefoundry.catalog")
    #expect(Bundle.main.object(forInfoDictionaryKey: "NSCameraUsageDescription") == nil)
}
