package dev.mobilefoundry.ui.components.layout.aspectratio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/** Width-led media bounds. Children choose image fitting; overflowing artwork is clipped. */
@Composable
fun MediaFrame(modifier: Modifier = Modifier, ratio: Float = 16f / 9f, content: @Composable BoxScope.() -> Unit) {
    require(ratio.isFinite() && ratio > 0f)
    Box(modifier.fillMaxWidth().aspectRatio(ratio).clipToBounds(), content = content)
}
