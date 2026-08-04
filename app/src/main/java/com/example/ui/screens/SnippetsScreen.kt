package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.SnippetEntity
import com.example.ui.L10n
import com.example.ui.MainViewModel

@Composable
fun SnippetsScreen(viewModel: MainViewModel) {
    val snippets by viewModel.snippets.collectAsState()
    val searchQuery by viewModel.snippetQuery.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var snippetToEdit by remember { mutableStateOf<SnippetEntity?>(null) }

    // Dialog Input states
    var snippetName by remember { mutableStateOf("") }
    var snippetContent by remember { mutableStateOf("") }
    var snippetShortcut by remember { mutableStateOf("") }

    // Reset fields helper
    fun resetFields() {
        snippetName = ""
        snippetContent = ""
        snippetShortcut = ""
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    resetFields()
                    showAddDialog = true
                },
                containerColor = Color(0xFFE53935),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_snippet_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Snippet")
            }
        },
        containerColor = Color(0xFF0D0D0D)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSnippetQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("snippet_search_input"),
                placeholder = {
                    Text(
                        text = if (selectedLanguage == "ar") "البحث في المقتطفات الذكية..." else "Search smart snippets...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFE53935)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSnippetQuery("") }) {
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

            Text(
                text = "${L10n.get("navSnippets", selectedLanguage)} (${snippets.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            // Snippets list or empty state
            if (snippets.isEmpty()) {
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
                                .background(Color(0xFFFF3B30).copy(alpha = 0.08f), RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "No snippets",
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (selectedLanguage == "ar") "لا توجد مقتطفات ذكية" else "No smart snippets",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedLanguage == "ar") {
                                "أضف نصوصك الأكثر استخدامًا (مثل البريد الإلكتروني أو العناوين) للوصول إليها ونسخها فورا بلمسة واحدة."
                            } else {
                                "Add your frequently used texts (like email or links) to quickly copy them with a single touch."
                            },
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        ModernSnippetCard(
                            snippet = snippet,
                            selectedLanguage = selectedLanguage,
                            onCopy = {
                                viewModel.copyToClipboard(snippet.content)
                                Toast.makeText(context, L10n.get("toastCopied", selectedLanguage), Toast.LENGTH_SHORT).show()
                            },
                            onEdit = {
                                snippetToEdit = snippet
                                snippetName = snippet.name
                                snippetContent = snippet.content
                                snippetShortcut = snippet.shortcut ?: ""
                            },
                            onDelete = {
                                viewModel.deleteSnippet(snippet)
                                Toast.makeText(context, if (selectedLanguage == "ar") "تم حذف المقتطف!" else "Snippet deleted!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = if (selectedLanguage == "ar") "إضافة مقتطف ذكي" else "Add Smart Snippet",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = snippetName,
                        onValueChange = { snippetName = it },
                        label = { Text(if (selectedLanguage == "ar") "الاسم أو العنوان" else "Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )
                    OutlinedTextField(
                        value = snippetContent,
                        onValueChange = { snippetContent = it },
                        label = { Text(if (selectedLanguage == "ar") "المحتوى النصي" else "Text Content") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )
                    OutlinedTextField(
                        value = snippetShortcut,
                        onValueChange = { snippetShortcut = it },
                        label = { Text(if (selectedLanguage == "ar") "الاختصار (مثال: emy)" else "Shortcut (e.g. emy)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )
                }
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        if (snippetName.isNotBlank() && snippetContent.isNotBlank()) {
                            viewModel.saveSnippet(snippetName, snippetContent, snippetShortcut)
                            showAddDialog = false
                            Toast.makeText(context, if (selectedLanguage == "ar") "تم حفظ المقتطف بنجاح!" else "Snippet saved successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى تعبئة الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text(L10n.get("confirm", selectedLanguage), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(L10n.get("cancel", selectedLanguage), color = Color.Gray)
                }
            }
        )
    }

    // Edit dialog
    if (snippetToEdit != null) {
        val editing = snippetToEdit!!
        AlertDialog(
            onDismissRequest = { snippetToEdit = null },
            title = {
                Text(
                    text = if (selectedLanguage == "ar") "تعديل المقتطف" else "Edit Snippet",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = snippetName,
                        onValueChange = { snippetName = it },
                        label = { Text(if (selectedLanguage == "ar") "العنوان" else "Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )
                    OutlinedTextField(
                        value = snippetContent,
                        onValueChange = { snippetContent = it },
                        label = { Text(if (selectedLanguage == "ar") "المحتوى" else "Content") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )
                    OutlinedTextField(
                        value = snippetShortcut,
                        onValueChange = { snippetShortcut = it },
                        label = { Text(if (selectedLanguage == "ar") "الاختصار" else "Shortcut") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53935),
                            unfocusedBorderColor = Color(0xFF8B0000)
                        )
                    )
                }
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        if (snippetName.isNotBlank() && snippetContent.isNotBlank()) {
                            val updated = editing.copy(
                                name = snippetName,
                                content = snippetContent,
                                shortcut = if (snippetShortcut.trim().isEmpty()) null else snippetShortcut,
                                timestamp = System.currentTimeMillis()
                            )
                            viewModel.updateSnippet(updated)
                            snippetToEdit = null
                            Toast.makeText(context, if (selectedLanguage == "ar") "تم تحديث المقتطف!" else "Snippet updated!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text(L10n.get("confirm", selectedLanguage), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { snippetToEdit = null }) {
                    Text(L10n.get("cancel", selectedLanguage), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ModernSnippetCard(
    snippet: SnippetEntity,
    selectedLanguage: String,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(18.dp))
            .clickable { onCopy() }
            .testTag("snippet_item_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = snippet.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!snippet.shortcut.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE53935).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${if (selectedLanguage == "ar") "اختصار: " else "Shortcut: "}${snippet.shortcut}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = snippet.content,
                fontSize = 13.sp,
                color = Color.LightGray,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF8B0000), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF3B30).copy(alpha = 0.12f))
                        .clickable { onCopy() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "نسخ فوري" else "Quick Copy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF3B30)
                    )
                }
            }
        }
    }
}
