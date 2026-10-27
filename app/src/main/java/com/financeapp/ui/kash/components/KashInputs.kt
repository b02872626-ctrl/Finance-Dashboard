package com.financeapp.ui.kash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType

/**
 * Kash.SearchField: filters as you type, no search button. On slate in the
 * History hero; light on the Account detail sheet.
 */
@Composable
fun KashSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSlate: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bg = if (onSlate) KashColors.GhostFill else KashColors.SurfaceRaised
    val line = when {
        focused && onSlate -> KashColors.FocusOnSlate
        focused -> KashColors.Focus
        onSlate -> KashColors.GhostLine
        else -> KashColors.Line
    }
    val muted = if (onSlate) KashColors.OnSlateMuted else KashColors.InkMuted
    val text = if (onSlate) KashColors.OnSlate else KashColors.Ink
    Row(
        modifier
            .fillMaxWidth()
            .height(KashSize.SearchH)
            .clip(KashShapes.Control)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, line, KashShapes.Control)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KashIconView(KashIcon.Search, size = 17.dp, tint = muted, strokeWidth = 2.2f)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = KashType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = muted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = KashType.Body.copy(color = text, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                cursorBrush = SolidColor(if (onSlate) KashColors.Sand else KashColors.Slate),
                interactionSource = source,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Kash.TextField: label always visible above, a placeholder that shows the
 * expected shape, and a [hint] line. [error] replaces the hint in money-out
 * and turns the border red.
 */
@Composable
fun KashTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val locked = !enabled || readOnly
    val line = when {
        error != null -> KashColors.MoneyOut
        focused -> KashColors.Focus
        else -> KashColors.Line
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = KashType.Label, color = KashColors.InkMuted)
        Box(
            Modifier
                .fillMaxWidth()
                .height(KashSize.FieldH)
                .clip(KashShapes.Field)
                .background(if (locked) KashColors.Well else KashColors.SurfaceRaised)
                .border(if (focused && error == null) 2.dp else 1.dp, line, KashShapes.Field)
                .padding(horizontal = 14.dp)
                .then(if (error != null) Modifier.semantics { this.error(error) } else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = KashType.BodyL, color = KashColors.InkMuted, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = true,
                textStyle = KashType.BodyL.copy(color = if (locked) KashColors.InkMuted else KashColors.Ink),
                cursorBrush = SolidColor(KashColors.Slate),
                interactionSource = source,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val line2 = error ?: hint
        if (line2 != null) {
            Text(line2, style = KashType.Caption, color = if (error != null) KashColors.MoneyOut else KashColors.InkMuted)
        }
    }
}

@Preview(name = "Inputs", widthDp = 360)
@Composable
private fun InputsPreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var q by remember { mutableStateOf("") }
        KashSearchField(q, { q = it }, "Search this account")
        Box(Modifier.background(KashColors.Slate).padding(12.dp)) {
            KashSearchField("Yod Abyssinia", {}, "Search name, category, reference", onSlate = true)
        }
        KashTextField("", {}, "Email", placeholder = "you@example.com")
        KashTextField("secret123", {}, "Password", password = true, error = "Wrong email or password.")
        KashTextField("meron@example.com", {}, "Email", readOnly = true, hint = "Your sign-in email can't be changed here.")
    }
}
