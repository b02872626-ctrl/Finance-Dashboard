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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.viewmodel.SettingsViewModel

// =============================================================================
// Profile sub-pages — pixel-faithful reproduction (dark theme) of:
//   design/profile/Profile_Notifications_light.png       → NotificationPreferencesScreen
//   design/profile/Profile_accounts security_light.png   → AccountSecurityScreen
//     (now "Privacy & Data"; the mock-up's 2FA/biometrics/sessions were
//      never implemented and were removed before v1)
//   design/profile/Profile_edit Account_light.png        → EditAccountScreen
//
// All three share the compact header (small avatar + name + email) — see
// CompactProfileHeader below.
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)
private val InnerDark  = Color(0xFF15110F)
private val Headline   = AppBlue
private val Coral      = CoralPrimary
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)
private val DividerCol = Color(0x227FE3CB)

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------
@Composable
private fun CompactProfileHeader(name: String, email: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(CardBg)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5EFE3))
                    .border(1.5.dp, Color(0xFF231E1A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_profile),
                    contentDescription = null,
                    tint = Color(0xFF231E1A),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = name, color = Headline, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(text = email, color = BodyMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 90.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
        )
    }
}

@Composable
private fun BackTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back_dark),
            contentDescription = "Back",
            tint = Headline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Headline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (trailing != null) {
            Text(text = trailing, color = FooterGrey, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        }
    }
}

@Composable
private fun ThinDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
}

@Composable
private fun ToggleRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InnerDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = Headline, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = BodyMuted, fontSize = 12.sp)
        }
        CoralToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TextToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = BodyMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        CoralToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CoralToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackColor = if (checked) Coral else Color(0xFF3A3633)
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ---------------------------------------------------------------------------
// Notification Preferences
//
// One real switch. It gates every notification the app posts: the "review
// today's transactions" reminder after an SMS scan and the sync status
// notices (SmsIngestWorker / SmsIngestService / SupabaseSyncWorker).
// ---------------------------------------------------------------------------
@Composable
fun NotificationPreferencesScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val userName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")
    val enabled by vm.notificationsEnabled.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(PageBg).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column {
                    CompactProfileHeader(name = userName.ifBlank { "Your Account" }, email = email.ifBlank { "—" })
                    Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) { BackTile(onBack) }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Notifications")
                    ThinDivider()
                    TextToggleRow(
                        title = "Transaction alerts",
                        subtitle = "A reminder to review new transactions, and updates when " +
                            "syncing is waiting for an internet connection.",
                        checked = enabled,
                        onCheckedChange = { vm.setNotificationsEnabled(it) }
                    )
                    ThinDivider()
                    Text(
                        text = "You can also turn Kash notifications off in your phone's settings.",
                        color = FooterGrey,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Privacy & Data (was the "Accounts Security" mock-up)
//
// Every sentence here must stay true to what the code does — if sync, the
// admin dashboard (Web/admin, supabase/add_admin_sessions.sql) or local
// storage changes, update this copy and the privacy policy together.
// ---------------------------------------------------------------------------
@Composable
fun AccountSecurityScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val userName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(PageBg).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column {
                    CompactProfileHeader(name = userName.ifBlank { "Your Account" }, email = email.ifBlank { "—" })
                    Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) { BackTile(onBack) }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "How your data is handled")
                    ThinDivider()
                    PrivacyFact(
                        title = "Only bank messages",
                        body = "Kash reads SMS from banks and mobile-money services it " +
                            "recognises. Other messages are skipped — never stored or uploaded."
                    )
                    ThinDivider()
                    PrivacyFact(
                        title = "Synced to your account",
                        body = "Your transactions and the original bank SMS are synced to your " +
                            "Kash account over an encrypted connection, so they show up " +
                            "on the web dashboard and survive a phone change."
                    )
                    ThinDivider()
                    PrivacyFact(
                        title = "Who can see it",
                        body = "You, when signed in. The Kash developer can also access " +
                            "synced data to fix import problems and answer support requests. " +
                            "Your data is never sold or shared."
                    )
                    ThinDivider()
                    PrivacyFact(
                        title = "On this phone",
                        body = "A copy stays on this phone until you delete your account or " +
                            "uninstall the app. Logging out does not erase it."
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CardBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(context.getString(R.string.privacy_policy_url))
                                            )
                                        )
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Read the privacy policy", color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DeleteAccountLink()
                }
            }
        }
    }
}

@Composable
private fun PrivacyFact(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = title, color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(text = body, color = BodyMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

// ---------------------------------------------------------------------------
// Edit Account — name is the only editable profile field. Email comes from
// the sign-in provider and is shown read-only.
// ---------------------------------------------------------------------------
@Composable
fun EditAccountScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val initialName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")

    var fullName by rememberSaveable { mutableStateOf(initialName) }

    Box(modifier = Modifier.fillMaxSize().background(PageBg).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackTile(onBack)
                }
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
                }
            }

            item {
                LabeledField(label = "NAME", value = fullName, onValueChange = { fullName = it }, placeholder = "Your name")
            }
            item {
                Column {
                    Text(text = "EMAIL ADDRESS", color = Headline, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = email.ifBlank { "—" },
                        color = BodyMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )
                }
            }

            item {
                val canSave = fullName.isNotBlank() && fullName.trim() != initialName
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (canSave) Color(0xFFEAE3D2) else Color(0xFF3A342E))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = canSave,
                            onClick = {
                                vm.updateUserName(fullName.trim())
                                onBack()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save Changes",
                        color = if (canSave) Color(0xFF231E1A) else FooterGrey,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    multiline: Boolean = false
) {
    Column {
        Text(text = label, color = Headline, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (multiline) 22.dp else 28.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(if (multiline) 22.dp else 28.dp))
                .padding(horizontal = 18.dp, vertical = if (multiline) 14.dp else 14.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = !multiline,
                textStyle = TextStyle(color = Headline, fontSize = 14.sp),
                cursorBrush = SolidColor(Coral),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(text = placeholder, color = FooterGrey, fontSize = 14.sp)
                    }
                    inner()
                }
            )
        }
    }
}
