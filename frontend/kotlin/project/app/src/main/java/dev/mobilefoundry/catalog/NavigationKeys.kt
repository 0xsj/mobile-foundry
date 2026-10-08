package dev.mobilefoundry.catalog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object HealthCatalog : NavKey

@Serializable data object NotesCatalog : NavKey

@Serializable data object QueryCatalog : NavKey

@Serializable data object TokensCatalog : NavKey

@Serializable data object FormsCatalog : NavKey

@Serializable data object GPUEffectsCatalog : NavKey
@Serializable data object ImageStudioCatalog : NavKey
@Serializable data object ProductStudioCatalog : NavKey

@Serializable data object CompositorStudioCatalog : NavKey
