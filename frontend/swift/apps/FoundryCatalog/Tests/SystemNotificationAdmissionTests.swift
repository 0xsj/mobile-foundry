import Foundation
import Testing
import UserNotifications
import FoundryServices
@testable import FoundryCatalog

@Suite struct SystemNotificationAdmissionTests {
@Test func remoteEnvelopeNeverProjectsAPSOrAuthorityIntoApplicationData() {
    let value = RemoteNotificationPreview(id: UUID().uuidString.lowercased())!
    var info: [AnyHashable: Any] = value.payload.reduce(into: [:]) { $0[$1.key] = $1.value }
    info["aps"] = ["alert": ["title": "ignored server text", "body": "ignored"], "sound": "default"] as [String: Any]
    #expect(NotificationApplicationDelegate.remote(info) == value)
    info["account_id"] = UUID().uuidString; #expect(NotificationApplicationDelegate.remote(info) == nil)
    info.removeValue(forKey: "account_id"); info["version"] = 1
    #expect(NotificationApplicationDelegate.remote(info) == nil)
    info["version"] = "1"; info["aps"] = "invalid envelope"
    #expect(NotificationApplicationDelegate.remote(info) == nil)
    // A local request containing the remote payload cannot masquerade as an APNs trigger.
    let content = UNMutableNotificationContent(); content.userInfo = value.payload
    let request = UNNotificationRequest(identifier: "unowned", content: content, trigger: nil)
    #expect(NotificationApplicationDelegate.open(request) == nil)
}
@Test func delegateAdmitsOnlyItsOwnedPreviewSlot() {
    let value = LocalNotificationPreview(id:UUID().uuidString.lowercased())!
    let content = UNMutableNotificationContent();content.userInfo=value.payload;content.categoryIdentifier="catalog.local-preview.v1"
    func request(_ id: String = "catalog.local-preview.v1") -> UNNotificationRequest { .init(identifier:id,content:content,trigger:nil) }
    #expect(NotificationApplicationDelegate.preview(request()) == value)
    #expect(NotificationApplicationDelegate.preview(request("another-feature")) == nil)
    content.categoryIdentifier="unowned";#expect(NotificationApplicationDelegate.preview(request()) == nil)
    content.categoryIdentifier="catalog.local-preview.v1";content.userInfo["account_id"]="untrusted";#expect(NotificationApplicationDelegate.preview(request()) == nil)
    content.userInfo=value.payload;content.userInfo["version"]=1;#expect(NotificationApplicationDelegate.preview(request()) == nil)
}
}
