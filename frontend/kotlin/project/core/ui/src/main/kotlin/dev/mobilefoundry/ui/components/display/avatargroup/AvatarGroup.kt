package dev.mobilefoundry.ui.components.display.avatargroup

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.avatar.Avatar

/** Passive artwork read as one supplied summary. The caller owns stable keys and localized overflow copy. */
@Composable
fun <T> AvatarGroup(items: List<T>, label: String, overflowText: String, itemKey: (T) -> Any,
    modifier: Modifier = Modifier, maximumVisible: Int = 4, size: Dp = 40.dp, avatar: @Composable (T) -> Unit) {
    require(maximumVisible > 0 && size.value.isFinite() && size > 0.dp)
    Row(modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(-size / 5)) {
        items.take(maximumVisible).forEach { item -> key(itemKey(item)) { Box(Modifier.size(size)) { avatar(item) } } }
        if (items.size > maximumVisible) Avatar(overflowText, overflowText, size = size)
    }
}
