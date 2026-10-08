package dev.mobilefoundry.catalog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.alert.InlineAlert
import dev.mobilefoundry.ui.components.forms.checkbox.Checkbox
import dev.mobilefoundry.ui.components.forms.checkbox.CheckState
import dev.mobilefoundry.ui.components.forms.datepicker.DateField
import dev.mobilefoundry.ui.components.forms.radiogroup.RadioGroup
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.slider.ValueSlider
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.patterns.settingssection.SettingsSection
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@Composable
internal fun ControlExamples(activityUpdates: Boolean, onActivityUpdates: (Boolean) -> Unit,
    photos: Boolean, onPhotos: (Boolean) -> Unit, notes: Boolean, onNotes: (Boolean) -> Unit,
    format: String, onFormat: (String) -> Unit, destination: String, onDestination: (String) -> Unit,
    intensity: Float, onIntensity: (Float) -> Unit, reviewDate: Long, onReviewDate: (Long) -> Unit) {
    val t = FoundryTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        SettingsSection("Workspace preferences", footer = "Committed values stay here when you change families or preview themes.") {
            ToggleField("Activity updates", activityUpdates, onActivityUpdates, help = "Keep track of changes to your workspace.")
            HorizontalDivider()
            ToggleField("Managed setting", false, {}, help = "An example of a disabled control.", enabled = false)
        }
        Card {
            Text("Include in export", style = t.typography.heading)
            val aggregate = if (photos && notes) CheckState.ON else if (!photos && !notes) CheckState.OFF else CheckState.MIXED
            Checkbox("Include everything", aggregate, when (aggregate) {
                CheckState.ON -> "All included"; CheckState.OFF -> "None included"; CheckState.MIXED -> "Some included"
            }, onToggle = { val next = aggregate != CheckState.ON; onPhotos(next); onNotes(next) })
            HorizontalDivider()
            Checkbox("Photos", if (photos) CheckState.ON else CheckState.OFF, if (photos) "Included" else "Excluded", { onPhotos(!photos) })
            Checkbox("Notes", if (notes) CheckState.ON else CheckState.OFF, if (notes) "Included" else "Excluded", { onNotes(!notes) })
        }
        Card {
            RadioGroup("Export format", listOf("Original", "Compact", "Print"), format, onFormat, label = { it })
            HorizontalDivider()
            SelectField("Destination", listOf("This device", "Shared workspace", "Archive"), destination, onDestination, label = { it })
        }
        Card {
            ValueSlider("Preview intensity", intensity, onIntensity, "${(intensity * 100).toInt()}%", steps = 3)
            HorizontalDivider()
            DateField("Review date", reviewDate, onReviewDate, "Use date", "Discard date", "Choose date")
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(reviewDate))
            Text("Review: $date", style = t.typography.caption)
        }
        InlineAlert("Your selection", "$format · $destination · ${(intensity * 100).toInt()}% intensity")
    }
}
