import SwiftUI

struct TodayScreen: View {
    let displayName: String
    var body: some View {
        ProductPage(title: displayName, subtitle: "Today", symbol: "house") {
            Text("A fresh start for your day.")
        }
    }
}
