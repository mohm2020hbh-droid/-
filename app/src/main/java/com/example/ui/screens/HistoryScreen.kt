package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.ClipboardEntity
import com.example.database.ClipboardType
import com.example.ui.L10n
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val items by viewModel.clipboardItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedTypeFilter.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val totalClipboard by viewModel.allClipboardCount.collectAsState()
    val totalSnippets by viewModel.allSnippetsCount.collectAsState()
    val context = LocalContext.current

    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<ClipboardEntity?>(null) }
    var showLearnMoreDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Stats row (Saved items, Snippets, Last sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniStatsCard(
                title = L10n.get("statsSavedItems", selectedLanguage),
                value = "$totalClipboard",
                icon = Icons.Default.ContentPaste,
                tint = Color(0xFFE53935),
                modifier = Modifier.weight(1f)
            )
            MiniStatsCard(
                title = L10n.get("statsSnippetsCount", selectedLanguage),
                value = "$totalSnippets",
                icon = Icons.Default.AutoAwesome,
                tint = Color(0xFFFF3B30),
                modifier = Modifier.weight(1f)
            )
            MiniStatsCard(
                title = L10n.get("statsLastSync", selectedLanguage),
                value = L10n.get("lastSyncTime", selectedLanguage),
                icon = Icons.Default.Sync,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.weight(1.2f)
            )
        }

        // 2. Large modern Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            placeholder = {
                Text(
                    text = L10n.get("searchPlaceholder", selectedLanguage),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFE53935)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1C1C1C),
                unfocusedContainerColor = Color(0xFF1C1C1C),
                focusedBorderColor = Color(0xFFE53935),
                unfocusedBorderColor = Color(0xFF8B0000)
            )
        )

        // 3. Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "All" chip
            FilterChipItem(
                label = L10n.get("filterAll", selectedLanguage),
                selected = selectedFilter == null,
                onClick = { viewModel.setTypeFilter(null) },
                modifier = Modifier.testTag("filter_all_button")
            )

            ClipboardType.values().forEach { type ->
                val typeName = when (type) {
                    ClipboardType.TEXT -> L10n.get("filterText", selectedLanguage)
                    ClipboardType.LINK -> L10n.get("filterLink", selectedLanguage)
                    ClipboardType.IMAGE -> L10n.get("filterImage", selectedLanguage)
                    ClipboardType.CODE -> L10n.get("filterCode", selectedLanguage)
                }
                val typeIcon = when (type) {
                    ClipboardType.TEXT -> Icons.AutoMirrored.Filled.Notes
                    ClipboardType.LINK -> Icons.Default.Link
                    ClipboardType.IMAGE -> Icons.Default.Image
                    ClipboardType.CODE -> Icons.Default.Code
                }
                FilterChipItem(
                    label = typeName,
                    icon = typeIcon,
                    selected = selectedFilter == type,
                    onClick = { viewModel.setTypeFilter(type) },
                    modifier = Modifier.testTag("filter_${type.name.lowercase()}_button")
                )
            }
        }

        // 4. Actions bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${L10n.get("navHistory", selectedLanguage)} (${items.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            if (items.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                    modifier = Modifier.testTag("clear_all_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(L10n.get("settingsCleanUnpinned", selectedLanguage), fontSize = 12.sp)
                }
            }
        }

        // 5. List items or Beautiful empty state
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.08f), RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Clipboard empty",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = L10n.get("emptyTitle", selectedLanguage),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = L10n.get("emptyDesc", selectedLanguage),
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showLearnMoreDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(L10n.get("learnMore", selectedLanguage), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ModernClipboardItemCard(
                        item = item,
                        selectedLanguage = selectedLanguage,
                        onCopy = {
                            viewModel.copyToClipboard(item.content)
                            Toast.makeText(context, L10n.get("toastCopied", selectedLanguage), Toast.LENGTH_SHORT).show()
                        },
                        onPin = { viewModel.togglePin(item) },
                        onDelete = { viewModel.deleteItem(item) },
                        onDetail = { selectedItemForDetail = item },
                        onShare = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, item.content)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    )
                }
            }
        }
    }

    // Confirmation wipe dialog
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
                        Toast.makeText(context, "تم تنظيف العناصر غير المثبتة!", Toast.LENGTH_SHORT).show()
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

    // Info trigger learn more dialog
    if (showLearnMoreDialog) {
        AlertDialog(
            onDismissRequest = { showLearnMoreDialog = false },
            title = { Text(if (selectedLanguage == "ar") "كيف يعمل COPY؟" else "How COPY works", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (selectedLanguage == "ar") {
                        "يقوم التطبيق بالاستماع الذكي للحافظة عند الفتح والمزامنة المحلية المشفرة بالكامل للأجهزة على نفس الشبكة لحماية بياناتك الشخصية 100%."
                    } else {
                        "The application securely and intelligently monitors your clipboard upon opening, supporting encrypted peer-to-peer sync on local Wi-Fi networks."
                    },
                    color = Color.LightGray,
                    lineHeight = 22.sp
                )
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = { showLearnMoreDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Detailed Modal Dialog
    if (selectedItemForDetail != null) {
        val detail = selectedItemForDetail!!
        AlertDialog(
            onDismissRequest = { selectedItemForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (detail.type) {
                            ClipboardType.LINK -> Icons.Default.Link
                            ClipboardType.CODE -> Icons.Default.Code
                            ClipboardType.IMAGE -> Icons.Default.Image
                            else -> Icons.AutoMirrored.Filled.Notes
                        },
                        contentDescription = "Type",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "تفاصيل العنصر المنسوخ" else "Clipboard Item Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = detail.content,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontFamily = if (detail.type == ClipboardType.CODE) FontFamily.Monospace else FontFamily.Default
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0D0D0D),
                            unfocusedContainerColor = Color(0xFF0D0D0D),
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "📅 ${if (selectedLanguage == "ar") "تاريخ النسخ: " else "Timestamp: "} ${formatTimestamp(detail.timestamp)}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "🏷️ ${if (selectedLanguage == "ar") "النوع: " else "Type: "} ${detail.type.name}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "🌐 ${if (selectedLanguage == "ar") "المصدر: " else "Source: "} ${detail.sourceApp ?: "فتح التطبيق"}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "💾 ${if (selectedLanguage == "ar") "الحجم: " else "Size: "} ${detail.size} bytes",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.copyToClipboard(detail.content)
                        selectedItemForDetail = null
                        Toast.makeText(context, L10n.get("toastCopied", selectedLanguage), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text(if (selectedLanguage == "ar") "نسخ" else "Copy", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForDetail = null }) {
                    Text(L10n.get("cancel", selectedLanguage), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun MiniStatsCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFF8B0000), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFE53935) else Color(0xFF1C1C1C))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFFE53935) else Color(0xFF8B0000),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else Color.LightGray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color.LightGray
            )
        }
    }
}

