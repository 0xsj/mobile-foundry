@testable import ProductApp
import FoundryKernel
import Testing

@MainActor @Test func generatedProductAcceptsAnInjectedServiceQueryAndMutation() {
    let service = MemoryCounterService(), model = CounterModel(service: service)
    #expect(model.query.value == 0)
    model.increment(); model.increment()
    #expect(model.mutation.value == 2 && model.query.value == 2)
    let otherReader = CounterModel(service: service)
    #expect(otherReader.query.value == 2)
    #expect(CounterModel(service: MemoryCounterService()).query.value == 0)
}
