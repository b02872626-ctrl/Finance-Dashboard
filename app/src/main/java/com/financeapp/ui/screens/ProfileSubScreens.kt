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
// ---------------------------------------------------------------------------
@Composable
fun NotificationPreferencesScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val userName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")

    var push by rememberSaveable { mutableStateOf(true) }
    var emailToggle by rememberSaveable { mutableStateOf(true) }
    var payments by rememberSaveable { mutableStateOf(true) }
    var deposits by rememberSaveable { mutableStateOf(true) }
    var newSignIns by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(PageBg).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column {
                    CompactProfileHeader(name = userName.ifBlank { "Your Account" }, email = email.ifBlank { "—" })
                    Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) {
                        BackTile(onBack)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Communications")
                    ThinDivider()
                    ToggleRow(
                        iconRes = R.drawable.ic_notification,
                        title = "Push",
                        subtitle = "Real-time alerts",
                        checked = push,
                        onCheckedChange = { push = it }
                    )
                    ToggleRow(
                        iconRes = R.drawable.ic_mail,
                        title = "Email",
                        subtitle = "In-depth summaries",
                        checked = emailToggle,
                        onCheckedChange = { emailToggle = it }
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Transactional Activity")
                    ThinDivider()
                    TextToggleRow(
                        title = "Payments & Transfers",
                        subtitle = "Notify me for every outgoing payment over ETB 1.00.",
                        checked = payments,
                        onCheckedChange = { payments = it }
                    )
                    ThinDivider()
                    TextToggleRow(
                        title = "Direct Deposits",
                        subtitle = "Confirm when payroll or external transfers arrive.",
                        checked = deposits,
                        onCheckedChange = { deposits = it }
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Security & Integrity")
                    ThinDivider()
                    TextToggleRow(
                        title = "New Sign-ins",
                        subtitle = "Alert me when my account is accessed from a new device.",
                        checked = newSignIns,
                        onCheckedChange = { newSignIns = it }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Account Security
// ---------------------------------------------------------------------------
@Composable
fun AccountSecurityScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val userName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")

    var authApp by rememberSaveable { mutableStateOf(true) }
    var biometric by rememberSaveable { mutableStateOf(true) }
    var smsVerify by rememberSaveable { mutableStateOf(false) }
    val activeCount = listOf(authApp, biometric, smsVerify).count { it }

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
                    SectionHeader(title = "Authentication", trailing = "$activeCount/3 STEPS ACTIVE")
                    ThinDivider()
                    ToggleRow(
                        iconRes = R.drawable.ic_account_security,
                        title = "Authenticator App",
                        subtitle = "Google Authenticator",
                        checked = authApp,
                        onCheckedChange = { authApp = it }
                    )
                    ThinDivider()
                    ToggleRow(
                        iconRes = R.drawable.ic_finger_print,
                        title = "Biometric Access",
                        subtitle = "Face ID & Touch ID",
                        checked = biometric,
                        onCheckedChange = { biometric = it }
                    )
                    ThinDivider()
                    ToggleRow(
                        iconRes = R.drawable.ic_sms,
                        title = "SMS Verification",
                        subtitle = if (smsVerify) "Configured" else "Not configured",
                        checked = smsVerify,
                        onCheckedChange = { smsVerify = it }
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Active Sessions")
                    ThinDivider()
                    DeviceRow(
                        iconRes = R.drawable.ic_phone_device,
                        title = "Surface laptop 4 - Wind...",
                        subtitle = "Finance Lore - A.A Eth.",
                        isThisDevice = false
                    )
                    ThinDivider()
                    DeviceRow(
                        iconRes = R.drawable.ic_pc_device,
                        title = "Samsung S22",
                        subtitle = "Finance Lore - A.A Eth.",
                        isThisDevice = true
                    )
                }
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrivacyVaultCard()
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(iconRes: Int, title: String, subtitle: String, isThisDevice: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(InnerDark),
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
        if (isThisDevice) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(InnerDark)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = "THIS DEVICE", color = BodyMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            }
        }
    }
}

@Composable
private fun PrivacyVaultCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield),
                contentDescription = null,
                tint = Headline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "Privacy Vault", color = Headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your financial data is protected by AES-256 hardware-level encryption. We never share your biometric data with third-party providers.",
            color = BodyMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Edit Account
// ---------------------------------------------------------------------------
@Composable
fun EditAccountScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onDeactivate: () -> Unit = {}
) {
    val initialName by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState(initial = "")

    var fullName by rememberSaveable { mutableStateOf(initialName) }
    var emailField by rememberSaveable { mutableStateOf(email) }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var twoFactor by rememberSaveable { mutableStateOf(true) }

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
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF5EFE3))
                            .border(2.dp, Color(0xFF231E1A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_profile),
                            contentDescription = null,
                            tint = Color(0xFF231E1A),
                            modifier = Modifier.size(96.dp)
                        )
                        // Pencil overlay bottom-right
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF5EFE3))
                                .border(1.5.dp, Color(0xFF231E1A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = "Edit photo",
                                tint = Color(0xFF231E1A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            item {
                LabeledField(label = "FULL NAME", value = fullName, onValueChange = { fullName = it })
            }
            item {
                LabeledField(label = "EMAIL ADDRESS", value = emailField, onValueChange = { emailField = it })
            }
            item {
                LabeledField(label = "PHONE NUMBER", value = phone, onValueChange = { phone = it }, placeholder = "+251 ...")
            }
            item {
                LabeledField(label = "ADDRESS", value = address, onValueChange = { address = it }, placeholder = "Street, City", multiline = true)
            }

            item {
                Text(text = "Security Preferences", color = Headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBg)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Two-Factor Auth", color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(text = "Required for all transfers", color = BodyMuted, fontSize = 12.sp)
                        }
                        CoralToggle(checked = twoFactor, onCheckedChange = { twoFactor = it })
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFEAE3D2))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (fullName.isNotBlank()) vm.updateUserName(fullName)
                                onBack()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Save Changes", color = Color(0xFF231E1A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDeactivate
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Deactivate Account", color = Coral, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
