package com.financeapp

import androidx.compose.ui.graphics.Color
import com.financeapp.ui.kash.KashPalette
import com.financeapp.ui.kash.KashPalettes
import com.financeapp.ui.kash.KashThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Both themes stay readable: WCAG contrast of every text pairing the
 * screens use, and the theme setting resolves as Settings → Appearance says.
 */
class KashPaletteTest {

    private fun channel(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun atLeast(ratio: Double, what: String, fg: Color, bg: Color) {
        val c = contrast(fg, bg)
        assertTrue("$what is ${"%.2f".format(c)}:1, needs $ratio:1", c >= ratio)
    }

    private fun check(name: String, p: KashPalette) {
        // Body text on every sheet surface.
        atLeast(7.0, "$name ink on surface", p.ink, p.surface)
        atLeast(7.0, "$name ink on raised card", p.ink, p.surfaceRaised)
        atLeast(7.0, "$name ink on well", p.ink, p.well)
        // Muted labels (15sp) on the sheet and inside cards and wells.
        atLeast(4.5, "$name muted on surface", p.inkMuted, p.surface)
        atLeast(4.5, "$name muted on raised card", p.inkMuted, p.surfaceRaised)
        atLeast(4.5, "$name muted on well", p.inkMuted, p.well)
        // Faint: placeholders and inactive icons only (3:1 is the floor for those).
        atLeast(3.0, "$name faint on surface", p.inkFaint, p.surface)
        // Amounts in their money colours.
        atLeast(4.5, "$name money in on surface", p.moneyIn, p.surface)
        atLeast(4.5, "$name money out on surface", p.moneyOut, p.surface)
        // Primary buttons and selected chips.
        atLeast(4.5, "$name text on accent", p.onAccent, p.accent)
        // The slate top.
        atLeast(4.5, "$name white on slate", Color.White, p.slate)
        atLeast(4.5, "$name muted on slate", Color(0xFFC6CDD8), p.slate)
    }

    @Test
    fun `light theme pairs pass`() = check("light", KashPalettes.Light)

    @Test
    fun `dark theme pairs pass`() = check("dark", KashPalettes.Dark)

    @Test
    fun `accent is slate in light and sand in dark, never sand on a light sheet`() {
        assertEquals(KashPalettes.Light.slate, KashPalettes.Light.accent)
        assertEquals(Color(0xFFE9DFC6), KashPalettes.Dark.accent)
        assertFalse(KashPalettes.Light.accent == Color(0xFFE9DFC6))
    }

    @Test
    fun `theme setting resolves as Appearance says`() {
        assertFalse(KashThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(KashThemeMode.DARK.isDark(systemDark = false))
        assertTrue(KashThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(KashThemeMode.SYSTEM.isDark(systemDark = false))
        assertEquals(KashThemeMode.DARK, KashThemeMode.fromStored("DARK"))
        assertEquals(KashThemeMode.SYSTEM, KashThemeMode.fromStored(null))
        assertEquals(KashThemeMode.SYSTEM, KashThemeMode.fromStored("anything"))
    }
}
