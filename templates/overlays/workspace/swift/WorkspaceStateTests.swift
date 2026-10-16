@testable import ProductApp
import Testing

@Test func workspaceAdmissionBackAndMaterialKeepSelection() {
    var state = ProductState(glass: true)
    #expect(state.selectedProject == nil && !state.detailPresented)
    state.select("unknown")
    #expect(state.selectedProject == nil && !state.detailPresented)
    state.select("project-two")
    #expect(state.selectedProject == .projectTwo && state.detailPresented)
    state.glass = false
    state.back()
    #expect(state.selectedProject == .projectTwo && !state.detailPresented && !state.glass)
    state.select("unknown")
    #expect(state.selectedProject == .projectTwo && !state.detailPresented)
    state.select("project-two")
    #expect(state.detailPresented)
    state.select("project-three")
    #expect(state.selectedProject == .projectThree && state.detailPresented)
}
