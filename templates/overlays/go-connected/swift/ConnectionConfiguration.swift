import Foundation

/// Public deployment settings. Device credentials belong only in CredentialVault.
enum ConnectionConfiguration {
    static let baseURL = "http://127.0.0.1:8080/v1"
    #if DEBUG
    static let allowLocalHTTP = true
    #else
    static let allowLocalHTTP = false
    #endif
}
