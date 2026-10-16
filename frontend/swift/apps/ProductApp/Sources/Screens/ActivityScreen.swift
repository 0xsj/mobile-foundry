import SwiftUI

struct ActivityScreen: View {
    var body: some View {
        ProductPage(title: "Activity", subtitle: "Keep up with what matters", symbol: "clock") {
            Text("You're all caught up.")
        }
    }
}
