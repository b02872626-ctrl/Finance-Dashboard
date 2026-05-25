package com.financeapp.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Design palette — dark mode (extracted from design/onboarding/reference/*.png).
// Existing token names are preserved so legacy screens compile unchanged; their
// values now point at dark-mode equivalents.
// =============================================================================

// --- Surfaces ---
val AppBackground       = Color(0xFF0E1614)   // page background — deep near-black with green tint
val AppSurface          = Color(0xFF1F2522)   // card surface — warm dark
val AppSurfaceSecondary = Color(0xFF181E1B)   // recessed surface
val PureWhite           = Color(0xFFFFFFFF)

// --- Text ---
val CharcoalText = Color(0xFFEDECE7)   // primary text (off-white)
val SlateGray    = Color(0xFFA6A39C)   // secondary text
val MutedGray    = Color(0xFF6F6C66)   // tertiary / muted text

// --- Brand accent (teal / mint) ---
// In the references this is the headline & link colour, e.g. "You're all set!".
val AppBlue        = Color(0xFF7FE3CB)
val AppBlueDark    = Color(0xFF4FB99F)
val AppBlueNavy    = Color(0xFF2C7A66)
val AppBlueLight   = Color(0xFFBDF1E2)
val AppBlueSurface = Color(0x1F7FE3CB)   // 12% teal tint

val MintAccent = AppBlue
val MintLine   = Color(0x337FE3CB)

// --- Primary action / data emphasis (coral) ---
// CTAs ("Get started", "Allow Access") and key metrics ("1,375 Transactions").
val CoralPrimary = Color(0xFFFB5D53)
val CoralStrong  = Color(0xFFE34A41)
val CoralSurface = Color(0x29FB5D53)   // ~16% coral tint for chips / toggles

val RedExpense = CoralPrimary
val RedBg      = Color(0x14FB5D53)

// --- Surface accents / dividers ---
val AccentFill       = Color(0x1F7FE3CB)
val AccentFillStrong = Color(0x337FE3CB)
val AccentBorder     = Color(0x447FE3CB)
val BorderLight      = Color(0x1FFFFFFF)
val BorderStrong     = Color(0x33FFFFFF)
val DividerColor     = Color(0x1FFFFFFF)

// --- Semantic transaction colours ---
val GreenIncome    = Color(0xFF4CD495)
val GreenBg        = Color(0x1F4CD495)
val AmberTransfer  = Color(0xFFE0B355)
val AmberBg        = Color(0x1FE0B355)
val PurplePayment  = Color(0xFFB59BE0)
val PurpleBg       = Color(0x1FB59BE0)

// --- Legacy aliases (kept so older code keeps compiling) ---
val SoftPearl   = AppBackground
val SoftOnyx    = CharcoalText
val MutedTaupe  = SlateGray
val MutedSlate  = SlateGray

val Indigo500   = AppBlue
val Indigo300   = AppBlueLight
val Indigo100   = AppBlueSurface
val BlueSky     = AppBlueSurface
val CyanBlue    = AppBlue

val Cream100    = AppBackground
val Cream50     = AppSurface
val PaperBeige  = AppSurfaceSecondary

val TextPrimary   = CharcoalText
val TextSecondary = SlateGray
val TextMuted     = MutedGray
