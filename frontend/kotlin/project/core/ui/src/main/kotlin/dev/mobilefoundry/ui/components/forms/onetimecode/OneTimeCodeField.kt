package dev.mobilefoundry.ui.components.forms.onetimecode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Fixed-length ASCII digits. Only edits/pastes may contain the five specified separators. */
data class CodeFormat(val length: Int = 6) {
    init { require(length in 1..12) }
    fun admit(input: String): String? {
        val digits = StringBuilder()
        for (character in input) {
            when (character) {
                in '0'..'9' -> digits.append(character)
                ' ', '\t', '\n', '\r', '-' -> continue
                else -> return null
            }
            if (digits.length > length) return null
        }
        return digits.toString()
    }
    fun isValid(value: String) = value.length <= length && value.all { it in '0'..'9' }
    fun isComplete(value: String) = isValid(value) && value.length == length
}

/** One native editable field with an SMS-code autofill hint. No request, auto-submit, timer or persistence. */
@Composable
fun OneTimeCodeField(title: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    format: CodeFormat = CodeFormat(), help: String? = null, error: String? = null, enabled: Boolean = true,
    canSubmit: Boolean = true, onSubmit: () -> Unit = {}) {
    require(format.isValid(value))
    val t = FoundryTheme.tokens
    OutlinedTextField(value, { input -> if (enabled) format.admit(input)?.let { if (it != value) onValueChange(it) } },
        modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive).semantics {
            contentType = ContentType.SmsOtpCode
            if (error != null) error(error)
        }, label = { Text(title) }, singleLine = true, enabled = enabled, isError = error != null,
        textStyle = t.typography.code.copy(textDirection = TextDirection.Ltr),
        supportingText = if (error != null || help != null) { { Text(error ?: help.orEmpty()) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (enabled && canSubmit && format.isComplete(value)) onSubmit() }))
}
