import FoundryUI
import SwiftUI

struct TodayScreen: View {
    let displayName: String
    @State private var model = CounterModel(service: MemoryCounterService())
    var body: some View {
        ProductPage(title: displayName, subtitle: "Today", symbol: "house") {
            Text("A fresh start for your day.")
            Text("Counter: \(model.query.value ?? 0)")
            if let receipt = model.mutation.value { Text("Confirmed: \(receipt)") }
            ActionButton("Increment") { model.increment() }
        }
    }
}
