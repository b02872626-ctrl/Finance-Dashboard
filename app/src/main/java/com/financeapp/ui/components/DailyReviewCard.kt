package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.theme.pressScale
import com.financeapp.ui.theme.rememberTactileSource
import com.financeapp.viewmodel.DailyReviewSnapshot

/**
 * Single "Review today's transactions" entry on Home — matches V2 mock:
 *
 *   Review today's transactions                                [→]
 *   8 transactions need confirmation
 *
 * Replaces the prior two-card stack (Daily Review status + "Catch up
 * on older"). Title / subtitle / tap target are picked from state:
 *
 *  - today.pending > 0           → Today CTA            tap → onStartToday
 *  - today done && older > 0     → Older CTA            tap → onStartOlder
 *  - everything reviewed         → "All caught up"      no arrow, no tap
 *  - no transactions yet today   → "Listening..."       no arrow, no tap
 *
 * @param snapshot      today's queue stats (pending/done/total)
 * @param olderPending  count of pending rows older than today
 */
@Composable
fun DailyReviewCard(
    snapshot: DailyReviewSnapshot,
    olderPending: Int,
    onStartToday: () -> Unit,
    onStartOlder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg     = Color(0xFF231E1A)
    val borderCol  = Color(0xFF353535)
    val text       = Color(0xFFF5F2EB)
    val textMute   = Color(0xFFB7B3AC)
    val mint       = Color(0xFF82C8AA)
    val coral      = Color(0xFFFB5D53)

    // Three-state machine. Today wins over older; both win over "all done".
    val view: ReviewView = when {
        snapshot.pending > 0 -> ReviewView.TodayCta(snapshot.pending, onStartToday)
        olderPending > 0     -> ReviewView.OlderCta(olderPending, onStartOlder)
        snapshot.total > 0   -> ReviewView.AllCaughtUp(snapshot.done)
        else                 -> ReviewView.Idle
    }

    val cardIx = rememberTactileSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (view is ReviewView.HasAction) Modifier.pressScale(cardIx)
                else Modifier
            )
            .clip(RoundedCornerShape(26.dp))
            .background(cardBg)
            .border(1.dp, borderCol, RoundedCornerShape(26.dp))
            .then(
                if (view is ReviewView.HasAction) Modifier.clickable(
                    interactionSource = cardIx,
                    indication = null,
                    onClick = view.onClick
                ) else Modifier
            )
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                view.title,
                color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp
            )
            Text(
                view.subtitle,
                color = textMute, fontSize = 12.5.sp, lineHeight = 17.sp
            )
        }

        when (view) {
            is ReviewView.HasAction -> CoralArrow(coral = coral, onClick = view.onClick)
            is ReviewView.AllCaughtUp -> MintCheck(mint = mint)
            ReviewView.Idle           -> Spacer(Modifier.width(0.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// State view model — picks copy + which trailing affordance to render.
// ─────────────────────────────────────────────────────────────────────────
private sealed interface ReviewView {
    val title: String
    val subtitle: String

    sealed interface HasAction : ReviewView {
        val onClick: () -> Unit
    }

    data class TodayCta(val count: Int, override val onClick: () -> Unit) : HasAction {
        override val title    = "Review today's transactions"
        override val subtitle = "$count ${if (count == 1) "transaction" else "transactions"} need confirmation"
    }

    data class OlderCta(val count: Int, override val onClick: () -> Unit) : HasAction {
        override val title    = "Review older transactions"
        override val subtitle = "$count ${if (count == 1) "transaction" else "transactions"} still need a category"
    }

    data class AllCaughtUp(val doneToday: Int) : ReviewView {
        override val title    = "All caught up"
        override val subtitle = "${doneToday} ${if (doneToday == 1) "transaction" else "transactions"} reviewed today"
    }

    data object Idle : ReviewView {
        override val title    = "Listening for new transactions"
        override val subtitle = "We'll let you know when an SMS comes in"
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Trailing affordances
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun CoralArrow(coral: Color, onClick: () -> Unit) {
    val ix = rememberTactileSource()
    Box(
        modifier = Modifier
            .size(48.dp)
            .pressScale(ix, pressedScale = 0.90f)
            .clip(CircleShape)
            .background(coral)
            .clickable(
                interactionSource = ix,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Start review",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MintCheck(mint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(mint),
        contentAlignment = Alignment.Center
    ) {
        Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
