package dev.mobilefoundry.product

import androidx.compose.runtime.saveable.listSaver

enum class ProductProject(val id: String, val title: String) {
    ONE("project-one", "Project one"), TWO("project-two", "Project two"), THREE("project-three", "Project three");
}

/** App state outlives geometry/material branches; these are placeholder identities. */
data class ProductState(val selectedProject: ProductProject? = null, val detailPresented: Boolean = false, val glass: Boolean = true) {
    fun select(id: String): ProductState = ProductProject.entries.firstOrNull { it.id == id }
        ?.let { copy(selectedProject = it, detailPresented = true) } ?: this
    fun back(): ProductState = copy(detailPresented = false)

    companion object {
        const val shellTitle = "Workspace"
        val Saver = listSaver<ProductState, Any>(
            save = { listOf(it.selectedProject?.id.orEmpty(), it.detailPresented, it.glass) },
            restore = { values ->
                val project = ProductProject.entries.firstOrNull { it.id == values[0] }
                ProductState(project, project != null && values[1] as Boolean, values[2] as Boolean)
            })
    }
}
