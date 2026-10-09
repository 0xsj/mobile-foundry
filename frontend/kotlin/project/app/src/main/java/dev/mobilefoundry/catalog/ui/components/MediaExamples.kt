package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.components.forms.rating.RatingField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.carousel.Carousel
import dev.mobilefoundry.ui.components.layout.container.ContentContainer
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.navigation.pageindicator.PageIndicator
import dev.mobilefoundry.ui.components.patterns.mediaoverlay.MediaOverlay
import dev.mobilefoundry.ui.components.patterns.mediatile.MediaTile
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.launch

internal data class MediaStudy(val id: String, val title: String, val subtitle: String, val colors: List<Color>) {
    companion object {
        val all = listOf(
            MediaStudy("orbit", "Orbit study", "Soft rings and a quiet blue palette.", listOf(Color(0xFF334AB8), Color(0xFF9970CC))),
            MediaStudy("field", "Field study", "Warm light and open space.", listOf(Color(0xFFBD4A40), Color(0xFFF5BA6E))),
            MediaStudy("arc", "Arc study", "A cool shape against deep ink.", listOf(Color(0xFF146370), Color(0xFF29335E))))
    }
}
internal data class MediaValues(val ratings: Map<String, Int> = emptyMap(), val favorites: List<String> = emptyList(),
    val enabled: Boolean = true, val used: Int = 0)

@Composable internal fun MediaExamples(values: MediaValues, onChange: (MediaValues) -> Unit, pager: PagerState, onPreview: () -> Unit) {
    ToggleField("Enable media controls", values.enabled, { onChange(values.copy(enabled = it)) })
    MediaBrowser(values, onChange, pager)
    SelectedMediaTile(values, onChange, pager.currentPage)
    NavLink("Open media preview", onPreview, subtitle = "A separate screen using the same selected study.")
}
@Composable private fun StudyArtwork(study: MediaStudy) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(study.colors))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.65f), radius = size.width * 0.30f,
                center = Offset(size.width * 0.60f, size.height * 0.50f - 20.dp.toPx()), style = Stroke(2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.18f), radius = size.width * 0.135f,
                center = Offset(size.width * 0.28f, size.height * 0.50f + 25.dp.toPx()))
        }
    }
}
@Composable private fun MediaBrowser(values: MediaValues, onChange: (MediaValues) -> Unit, pager: PagerState) {
    val t = FoundryTheme.tokens
    val scope = rememberCoroutineScope()
    val index = pager.currentPage
    val study = MediaStudy.all[index]
    fun move(step: Int) {
        val next = index + step
        if (!values.enabled || next !in MediaStudy.all.indices) return
        scope.launch { if (t.motion.reduced) pager.scrollToPage(next) else pager.animateScrollToPage(next) }
    }
    Card {
        SectionHeader("Study collection", "Swipe or use the page controls. Local preview only.")
        Carousel(pager, Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(t.shape.panel)).testTag("media-carousel"),
            key = { MediaStudy.all[it].id }, userScrollEnabled = values.enabled) { page ->
            val record = MediaStudy.all[page]
            MediaOverlay(Modifier.fillMaxSize(), artwork = { StudyArtwork(record) }, overlay = {
                Text(record.title, style = t.typography.heading)
                val favorite = record.id in values.favorites
                IconAction(if (favorite) "Remove favorite ${record.title}" else "Favorite ${record.title}",
                    { onChange(values.copy(favorites = if (favorite) values.favorites - record.id else values.favorites + record.id)) },
                    variant = ButtonVariant.SECONDARY, enabled = values.enabled) { Text(if (favorite) "♥" else "♡") }
            })
        }
        PageIndicator(MediaStudy.all.size, index, "Study ${index + 1} of ${MediaStudy.all.size}")
        Text("Selected: ${study.title}", style = t.typography.label)
        Row {
            IconAction("Previous study", { move(-1) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled && index > 0) { Text("‹") }
            IconAction("Next study", { move(1) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled && index < MediaStudy.all.lastIndex) { Text("›") }
        }
        RatingField("Rate ${study.title}", values.ratings[study.id] ?: 0, { rating ->
            onChange(values.copy(ratings = values.ratings + (study.id to rating)))
        }, "Rating: ${values.ratings[study.id] ?: 0} of 5", { "Rate ${study.title} $it of 5" }, enabled = values.enabled)
        ActionButton({ onChange(values.copy(ratings = values.ratings - study.id)) },
            variant = ButtonVariant.QUIET, enabled = values.enabled && (values.ratings[study.id] ?: 0) > 0) { Text("Clear study rating") }
    }
}
@Composable private fun SelectedMediaTile(values: MediaValues, onChange: (MediaValues) -> Unit, index: Int) {
    val study = MediaStudy.all[index]
    MediaTile(study.title, study.subtitle, ratio = 16f / 9f,
        artwork = { Box(Modifier.fillMaxSize().clearAndSetSemantics {}) { StudyArtwork(study) } }, actions = {
            Badge(if (study.id in values.favorites) "Favorite study" else "Not favorited")
            ActionButton({ onChange(values.copy(used = values.used + 1)) }, variant = ButtonVariant.SECONDARY,
                enabled = values.enabled) { Text("Use selected study") }
            Text("Used previews: ${values.used}")
        })
}
@Composable internal fun MediaPreviewScreen(values: MediaValues, onChange: (MediaValues) -> Unit, pager: PagerState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back to components") }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ContentContainer {
                MediaBrowser(values, onChange, pager)
                Spacer(Modifier.height(FoundryTheme.tokens.space.section))
                SelectedMediaTile(values, onChange, pager.currentPage)
            }
        }
    }
}
