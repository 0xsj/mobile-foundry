package dev.mobilefoundry.product

/** Product branding is loaded from app_name; library/source names stay stable. */
data class ProductConfiguration(val displayName: String, val defaultGlass: Boolean = true)
