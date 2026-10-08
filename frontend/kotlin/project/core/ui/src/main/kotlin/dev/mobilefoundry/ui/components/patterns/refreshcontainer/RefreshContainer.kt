package dev.mobilefoundry.ui.components.patterns.refreshcontainer

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Native gesture adapter around bounded scrolling content (usually LazyColumn).
 * The caller owns the refreshing flag, task, concurrency, errors and cancellation policy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshContainer(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, content = content)
}