@Composable
fun ModernClipboardItemCard(
    item: ClipboardEntity,
    selectedLanguage: String,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onDetail: () -> Unit,
    onShare: () -> Unit
) {
    var isExpandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(18.dp))
            .clickable { onCopy() }
            .testTag("clipboard_item_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1C)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Icon + Source App + Timestamp + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.type) {
                                ClipboardType.LINK -> Icons.Default.Link
                                ClipboardType.CODE -> Icons.Default.Code
                                ClipboardType.IMAGE -> Icons.Default.Image
                                else -> Icons.AutoMirrored.Filled.Notes
                            },
                            contentDescription = "Type",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.sourceApp ?: "تطبيق خارجي",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatTimestamp(item.timestamp),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPin, modifier = Modifier.testTag("pin_button")) {
                        Icon(
                            imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (item.isPinned) Color(0xFFE53935) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { isExpandedMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.Gray)
                        }
                        DropdownMenu(
                            expanded = isExpandedMenu,
                            onDismissRequest = { isExpandedMenu = false },
                            modifier = Modifier.background(Color(0xFF1C1C1C))
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (selectedLanguage == "ar") "تفاصيل" else "Details", color = Color.White) },
                                onClick = {
                                    isExpandedMenu = false
                                    onDetail()
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (selectedLanguage == "ar") "مشاركة" else "Share", color = Color.White) },
                                onClick = {
                                    isExpandedMenu = false
                                    onShare()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.LightGray) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (selectedLanguage == "ar") "حذف" else "Delete", color = Color(0xFFFF3B30)) },
                                onClick = {
                                    isExpandedMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF3B30)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body content preview
            Text(
                text = item.content,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Divider
            HorizontalDivider(color = Color(0xFF8B0000), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Copy actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.size} bytes",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.12f))
                        .clickable { onCopy() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "نسخ فوري" else "Quick Copy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}
