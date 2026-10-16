import SwiftUI

struct LibraryScreen: View {
    var body: some View {
        ProductPage(title: "Library", subtitle: "Saved for later", symbol: "books.vertical") {
            Text("Your collection starts here.")
        }
    }
}
