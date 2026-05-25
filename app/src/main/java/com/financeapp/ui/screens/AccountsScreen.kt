package com.financeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.financeapp.R
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.viewmodel.AccountSummary
import com.financeapp.viewmodel.DashboardViewModel
import java.util.Locale

// =============================================================================
// Accounts (Sources) — pixel-faithful reproduction of
//   design/Accounts/references/Accounts_light_Option 7.png
//
// Layout:
//   1. Header — "Sources" tiny label, "Accounts" big bold teal + profile
//   2. Card deck — N cards stacked with vertical overlap; the front card
//      is fully expanded and shows balance + bank info; other cards peek
//      from behind showing only the balance + logo.
//   3. Tap a peeking card to bring it to the front.
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)
private val CardBgDim  = Color(0xFF1B1714)
private val Headline   = AppBlue
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)

@Composable
fun AccountsScreen(vm: DashboardViewModel, onAccountClick: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val accounts = state.accountBalances

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 140.dp)
    ) {
        AccountsHeader()
        Spacer(Modifier.height(24.dp))

        if (accounts.isEmpty()) {
            EmptyState()
        } else {
            CardDeck(accounts = accounts, onOpen = { bank -> onAccountClick(bank) })
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
@Composable
private fun AccountsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Sources", color = Headline, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Accounts",
                color = Headline,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_profile_dark),
                contentDescription = "Profile",
                tint = Headline,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stacked card deck
// ---------------------------------------------------------------------------
@Composable
private fun CardDeck(
    accounts: List<AccountSummary>,
    onOpen: (String) -> Unit
) {
    if (accounts.isEmpty()) return

    // Track which bank is at the front. Persists across rotations.
    var frontKey by rememberSaveable(accounts.size) {
        mutableStateOf(accounts.lastOrNull()?.bankName ?: "")
    }
    val ordered = remember(accounts, frontKey) {
        val list = accounts.toMutableList()
        val idx = list.indexOfFirst { it.bankName == frontKey }
        if (idx >= 0) {
            val front = list.removeAt(idx)
            list.add(front) // append the chosen front last so it draws on top + lives at the bottom of the stack
        }
        list
    }

    val cardHeight = 320.dp
    val peekOffset = 80.dp
    val cellCount = ordered.size.coerceAtLeast(1)
    val deckHeight = (peekOffset.value * (cellCount - 1) + cardHeight.value).dp

    val scope = rememberCoroutineScope()
    // The front card's extra y-shift while the user is dragging it down.
    val swipeOffset = remember { Animatable(0f) }
    val swipeThreshold = 120f                       // pixels of drag to commit cycle
    val flyAwayDistance = deckHeight.value + 240f   // how far down the dismissed card travels
    val canCycle = accounts.size > 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(deckHeight)
            .clipToBounds()                          // the dismissed card disappears off the bottom
    ) {
        ordered.forEachIndexed { drawIndex, account ->
            val isFront = drawIndex == ordered.lastIndex
            // Smoothly animate the base position when the deck reorders.
            val animatedBaseY by animateDpAsState(
                targetValue = (peekOffset.value * drawIndex).dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "baseY-${account.bankName}"
            )
            val extraY = if (isFront) swipeOffset.value.dp else 0.dp

            val cardModifier = Modifier
                .offset(y = animatedBaseY + extraY)
                .fillMaxWidth()
                .height(cardHeight)
                .let { mod ->
                    if (isFront && canCycle) {
                        mod
                            .pointerInput(account.bankName) {
                                detectVerticalDragGestures(
                                    onDragStart = {},
                                    onDragCancel = {
                                        scope.launch {
                                            swipeOffset.animateTo(
                                                0f,
                                                spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            if (swipeOffset.value > swipeThreshold) {
                                                // Step 1 — slide the front card down off the deck.
                                                swipeOffset.animateTo(
                                                    targetValue = flyAwayDistance,
                                                    animationSpec = tween(220)
                                                )
                                                // Step 2 — pick the card that was peeking just above it as the new front.
                                                val newFront = ordered
                                                    .getOrNull(ordered.lastIndex - 1)
                                                    ?.bankName
                                                    ?: accounts.first().bankName
                                                frontKey = newFront
                                                // Step 3 — reset offset. The cycled card is now at drawIndex 0
                                                // (drawn behind the rest); animateDpAsState slides it back into
                                                // the back-of-stack position naturally.
                                                swipeOffset.snapTo(0f)
                                            } else {
                                                swipeOffset.animateTo(
                                                    0f,
                                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                )
                                            }
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            val next = (swipeOffset.value + dragAmount)
                                                .coerceAtLeast(-40f) // tiny rubber-band upward, mostly blocks upward drag
                                            swipeOffset.snapTo(next)
                                        }
                                    }
                                )
                            }
                            .pointerInput(account.bankName) {
                                detectTapGestures(onTap = { onOpen(account.bankName) })
                            }
                    } else {
                        mod.clickable(
                            interactionSource = remember(account.bankName) { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isFront) onOpen(account.bankName) else frontKey = account.bankName
                        }
                    }
                }

            BalanceCard(
                account = account,
                isFront = isFront,
                modifier = cardModifier
            )
        }
    }
}

@Composable
private fun BalanceCard(
    account: AccountSummary,
    isFront: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (isFront) CardBg else CardBgDim)
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = moneyShort(account.balance),
                color = if (isFront) Headline else Headline.copy(alpha = 0.6f),
                fontSize = if (isFront) 38.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f)
            )
            BankLogo(bankName = account.bankName, isFront = isFront)
        }

        if (isFront) {
            Spacer(Modifier.weight(1f))
            Text(
                text = account.bankName,
                color = Headline,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            account.accountNumber.takeIf { it.isNotBlank() && it != "Unknown Account" }?.let {
                Text(text = "Account - $it", color = BodyMuted, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = "${account.transactionCount} transactions Tracked",
                color = BodyMuted,
                fontSize = 13.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bank logo picker
// ---------------------------------------------------------------------------
@Composable
private fun BankLogo(bankName: String, isFront: Boolean) {
    val logoRes = bankLogoFor(bankName)
    val size = if (isFront) 52.dp else 36.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (logoRes != null) {
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = bankName,
                modifier = Modifier
                    .size(size)
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // Fallback: bank initial in dark on white circle
            Text(
                text = bankInitial(bankName),
                color = Color(0xFF231E1A),
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun bankLogoFor(name: String): Int? {
    val n = name.lowercase(Locale.US)
    return when {
        "telebirr" in n            -> R.drawable.logo_telebirr
        "abyssinia" in n           -> R.drawable.logo_boa
        "boa" == n                 -> R.drawable.logo_boa
        "commercial bank" in n     -> R.drawable.logo_cbe
        "cbe" in n                 -> R.drawable.logo_cbe
        else                       -> null
    }
}

private fun bankInitial(name: String): String =
    name.trim().split(Regex("\\s+")).firstOrNull()?.firstOrNull()?.uppercase() ?: "?"

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No accounts detected yet.\nImport SMS messages to see them here.",
            color = BodyMuted,
            fontSize = 13.sp
        )
    }
}

private fun moneyShort(amount: Double): String =
    String.format(Locale.US, "%,.2f", amount)
