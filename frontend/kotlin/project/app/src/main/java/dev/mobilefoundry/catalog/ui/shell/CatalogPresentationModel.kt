package dev.mobilefoundry.catalog.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Presentation lifetime survives configuration changes and ends on explicit dismissal. */
class CatalogPresentationModel : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    fun dismiss() { viewModelStore.clear() }
    override fun onCleared() { dismiss() }
}
