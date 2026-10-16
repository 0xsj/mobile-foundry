package dev.mobilefoundry.product

/** Product inputs stay outside reusable UI. Display branding comes from app_name. */
data class ProductConfiguration(val displayName: String, val defaultGlass: Boolean = true)

enum class ProductTab(val id: String, val title: String, val icon: Int) {
    TODAY("today", "Today", R.drawable.ic_tab_home),
    LIBRARY("library", "Library", R.drawable.ic_tab_library),
    ACTIVITY("activity", "Activity", R.drawable.ic_tab_activity),
    ACCOUNT("account", "Account", R.drawable.ic_tab_account),
}
