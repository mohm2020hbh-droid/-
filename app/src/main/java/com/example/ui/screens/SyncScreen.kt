package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.L10n
import com.example.ui.MainViewModel

@Composable
fun SyncScreen(viewModel: MainViewModel) {
    val syncingState by viewModel.syncingState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val context = LocalContext.current

    var pairingInputCode by remember { mutableStateOf("") }

    // Sync filter preferences state
    var syncText by remember { mutableStateOf(true) }
    var syncLink by remember { mutableStateOf(true) }
    var syncCode by remember { mutableStateOf(true) }
    var syncImage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Gradient Card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE53935).copy(alpha = 0.08f),
                                Color(0xFFFF3B30).copy(alpha = 0.03f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "المزامنة المحلية الآمنة (AES-256)" else "Secure Local Sync (AES-256)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedLanguage == "ar") {
                            "مزامنة الحافظة والمقتطفات مع أجهزتك الأخرى مباشرة عبر الشبكة المحلية دون الحاجة لخوادم سحابية."
                        } else {
                            "Sync your clipboard and smart snippets to other devices directly over your local network without external cloud servers."
                        },
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Live connection status card
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (selectedLanguage == "ar") "حالة الاتصال النشط" else "Active Connection Status",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLanguage == "ar") "الوضع الحالي:" else "Current status:",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE53935).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = syncingState,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                    }
                }

                if (pairedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "الأجهزة المتصلة والموثوقة:" else "Connected & trusted devices:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    pairedDevices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D0D0D), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = "Device",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFF3B30)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(device, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.startNetworkSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_sync_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = "Wifi", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "ابدأ الفحص والمزامنة الفورية" else "Start Scan & Real-time Sync",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // QR Code Section
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (selectedLanguage == "ar") "رمز الاقتران QR Code الخاص بك" else "Your Pairing QR Code",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (selectedLanguage == "ar") {
                        "امسح هذا الرمز باستخدام كاميرا COPY في جهاز آخر للاقتران فوريًا وتأسيس قناة اتصال مشفرة."
                    } else {
                        "Scan this code using COPY camera on another device to pair instantly over an encrypted channel."
                    },
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Beautiful custom drawn QR code placeholder
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Custom vector drawn qr code look
                        // Top-left block
                        drawRect(Color.Black, Offset(0f, 0f), Size(36f, 36f))
                        drawRect(Color.White, Offset(10f, 10f), Size(16f, 16f))
                        drawRect(Color.Black, Offset(14f, 14f), Size(8f, 8f))

                        // Top-right block
                        drawRect(Color.Black, Offset(size.width - 36f, 0f), Size(36f, 36f))
                        drawRect(Color.White, Offset(size.width - 26f, 10f), Size(16f, 16f))
                        drawRect(Color.Black, Offset(size.width - 22f, 14f), Size(8f, 8f))

                        // Bottom-left block
                        drawRect(Color.Black, Offset(0f, size.height - 36f), Size(36f, 36f))
                        drawRect(Color.White, Offset(10f, size.height - 26f), Size(16f, 16f))
                        drawRect(Color.Black, Offset(14f, size.height - 22f), Size(8f, 8f))

                        // Random bits details to make it look like QR
                        drawRect(Color.Black, Offset(50f, 20f), Size(10f, 10f))
                        drawRect(Color.Black, Offset(70f, 10f), Size(15f, 5f))
                        drawRect(Color.Black, Offset(50f, 50f), Size(12f, 12f))
                        drawRect(Color.Black, Offset(20f, 70f), Size(8f, 15f))
                        drawRect(Color.Black, Offset(70f, 70f), Size(20f, 10f))
                        drawRect(Color.Black, Offset(100f, 50f), Size(10f, 25f))
                        drawRect(Color.Black, Offset(50f, 100f), Size(30f, 10f))
                        drawRect(Color.Black, Offset(100f, 100f), Size(15f, 15f))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Manual pairing code alternative
                OutlinedTextField(
                    value = pairingInputCode,
                    onValueChange = { pairingInputCode = it },
                    label = { Text(if (selectedLanguage == "ar") "أدخل رمز الاقتران للجهاز الآخر" else "Enter remote pairing code") },
                    placeholder = { Text("e.g. CF-9824") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_code_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE53935),
                        unfocusedBorderColor = Color(0xFF8B0000)
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (pairingInputCode.trim().isNotEmpty()) {
                                    viewModel.pairDeviceWithQR(pairingInputCode)
                                    pairingInputCode = ""
                                    Toast.makeText(context, if (selectedLanguage == "ar") "تم إرسال طلب الربط المشفر!" else "Encrypted connection request sent!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("submit_pairing_code_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Pair", tint = Color(0xFFE53935))
                        }
                    }
                )
            }
        }

        // Sync preferences
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (selectedLanguage == "ar") "خيارات وتفضيلات المزامنة" else "Sync Preferences",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (selectedLanguage == "ar") {
                        "حدد أنواع الحافظات التي ترغب بمزامنتها تلقائيًا بين أجهزتك:"
                    } else {
                        "Select content types you want to automatically sync between devices:"
                    },
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncText,
                        onCheckedChange = { syncText = it },
                        modifier = Modifier.testTag("sync_text_checkbox"),
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE53935))
                    )
                    Text(text = if (selectedLanguage == "ar") "مزامنة النصوص العامة" else "Sync plain text", fontSize = 13.sp, color = Color.LightGray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncLink,
                        onCheckedChange = { syncLink = it },
                        modifier = Modifier.testTag("sync_link_checkbox"),
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE53935))
                    )
                    Text(text = if (selectedLanguage == "ar") "مزامنة الروابط والمواقع" else "Sync links and URLs", fontSize = 13.sp, color = Color.LightGray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncCode,
                        onCheckedChange = { syncCode = it },
                        modifier = Modifier.testTag("sync_code_checkbox"),
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE53935))
                    )
                    Text(text = if (selectedLanguage == "ar") "مزامنة الأكواد والمقتطفات الذكية" else "Sync source code & snippets", fontSize = 13.sp, color = Color.LightGray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncImage,
                        onCheckedChange = { syncImage = it },
                        modifier = Modifier.testTag("sync_image_checkbox"),
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE53935))
                    )
                    Text(text = if (selectedLanguage == "ar") "مزامنة الصور المؤقتة (تتطلب باقة بريميوم)" else "Sync clip images (Premium subscription)", fontSize = 13.sp, color = Color.LightGray)
                }
            }
        }
    }
}
