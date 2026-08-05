package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.L10n
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val retentionDays by viewModel.retentionDays.collectAsState()
    val startOnBoot by viewModel.startOnBoot.collectAsState()
    val isServiceActive by viewModel.isForegroundServiceActive.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val context = LocalContext.current

    var showClearConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRetentionMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshServiceStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Title
        Text(
            text = L10n.get("navSettings", selectedLanguage),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // 1. Language preference settings card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = L10n.get("settingsLanguageTitle", selectedLanguage),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = L10n.get("settingsLanguageDesc", selectedLanguage),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showLanguageMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("language_dropdown_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (selectedLanguage) {
                                    "ar" -> "العربية (Arabic)"
                                    "en" -> "English"
                                    else -> L10n.get("systemDefault", selectedLanguage)
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                        modifier = Modifier.background(Color(0xFF1C1C1C))
                    ) {
                        DropdownMenuItem(
                            text = { Text("العربية (Arabic)", color = Color.White) },
                            onClick = {
                                viewModel.setSelectedLanguage("ar")
                                showLanguageMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("English", color = Color.White) },
                            onClick = {
                                viewModel.setSelectedLanguage("en")
                                showLanguageMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.get("systemDefault", selectedLanguage), color = Color.White) },
                            onClick = {
                                viewModel.setSelectedLanguage("system")
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 2. Foreground service settings card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = L10n.get("settingsServiceTitle", selectedLanguage),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = L10n.get("settingsServiceDesc", selectedLanguage),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = isServiceActive,
                    onCheckedChange = { viewModel.toggleForegroundService(it) },
                    modifier = Modifier.testTag("service_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE53935)
                    )
                )
            }
        }

        // 3. Auto restart on boot card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = L10n.get("settingsBootTitle", selectedLanguage),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = L10n.get("settingsBootDesc", selectedLanguage),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = { viewModel.toggleStartOnBoot(it) },
                    modifier = Modifier.testTag("boot_start_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE53935)
                    )
                )
            }
        }

        // 4. Retention settings card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = L10n.get("settingsRetentionTitle", selectedLanguage),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = L10n.get("settingsRetentionDesc", selectedLanguage),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showRetentionMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("retention_dropdown_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (retentionDays) {
                                    1 -> if (selectedLanguage == "ar") "يوم واحد" else "1 Day"
                                    7 -> if (selectedLanguage == "ar") "أسبوع واحد (7 أيام)" else "1 Week (7 Days)"
                                    30 -> if (selectedLanguage == "ar") "شهر واحد (30 يومًا)" else "1 Month (30 Days)"
                                    90 -> if (selectedLanguage == "ar") "3 أشهر (90 يومًا)" else "3 Months (90 Days)"
                                    else -> if (selectedLanguage == "ar") "أبدًا (عدم الحذف التلقائي)" else "Never (No auto delete)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = showRetentionMenu,
                        onDismissRequest = { showRetentionMenu = false },
                        modifier = Modifier.background(Color(0xFF1C1C1C))
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (selectedLanguage == "ar") "يوم واحد" else "1 Day", color = Color.White) },
                            onClick = {
                                viewModel.setRetentionDays(1)
                                showRetentionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (selectedLanguage == "ar") "أسبوع واحد" else "1 Week", color = Color.White) },
                            onClick = {
                                viewModel.setRetentionDays(7)
                                showRetentionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (selectedLanguage == "ar") "شهر واحد" else "1 Month", color = Color.White) },
                            onClick = {
                                viewModel.setRetentionDays(30)
                                showRetentionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (selectedLanguage == "ar") "3 أشهر" else "3 Months", color = Color.White) },
                            onClick = {
                                viewModel.setRetentionDays(90)
                                showRetentionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (selectedLanguage == "ar") "أبدًا" else "Never", color = Color.White) },
                            onClick = {
                                viewModel.setRetentionDays(0)
                                showRetentionMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 5. Database cleanup actions card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = L10n.get("settingsCleanTitle", selectedLanguage),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = L10n.get("settingsCleanDesc", selectedLanguage),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.15f), contentColor = Color(0xFFFF3B30)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_unpinned_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clean", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(L10n.get("settingsCleanUnpinned", selectedLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFF3B30).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .testTag("reset_db_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete all", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(L10n.get("settingsClearAllTitle", selectedLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 6. Info card explaining OS limitations
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "توضيح قيود نظام تشغيل Android وiOS" else "OS Clipboard Constraints Explanation",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
                Text(
                    text = if (selectedLanguage == "ar") {
                        "• على Android 10+، قيود الأمان تمنع قراءة الحافظة مباشرة في الخلفية إلا إذا كان التطبيق مفتوحًا أو تستخدم لوحة المفاتيح الإضافية المدمجة COPY KB.\n\n" +
                        "• على iOS، يمنع تمامًا تشغيل مراقبة الحافظة في الخلفية بأي شكل، ويتم الفحص تلقائيًا فور فتح التطبيق أو استخدام Keyboard Extension المضافة لضمان سلامة الخصوصية ومستوى استهلاك البطارية."
                    } else {
                        "• On Android 10+, OS security rules block background clipboard polling unless the app is currently in the foreground or you are using the COPY KB extension.\n\n" +
                        "• On iOS, background polling is fully disabled by Apple sandbox rules. Capturing happens securely upon reopening the app or using the integrated Keyboard Extension."
                    },
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = Color.Gray
                )
            }
        }
    }

    // Confirm dialogs
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(L10n.get("confirmClearTitle", selectedLanguage), color = Color.White) },
            text = { Text(L10n.get("confirmClearDesc", selectedLanguage), color = Color.LightGray) },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUnpinned()
                        showClearConfirm = false
                        Toast.makeText(context, if (selectedLanguage == "ar") "تم تنظيف العناصر غير المثبتة!" else "Unpinned items cleaned!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) {
                    Text(L10n.get("confirm", selectedLanguage), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(L10n.get("cancel", selectedLanguage), color = Color.Gray)
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(L10n.get("settingsClearAllTitle", selectedLanguage), color = Color.White) },
            text = { Text(if (selectedLanguage == "ar") "تحذير: هذا سيؤدي إلى حذف جميع الحافظات والمقتطفات تمامًا. هل ترغب بالمتابعة؟" else "Warning: This will permanently erase all history and snippets. Proceed?", color = Color.LightGray) },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showResetConfirm = false
                        Toast.makeText(context, if (selectedLanguage == "ar") "تم مسح وتطهير قاعدة البيانات تمامًا!" else "Database cleared permanently!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) {
                    Text(if (selectedLanguage == "ar") "مسح كلي" else "Wipe All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(L10n.get("cancel", selectedLanguage), color = Color.Gray)
                }
            }
        )
    }
}
