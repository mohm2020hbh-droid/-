package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.L10n
import com.example.ui.MainViewModel

@Composable
fun PaywallScreen(viewModel: MainViewModel) {
    val premiumStatus by viewModel.premiumStatus.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val context = LocalContext.current

    var selectedPlan by remember { mutableStateOf(0) } // 0: Yearly ($1.99), 1: Lifetime ($9.99)
    var isPurchasing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Premium Badge Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE53935).copy(alpha = 0.15f),
                            Color(0xFFFF3B30).copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFF3B30).copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Premium",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = if (premiumStatus == 1) {
                        if (selectedLanguage == "ar") "أنت مشترك في COPY بريميوم ✨" else "You have COPY Premium ✨"
                    } else {
                        if (selectedLanguage == "ar") "اشترك في COPY بريميوم" else "Upgrade to COPY Premium"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (premiumStatus == 1) {
                        if (selectedLanguage == "ar") "شكرًا لدعمك المستمر! جميع الميزات الاحترافية مفعلة الآن." else "Thank you for your support! All pro features are fully unlocked."
                    } else {
                        if (selectedLanguage == "ar") "احصل على مزامنة لا نهائية ومراقبة ذكية بدون حدود." else "Get unlimited local synchronization, stats, and snippets."
                    },
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        if (premiumStatus == 0) {
            // Subscription Plans
            Text(
                text = if (selectedLanguage == "ar") "اختر الخطة المناسبة لك (تجربة مجانية 7 أيام)" else "Select your plan (7 days free trial)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Plan 1: Yearly
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (selectedPlan == 0) 2.dp else 1.dp,
                        color = if (selectedPlan == 0) Color(0xFFE53935) else Color(0xFF8B0000),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { selectedPlan = 0 }
                    .testTag("plan_yearly_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedPlan == 0) Color(0xFFE53935).copy(alpha = 0.08f) else Color(0xFF1C1C1C)
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (selectedLanguage == "ar") "الاشتراك السنوي للمزامنة" else "Annual Sync Membership",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedLanguage == "ar") "مزامنة مشفرة سريعة لكافة أجهزتك" else "Fast encrypted peer-to-peer sync",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$1.99 / yr", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFE53935))
                        Text(if (selectedLanguage == "ar") "7 أيام تجربة مجانية" else "7 days free trial", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            // Plan 2: Lifetime
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (selectedPlan == 1) 2.dp else 1.dp,
                        color = if (selectedPlan == 1) Color(0xFFE53935) else Color(0xFF8B0000),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { selectedPlan = 1 }
                    .testTag("plan_lifetime_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedPlan == 1) Color(0xFFE53935).copy(alpha = 0.08f) else Color(0xFF1C1C1C)
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (selectedLanguage == "ar") "الرخصة الأبدية (شراء لمرة واحدة)" else "Lifetime Purchase (One-time)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFF3B30).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedLanguage == "ar") "الأكثر مبيعًا" else "Best Choice",
                                    fontSize = 9.sp,
                                    color = Color(0xFFFF3B30),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (selectedLanguage == "ar") "شراء مدى الحياة دون تجديد أو رسوم خفية" else "Own forever with zero recurring fees",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$9.99", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFE53935))
                        Text(if (selectedLanguage == "ar") "امتلاك مدى الحياة" else "Lifetime Access", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Compare Table Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (selectedLanguage == "ar") "مقارنة الميزات والخيارات" else "Feature Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CompareRow(
                        feature = if (selectedLanguage == "ar") "حفظ العناصر والنسخ محليًا" else "Offline Clip History",
                        free = true,
                        pro = true
                    )
                    HorizontalDivider(color = Color(0xFF8B0000))
                    CompareRow(
                        feature = if (selectedLanguage == "ar") "المقتطفات النصية الذكية والبحث" else "Smart Snippets & Search",
                        free = true,
                        pro = true
                    )
                    HorizontalDivider(color = Color(0xFF8B0000))
                    CompareRow(
                        feature = if (selectedLanguage == "ar") "لوحة المفاتيح الإضافية COPY KB" else "COPY Keyboard Access",
                        free = true,
                        pro = true
                    )
                    HorizontalDivider(color = Color(0xFF8B0000))
                    CompareRow(
                        feature = if (selectedLanguage == "ar") "تشفير ومزامنة الأجهزة بالشبكة المحلية" else "Encrypted Local Network Sync",
                        free = false,
                        pro = true
                    )
                    HorizontalDivider(color = Color(0xFF8B0000))
                    CompareRow(
                        feature = if (selectedLanguage == "ar") "دعم وتحديثات وميزات قادمة" else "Priority updates and new stats",
                        free = false,
                        pro = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Purchase Buttons
            if (isPurchasing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE53935))
                }
            } else {
                Button(
                    onClick = {
                        isPurchasing = true
                        viewModel.purchasePremium(1)
                        isPurchasing = false
                        showSuccessDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("buy_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (selectedPlan == 0) {
                            if (selectedLanguage == "ar") "ابدأ التجربة المجانية والاشتراك" else "Start Free Trial & Subscribe"
                        } else {
                            if (selectedLanguage == "ar") "شراء الرخصة الأبدية الآن" else "Buy Lifetime License Now"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        viewModel.restorePurchase()
                        Toast.makeText(context, if (selectedLanguage == "ar") "تم استعادة مشترياتك السابقة بنجاح!" else "Previous purchases restored!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_purchase_button")
                ) {
                    Text(
                        text = if (selectedLanguage == "ar") "استعادة المشتريات السابقة" else "Restore previous purchases",
                        textDecoration = TextDecoration.Underline,
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            // Already Pro state UI
            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = if (selectedLanguage == "ar") "أنت عضو بريميوم فعال في COPY" else "You are a COPY Premium Member",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (selectedLanguage == "ar") {
                            "تم إلغاء قفل جميع المزايا المتقدمة والمزامنة والتحديثات بدون حدود."
                        } else {
                            "All premium perks, advanced network sync, and weekly stats breakdowns are unlocked."
                        },
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.purchasePremium(0) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, Color(0xFFFF3B30).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Text(if (selectedLanguage == "ar") "إلغاء الاشتراك التجريبي (وضع الاختبار)" else "Cancel subscription (Sandbox mode)")
                    }
                }
            }
        }
    }

    // Success Purchase Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = "Congrats", tint = Color(0xFFFF3B30))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedLanguage == "ar") "تهانينا الكبيرة! 🎉" else "Congratulations! 🎉", color = Color.White)
                }
            },
            text = {
                Text(
                    text = if (selectedLanguage == "ar") {
                        "تم تفعيل باقة COPY بريميوم بنجاح! شكرًا جزيلًا لدعمك لتطوير التطبيق. يمكنك الآن الاستمتاع بجميع الخصائص والمزامنة اللانهائية."
                    } else {
                        "COPY Premium unlocked successfully! Thank you for supporting the app's development. You can now use all synchronization options."
                    },
                    color = Color.LightGray,
                    lineHeight = 20.sp
                )
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text(if (selectedLanguage == "ar") "البدء بالاستخدام" else "Get Started")
                }
            }
        )
    }
}

@Composable
fun CompareRow(feature: String, free: Boolean, pro: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(feature, fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = if (free) "✓ Free" else "✗ Locked",
                fontSize = 11.sp,
                color = if (free) Color(0xFFE53935) else Color(0xFFFF3B30),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "✓ Pro",
                fontSize = 11.sp,
                color = Color(0xFFFF3B30),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
