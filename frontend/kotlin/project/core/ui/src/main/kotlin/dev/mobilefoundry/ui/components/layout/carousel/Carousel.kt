package dev.mobilefoundry.ui.components.layout.carousel

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Native horizontal paging. The caller owns PagerState, stable keys and bounded layout.
 * No auto-advance, data loading, image admission or route policy. */
@Composable
fun Carousel(state: PagerState, modifier: Modifier = Modifier, key: ((Int) -> Any)? = null,
    userScrollEnabled: Boolean = true, content: @Composable PagerScope.(Int) -> Unit) {
    require(state.pageCount > 0)
    HorizontalPager(state = state, modifier = modifier, key = key, userScrollEnabled = userScrollEnabled, pageContent = content)
}
