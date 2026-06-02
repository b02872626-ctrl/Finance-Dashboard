package com.financeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.viewmodel.SettingsViewModel

// =============================================================================
// Profile / Settings — pixel-faithful reproduction of
//   design/profile/Profile_dark.png
//
// Sections, top → bottom:
//   1. Header card  — Edit pencil + theme toggle + avatar + name + email
//   2. Preferences  — Accounts Security / Notifications / Connected Banks / Support
//   3. Log out pill
//   4. Bottom tab bar lives in AppNavigation
//
// Sub-screens (Account Security, Notifications, Edit Account) are in
// ProfileSubScreens.kt.
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)
// Tile colors taken straight from design/icon/Frame 177.svg and Lock/safe.svg:
//   rect fill #302A25, rect stroke #353535 (1.5px).
private val TileBg     = Color(0xFF302A25)
private val TileBorder = Color(0xFF353535)
private val Headline   = AppBlue
private val BodyMuted  = Color(0xFFB7B3AC)
private val DividerCol = Color(0x227FE3CB)
private val GlyphLight = Color(0xFFEBEBEB) // matches Frame 177 bell stroke
private val GlyphMint  = Color(0xFF85D5C6) // matches safe.svg lock stroke

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onEditAccount: () -> Unit = {},
    onAccountSecurity: () -> Unit = {},
    onNotificationPreferences: () -> Unit = {},
    onConnectedBanks: () -> Unit = onBack,
    onSupport: () -> Unit = {},
    onToggleTheme: () -> Unit = {}
) {
    val userName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")
    val connectedBankCount = vm.financialSenders.collectAsState().value.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            ProfileHeaderCard(
                name = userName.takeIf { it.isNotBlank() } ?: "Your Account",
                email = email.ifBlank { "Not signed in" },
                onEdit = onEditAccount,
                onToggleTheme = onToggleTheme
            )
        }

        item {
            Text(
                text = "Preferences",
                color = BodyMuted,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            PreferencesList(
                connectedBankCount = connectedBankCount,
                onAccountSecurity = onAccountSecurity,
                onNotifications = onNotificationPreferences,
                onConnectedBanks = onConnectedBanks,
                onSupport = onSupport
            )
        }

        item {
            CalendarTogglePill(
                current = vm.calendarSystem.collectAsState().value,
                onToggle = { vm.toggleCalendarSystem() }
            )
        }

        item { LogoutPill(onClick = { vm.signOut() }) }
    }
}

/**
 * Two-state toggle pill for Gregorian ↔ Ethiopian calendar display.
 * Affects date rendering across History, Daily Review, transaction detail.
 * Time stays 24h Gregorian regardless — Ethiopian dawn-anchored time is a
 * separate decision.
 */
@Composable
private fun CalendarTogglePill(current: String, onToggle: () -> Unit) {
    val isEthiopian = current == "ETHIOPIAN"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Calendar", color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (isEthiopian) "Ethiopian (Meskerem, Tikimt…)" else "Gregorian (Jan, Feb…)",
                color = BodyMuted, fontSize = 11.sp
            )
        }
        // Inline two-pill segmented control
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1A1612))
                .padding(3.dp)
        ) {
            SegmentedOption(text = "GR", active = !isEthiopian)
            SegmentedOption(text = "ET", active = isEthiopian)
        }
    }
}

@Composable
private fun SegmentedOption(text: String, active: Boolean) {
    Text(
        text = text,
        color = if (active) Color(0xFF231E1A) else BodyMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Headline else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

// ---------------------------------------------------------------------------
// Header card
// ---------------------------------------------------------------------------
@Composable
private fun ProfileHeaderCard(
    name: String,
    email: String,
    onEdit: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderToolTile(iconRes = R.drawable.ic_edit, contentDescription = "Edit profile", onClick = onEdit)
            HeaderToolTile(iconRes = R.drawable.ic_frame_177, contentDescription = "Toggle theme", onClick = onToggleTheme)
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5EFE3))
                .border(2.dp, Color(0xFF231E1A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_profile),
                contentDescription = null,
                tint = Color(0xFF231E1A),
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(text = name, color = Headline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(text = email, color = BodyMuted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeaderToolTile(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Headline,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Preferences list
// ---------------------------------------------------------------------------
@Composable
private fun PreferencesList(
    connectedBankCount: Int,
    onAccountSecurity: () -> Unit,
    onNotifications: () -> Unit,
    onConnectedBanks: () -> Unit,
    onSupport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ThinDivider()
        // Lock/safe.svg is a pre-styled tile (already includes background + border + mint stroke).
        PreferenceRow(
            tileVariant = TileVariant.PreStyled(R.drawable.ic_safe),
            title = "Accounts Security",
            subtitle = "2FA, Password & Biometrics",
            onClick = onAccountSecurity
        )
        ThinDivider()
        // Frame 177.svg is a pre-styled bell tile.
        PreferenceRow(
            tileVariant = TileVariant.PreStyled(R.drawable.ic_frame_177),
            title = "Notification Preferences",
            subtitle = "Alerts, Marketing & SMS",
            onClick = onNotifications
        )
        ThinDivider()
        PreferenceRow(
            tileVariant = TileVariant.Glyph(R.drawable.ic_banks, tint = GlyphMint),
            title = "Connected Banks",
            subtitle = "$connectedBankCount External Account${if (connectedBankCount == 1) "" else "s"} Linked",
            onClick = onConnectedBanks
        )
        ThinDivider()
        PreferenceRow(
            tileVariant = TileVariant.Glyph(R.drawable.ic_support, tint = GlyphMint),
            title = "Support",
            subtitle = "Help Center & Direct Chat",
            onClick = onSupport
        )
        ThinDivider()
    }
}

private sealed class TileVariant {
    /** Pre-rendered tile (background + border + glyph baked into the SVG). */
    data class PreStyled(val res: Int) : TileVariant()
    /** Glyph drawable that should be tinted and centred inside a styled tile. */
    data class Glyph(val res: Int, val tint: Color) : TileVariant()
}

@Composable
private fun PreferenceRow(
    tileVariant: TileVariant,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (tileVariant) {
            is TileVariant.PreStyled -> {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = tileVariant.res),
                    contentDescription = null,
                    modifier = Modifier.size(51.dp)
                )
            }
            is TileVariant.Glyph -> {
                Box(
                    modifier = Modifier
                        .size(51.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(TileBg)
                        .border(1.5.dp, TileBorder, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = tileVariant.res),
                        contentDescription = null,
                        tint = tileVariant.tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = BodyMuted, fontSize = 12.sp)
        }
        Text(text = "›", color = BodyMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThinDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
}

// ---------------------------------------------------------------------------
// Log out pill
// ---------------------------------------------------------------------------
@Composable
private fun LogoutPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_log_out),
                contentDescription = null,
                tint = Headline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "Log out", color = Headline, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
