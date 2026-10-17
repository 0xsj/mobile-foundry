import Foundation
import FoundryKernel
import FoundryServices
import UIKit
import UserNotifications

@MainActor final class AppleSystemNotifications: SystemNotificationPort {
    static let identifier = "catalog.local-preview.v1"
    private let center: UNUserNotificationCenter
    init(center: UNUserNotificationCenter = .current()) { self.center = center }
    func access() async throws -> AppResult<NotificationAccess> {
        try Task.checkCancellation()
        let settings = await center.notificationSettings(); try Task.checkCancellation()
        let authorization: NotificationAuthorization
        switch settings.authorizationStatus {
        case .authorized: authorization = .allowed
        case .provisional, .ephemeral: authorization = .quiet
        case .notDetermined, .denied: authorization = .notGranted
        @unknown default: return .failure(SystemNotificationFailures.unavailable)
        }
        return .success(.init(authorization: authorization, deliveryEnabled: settings.alertSetting == .enabled || settings.notificationCenterSetting == .enabled))
    }
    func requestPermission() async throws -> AppResult<NotificationAccess> {
        try Task.checkCancellation()
        do { _ = try await center.requestAuthorization(options: [.alert, .sound]) }
        catch { if (error as NSError).domain == UNErrorDomain { return .failure(SystemNotificationFailures.unavailable) }; throw error }
        return try await access()
    }
    func postPreview(_ preview: LocalNotificationPreview) async throws -> AppResult<Void> {
        switch try await access() {
        case .failure(let error): return .failure(error)
        case .success(let value): guard value.canPost else { return .failure(SystemNotificationFailures.blocked) }
        }
        let content = UNMutableNotificationContent()
        content.title = "Mobile notification preview"
        content.body = "Tap to return to System notifications."
        content.sound = .default; content.categoryIdentifier = Self.identifier; content.userInfo = preview.payload
        // One owned pending/delivered slot. Never clear another feature's notifications.
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
        let request = UNNotificationRequest(identifier: Self.identifier, content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false))
        do { try await center.add(request); return .success(()) }
        catch {
            let native = error as NSError
            if native.domain == UNErrorDomain { return .failure(native.code == UNError.Code.notificationsNotAllowed.rawValue ? SystemNotificationFailures.blocked : SystemNotificationFailures.unavailable) }
            throw error
        }
    }
    func clearPreview() async throws -> AppResult<Void> {
        try Task.checkCancellation()
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier]); return .success(())
    }
    func openSettings() async throws -> AppResult<Void> {
        try Task.checkCancellation()
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString), await UIApplication.shared.open(url) else { return .failure(SystemNotificationFailures.unavailable) }
        return .success(())
    }
}

@MainActor enum SystemNotificationComposition { static let opens = NotificationOpenRouter() }

/// Installed before launch completes; UIApplicationDelegateAdaptor retains this weak center delegate.
@MainActor final class NotificationApplicationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushTokenComposition.provider.didRegister(deviceToken)
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: any Error) {
        // Provider details can contain sensitive context; publish only the owned failure.
        PushTokenComposition.provider.didFail()
    }
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self; return true
    }
    nonisolated static func preview(_ request: UNNotificationRequest) -> LocalNotificationPreview? {
        guard !(request.trigger is UNPushNotificationTrigger) else { return nil }
        guard request.identifier == "catalog.local-preview.v1", request.content.categoryIdentifier == "catalog.local-preview.v1", request.content.userInfo.count == 3 else { return nil }
        var payload: [String: String] = [:]
        for (key, value) in request.content.userInfo {
            guard let key = key as? String, let value = value as? String, value.utf8.count <= 128 else { return nil }; payload[key] = value
        }
        return LocalNotificationPreview.decode(payload)
    }
    nonisolated static func remote(_ userInfo: [AnyHashable: Any]) -> RemoteNotificationPreview? {
        guard Set(userInfo.keys.compactMap { $0 as? String }) == Set(["aps", "version", "kind", "preview_id"]),
              userInfo.count == 4, userInfo["aps"] is [AnyHashable: Any] else { return nil }
        var payload: [String: String] = [:]
        for key in ["version", "kind", "preview_id"] {
            guard let value = userInfo[key] as? String, value.utf8.count <= 128 else { return nil }
            payload[key] = value
        }
        return RemoteNotificationPreview.decode(payload)
    }
    nonisolated static func open(_ request: UNNotificationRequest) -> NotificationOpen? {
        if request.trigger is UNPushNotificationTrigger { return remote(request.content.userInfo).map(NotificationOpen.remote) }
        return preview(request).map(NotificationOpen.local)
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let open = Self.open(notification.request)
        if case .remote(let preview) = open {
            Task { @MainActor in SystemNotificationComposition.opens.recordReceipt(preview) }
        }
        completionHandler(open == nil ? [] : [.banner, .list, .sound])
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier, let open = Self.open(response.notification.request) {
            Task { @MainActor in SystemNotificationComposition.opens.receive(open) }
        }
        completionHandler()
    }
}
