@testable import ProductApp
import Testing

@Test func browseFilteringAdmissionAndBackKeepSearch() {
    var state = ProductState(glass: true)
    #expect(state.visibleCollections.count == 3)
    state.filter = "  TWO  "
    #expect(state.visibleCollections == [.collectionTwo])
    state.select("unknown")
    #expect(state.selectedCollection == nil)
    state.select("collection-two")
    state.glass = false
    state.back()
    #expect(state.selectedCollection == nil && state.filter == "  TWO  " && !state.glass)
    state.filter = "absent"
    #expect(state.visibleCollections.isEmpty)
    state.filter = ""
    #expect(state.visibleCollections == ProductCollection.allCases)
}
