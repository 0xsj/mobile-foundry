package dev.mobilefoundry.ui.components.feedback.query

/** Caller-selected copy; the owning application can inject localized strings. */
data class QueryCopy(
    val idle: String,
    val loading: String,
    val refreshing: String,
    val empty: String,
    val refresh: String = "Refresh",
    val retry: String = "Retry",
    val cancel: String = "Cancel loading",
)

