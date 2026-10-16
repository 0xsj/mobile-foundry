import FoundryUI
import SwiftUI

struct AccountScreen: View {
    @Binding var glass: Bool
    var body: some View {
        ProductPage(title: "Account", subtitle: "Your preferences", symbol: "person") {
            ToggleField("Glass", isOn: $glass, help: "Use translucent surfaces.")
        }
    }
}
