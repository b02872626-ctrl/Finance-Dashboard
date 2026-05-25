package com.financeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    val supabaseEmail by settingsRepo.supabaseEmailFlow().collectAsState(initial = "")
    val isSignedIn by settingsRepo.isSupabaseSignedInFlow()
        .collectAsState(initial = settingsRepo.getSupabaseSession() != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = CharcoalText,
                    navigationIconContentColor = CharcoalText
                )
            )
        },
        containerColor = AppBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppBackground)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Profile Info Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        AccountHeader("Account Details")
                        
                        Spacer(Modifier.height(20.dp))
                        
                        AccountInfoRow(
                            icon = Icons.Default.Person,
                            label = "Email Address",
                            value = if (supabaseEmail.isBlank()) "No email provided" else supabaseEmail
                        )
                        
                        Divider(Modifier.padding(vertical = 16.dp), color = DividerColor)
                        
                        AccountInfoRow(
                            icon = Icons.Default.CloudSync,
                            label = "Sync Provider",
                            value = "Supabase Cloud"
                        )
                    }
                }
            }

            item {
                // Actions Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        AccountHeader("Management")
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                SupabaseSyncScheduler.enqueue(context)
                                Toast.makeText(context, "Sync started", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = PureWhite)
                        ) {
                            Text("Sync Now", fontWeight = FontWeight.SemiBold)
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = {
                                settingsRepo.clearSupabaseSession()
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                // The AppNavigation handles the redirection when isSignedIn changes
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedExpense.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Your financial data is encrypted and synced only with your authenticated session. Every minute, the app actively checks for new local transactions to keep your cloud workspace up to date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AccountHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = AppBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun AccountInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp)
                .background(AppSurfaceSecondary, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AppBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, color = TextMuted, fontSize = 12.sp)
            Text(value, color = CharcoalText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
