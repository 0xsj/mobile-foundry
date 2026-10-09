package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.emptystate.EmptyState
import dev.mobilefoundry.ui.components.feedback.alert.InlineAlert
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.feedback.progress.ProgressIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.slider.ValueSlider
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.patterns.nowplayingcard.NowPlayingCard
import dev.mobilefoundry.ui.components.patterns.playbackcontrols.PlaybackControls
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.patterns.selectioncard.SelectionCard
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale

internal enum class PlaybackScenario(val label: String) { READY("Ready"), BUFFERING("Buffering"), FAILED("Failed") }
internal enum class PlaybackSpeed(val label: String, val multiplier: Double) { SLOW("0.75×", .75), NORMAL("1×", 1.0), FAST("1.5×", 1.5), DOUBLE("2×", 2.0) }
internal data class PreviewTrack(val id: String, val title: String, val creator: String, val duration: Double, val available: Boolean = true) {
    companion object {
        val all = listOf(PreviewTrack("coast", "Coastline study", "Mira Chen", 92.0),
            PreviewTrack("orbit", "Orbit session", "Studio sketches", 146.0),
            PreviewTrack("night", "Night walk", "Field recordings", 75.0, available = false))
        fun find(id: String) = all.firstOrNull { it.id == id }
    }
}
/** Admitted local timeline values. No audio engine, automatic clock or OS media-session effects. */
internal data class PlaybackValues(val selectedID: String = "coast", val positions: Map<String, Double> = emptyMap(),
    val favorites: List<String> = emptyList(), val isPlaying: Boolean = false, val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val repeatTrack: Boolean = false, val scenario: PlaybackScenario = PlaybackScenario.READY, val enabled: Boolean = true,
    val empty: Boolean = false, val trackChanges: Int = 0, val seeks: Int = 0, val advances: Int = 0) {
    val current get() = if (empty) null else PreviewTrack.find(selectedID)?.takeIf { it.available }
    val position get() = positions[selectedID] ?: 0.0
    val canSelect get() = enabled && !empty && scenario == PlaybackScenario.READY
    val canInteract get() = canSelect && current != null
    val canAdvance get() = canInteract && isPlaying
    val stateLabel get() = when (scenario) { PlaybackScenario.READY -> if (isPlaying) "Playing" else "Paused"; PlaybackScenario.BUFFERING -> "Buffering"; PlaybackScenario.FAILED -> "Unavailable" }
    val timeLabel get() = "${time(position)} of ${time(current?.duration ?: 0.0)}"
    private val available get() = PreviewTrack.all.filter { it.available }
    fun neighbor(step: Int): PreviewTrack? {
        val index = available.indexOfFirst { it.id == selectedID }
        return if (canInteract && (step == -1 || step == 1) && index >= 0) available.getOrNull(index + step) else null
    }
    fun select(id: String) = if (canSelect && PreviewTrack.find(id)?.available == true && id != selectedID)
        copy(selectedID = id, isPlaying = false, trackChanges = trackChanges + 1) else this
    fun move(step: Int) = neighbor(step)?.let { select(it.id) } ?: this
    fun toggle(): PlaybackValues {
        val track = current ?: return this
        if (!canInteract) return this
        return copy(isPlaying = !isPlaying, positions = if (!isPlaying && position >= track.duration) positions + (selectedID to 0.0) else positions)
    }
    fun seek(value: Double): PlaybackValues {
        val track = current ?: return this
        if (!canInteract || !value.isFinite()) return this
        val admitted = value.coerceIn(0.0, track.duration)
        return if (admitted == position) this else copy(positions = positions + (selectedID to admitted), seeks = seeks + 1,
            isPlaying = if (admitted == track.duration) false else isPlaying)
    }
    fun advance(seconds: Double = 10.0): PlaybackValues {
        val track = current ?: return this
        if (!canAdvance || !seconds.isFinite() || seconds <= 0) return this
        val target = position + seconds * speed.multiplier
        if (!target.isFinite()) return this
        return copy(positions = positions + (selectedID to if (target >= track.duration) (if (repeatTrack) target % track.duration else track.duration) else target),
            isPlaying = if (target >= track.duration && !repeatTrack) false else isPlaying, advances = advances + 1)
    }
    fun chooseSpeed(value: PlaybackSpeed) = if (canInteract) copy(speed = value) else this
    fun setRepeat(value: Boolean) = if (canInteract) copy(repeatTrack = value) else this
    fun chooseScenario(value: PlaybackScenario) = if (enabled && !empty) copy(scenario = value, isPlaying = if (value != PlaybackScenario.READY) false else isPlaying) else this
    fun retry() = if (enabled && !empty && scenario == PlaybackScenario.FAILED) copy(scenario = PlaybackScenario.READY) else this
    fun setEnabled(value: Boolean) = copy(enabled = value, isPlaying = if (!value) false else isPlaying)
    fun setEmpty(value: Boolean) = if (enabled) copy(empty = value, isPlaying = if (value) false else isPlaying) else this
    fun favorite(id: String) = if (enabled && !empty && PreviewTrack.find(id)?.available == true)
        copy(favorites = if (id in favorites) favorites - id else favorites + id) else this
    fun resetPlayer() = if (enabled) copy(selectedID = "coast", positions = emptyMap(), isPlaying = false, speed = PlaybackSpeed.NORMAL,
        repeatTrack = false, scenario = PlaybackScenario.READY, empty = false) else this
    companion object { fun time(value: Double): String { val whole = value.toInt(); return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60) } }
}
@Composable
internal fun PlaybackExamples(values: PlaybackValues, onChange: (PlaybackValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Playback and media state", subtitle = "Independent transport controls, timeline and media identity.")
        NavLink("Open playback preview", onPreview, subtitle = "Try a local timeline, queue and recovery states")
    }
    PlaybackContent(values, onChange)
}
@Composable
internal fun PlaybackPreviewScreen(values: PlaybackValues, onChange: (PlaybackValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack) { Text("Back to components") }
        Text("Playback preview", Modifier.padding(horizontal = 20.dp), style = FoundryTheme.tokens.typography.heading)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("playback-scroll").padding(20.dp)) { PlaybackContent(values, onChange) }
    }
}
@Composable
internal fun PlaybackContent(values: PlaybackValues, onChange: (PlaybackValues) -> Unit) {
    val t = FoundryTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable playback actions", values.enabled, { onChange(values.setEnabled(it)) })
            ToggleField("Show empty queue", values.empty, { onChange(values.setEmpty(it)) }, enabled = values.enabled)
            SelectField("Playback scenario", PlaybackScenario.entries, values.scenario, { onChange(values.chooseScenario(it)) }, enabled = values.enabled && !values.empty, label = { it.label })
            Text("Local timeline preview. Advance time manually; no audio is played.", style = t.typography.caption)
            ActionButton({ onChange(values.resetPlayer()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Reset player") }
            Text("Track changes: ${values.trackChanges} · Seeks: ${values.seeks} · Time steps: ${values.advances}", style = t.typography.caption)
        }
        val track = values.current
        if (track != null) {
            NowPlayingCard(track.title, "${track.title}, ${track.creator}", detail = track.creator, artwork = {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(t.colors.accent.color, t.colors.accentTint.color)))
                    drawCircle(t.colors.ink.color, radius = size.minDimension * .27f, center = center, style = Stroke(2.dp.toPx()))
                    drawCircle(t.colors.ink.color.copy(alpha = .25f), radius = size.minDimension * .12f, center = Offset(size.width * .63f, size.height * .38f))
                }
            }, timeline = {
                ValueSlider("Playback position", values.position.toFloat(), { onChange(values.seek(it.toDouble())) }, values.timeLabel,
                    range = 0f..track.duration.toFloat(), enabled = values.canInteract)
                if (values.scenario == PlaybackScenario.BUFFERING) ProgressIndicator("Buffering preview")
                if (values.scenario == PlaybackScenario.FAILED) InlineAlert("Playback unavailable", "Retry restores this local timeline.", tone = MessageTone.WARNING) {
                    ActionButton({ onChange(values.retry()) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled) { Text("Retry playback") }
                }
            }, controls = {
                PlaybackControls(values.isPlaying, "Previous track", if (values.isPlaying) "Pause preview" else "Play preview", "Next track", values.stateLabel,
                    onPrevious = { onChange(values.move(-1)) }, onToggle = { onChange(values.toggle()) }, onNext = { onChange(values.move(1)) },
                    previousEnabled = values.neighbor(-1) != null, toggleEnabled = values.canInteract, nextEnabled = values.neighbor(1) != null)
                Text(values.stateLabel, style = t.typography.caption)
            }, actions = {
                SelectField("Playback speed", PlaybackSpeed.entries, values.speed, { onChange(values.chooseSpeed(it)) }, enabled = values.canInteract, label = { it.label })
                ToggleField("Repeat current track", values.repeatTrack, { onChange(values.setRepeat(it)) }, enabled = values.canInteract)
                ActionButton({ onChange(values.advance()) }, variant = ButtonVariant.SECONDARY, enabled = values.canAdvance) { Text("Advance 10 seconds") }
                IconAction("${if (track.id in values.favorites) "Unfavorite" else "Favorite"} ${track.title}", { onChange(values.favorite(track.id)) }, enabled = values.enabled && !values.empty) {
                    Text(if (track.id in values.favorites) "♥" else "♡")
                }
            })
        } else Card { EmptyState("Nothing queued", "Restore the queue or reset the player.") }
        if (!values.empty) Card {
            SectionHeader("Preview queue", subtitle = "Each available track remembers its own position.")
            PreviewTrack.all.forEach { item -> key(item.id) {
                SelectionCard(values.selectedID == item.id, { onChange(values.select(item.id)) }, enabled = values.canSelect && item.available) {
                    Text(item.title, style = t.typography.label)
                    Text("${item.creator} · ${PlaybackValues.time(item.duration)}${if (item.available) "" else " · Unavailable"}", style = t.typography.caption)
                }
            } }
        }
    }
}
