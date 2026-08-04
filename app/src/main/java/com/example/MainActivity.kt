package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.L10n
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // Permission request contract for Android 13+ POST_NOTIFICATIONS
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "تم تفعيل إشعارات خدمة مراقبة الحافظة!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "تحتاج لتمكين الإشعارات لإظهار الخدمة في الخلفية.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val premiumStatus by viewModel.premiumStatus.collectAsState()
                val selectedLanguage by viewModel.selectedLanguage.collectAsState()

                // Determine dynamic layout direction based on language selection
                val isRtl = when (selectedLanguage) {
                    "ar" -> true
                    "en" -> false
                    else -> java.util.Locale.getDefault().language.startsWith("ar")
                }
                val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(
                                            text = L10n.get("appName", selectedLanguage),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color.White,
                                            style = androidx.compose.ui.text.TextStyle(
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    color = Color(0xFFE53935).copy(alpha = 0.5f),
                                                    blurRadius = 6f
                                                )
                                            )
                                        )
                                        if (premiumStatus == 1) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFFF3B30).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = L10n.get("premium", selectedLanguage),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFF3B30)
                                                )
                                            }
                                        }
                                    }
                                },
                                actions = {
                                    if (premiumStatus == 0) {
                                        IconButton(onClick = { viewModel.navigateTo(AppScreen.Paywall) }) {
                                            Icon(
                                                imageVector = Icons.Default.WorkspacePremium,
                                                contentDescription = "Upgrade",
                                                tint = Color(0xFFFF3B30)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.navigateTo(AppScreen.Settings) }) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = Color.LightGray
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Color(0xFF0D0D0D)
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xFF161616),
                                tonalElevation = 0.dp,
                                modifier = Modifier.background(Color(0xFF161616))
                            ) {
                                val itemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFE53935),
                                    unselectedIconColor = Color.Gray,
                                    selectedTextColor = Color(0xFFE53935),
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color(0xFFE53935).copy(alpha = 0.15f)
                                )

                                NavigationBarItem(
                                    selected = currentScreen is AppScreen.History,
                                    onClick = { viewModel.navigateTo(AppScreen.History) },
                                    label = { Text(L10n.get("navHistory", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                    colors = itemColors
                                )
                                NavigationBarItem(
                                    selected = currentScreen is AppScreen.Snippets,
                                    onClick = { viewModel.navigateTo(AppScreen.Snippets) },
                                    label = { Text(L10n.get("navSnippets", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Snippets") },
                                    colors = itemColors
                                )
                                NavigationBarItem(
                                    selected = currentScreen is AppScreen.Sync,
                                    onClick = { viewModel.navigateTo(AppScreen.Sync) },
                                    label = { Text(L10n.get("navSync", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    icon = { Icon(Icons.Default.Sync, contentDescription = "Sync") },
                                    colors = itemColors
                                )
                                NavigationBarItem(
                                    selected = currentScreen is AppScreen.Stats,
                                    onClick = { viewModel.navigateTo(AppScreen.Stats) },
                                    label = { Text(L10n.get("navStats", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Stats") },
                                    colors = itemColors
                                )
                                NavigationBarItem(
                                    selected = currentScreen is AppScreen.Settings,
                                    onClick = { viewModel.navigateTo(AppScreen.Settings) },
                                    label = { Text(L10n.get("navSettings", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    colors = itemColors
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D0D0D))
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                is AppScreen.History -> HistoryScreen(viewModel)
                                is AppScreen.Snippets -> SnippetsScreen(viewModel)
                                is AppScreen.Sync -> SyncScreen(viewModel)
                                is AppScreen.Stats -> StatsScreen(viewModel)
                                is AppScreen.Paywall -> PaywallScreen(viewModel)
                                is AppScreen.Settings -> SettingsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically check/capture current clipboard when app gains focus
        viewModel.checkAndSaveClipboardOnResume()
        viewModel.refreshServiceStatus()
    }
}
