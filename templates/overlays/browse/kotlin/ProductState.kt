package dev.mobilefoundry.product

import androidx.compose.runtime.saveable.listSaver

enum class ProductCollection(val id: String, val title: String) {
    ONE("collection-one", "Collection one"), TWO("collection-two", "Collection two"), THREE("collection-three", "Collection three");
}

/** UI-only search and navigation, separate from durable records or saved collections. */
data class ProductState(val selectedCollection: ProductCollection? = null, val filter: String = "", val glass: Boolean = true) {
    val visibleCollections: List<ProductCollection> get() {
        val query = filter.trim()
        return ProductCollection.entries.filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
    }
    fun select(id: String): ProductState = ProductCollection.entries.firstOrNull { it.id == id }
        ?.let { copy(selectedCollection = it) } ?: this
    fun back(): ProductState = copy(selectedCollection = null)

    companion object {
        const val shellTitle = "Browse"
        val Saver = listSaver<ProductState, Any>(
            save = { listOf(it.selectedCollection?.id.orEmpty(), it.filter, it.glass) },
            restore = { values -> ProductState(ProductCollection.entries.firstOrNull { it.id == values[0] }, values[1] as String, values[2] as Boolean) })
    }
}
