package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.L10n
import com.example.ui.MainViewModel

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val totalClipboard by viewModel.allClipboardCount.collectAsState()
    val totalSnippets by viewModel.allSnippetsCount.collectAsState()
    val items by viewModel.clipboardItems.collectAsState()

    val textCount = remember(items) { items.count { it.type.name == "TEXT" } }
    val linkCount = remember(items) { items.count { it.type.name == "LINK" } }
    val codeCount = remember(items) { items.count { it.type.name == "CODE" } }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Title
        Text(
            text = L10n.get("navStats", selectedLanguage),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Grid cards for general stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsGridCard(
                title = L10n.get("statsSavedItems", selectedLanguage),
                value = "$totalClipboard",
                icon = Icons.Default.ContentPaste,
                gradientColors = listOf(Color(0xFFE53935), Color(0xFFFF3B30)),
                modifier = Modifier.weight(1f)
            )

            StatsGridCard(
                title = L10n.get("statsSnippetsCount", selectedLanguage),
                value = "$totalSnippets",
                icon = Icons.Default.AutoAwesome,
                gradientColors = listOf(Color(0xFFFF3B30), Color(0xFFD0BCFF)),
                modifier = Modifier.weight(1f)
            )
        }

        // Beautiful Interactive Canvas Chart Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (selectedLanguage == "ar") "مؤشر النشاط الأسبوعي" else "Weekly Activity Trend",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (selectedLanguage == "ar") "تحليل وتيرة النسخ والحفظ التلقائي" else "Analysis of automated copying frequency",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Line graph
                WeeklyActivityGraph()
            }
        }

        // Type breakdown card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (selectedLanguage == "ar") "تصنيف محتويات الحافظة" else "Clipboard Content breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                val totalSum = (textCount + linkCount + codeCount).toFloat()
                val textPercent = if (totalSum > 0) textCount / totalSum else 0f
                val linkPercent = if (totalSum > 0) linkCount / totalSum else 0f
                val codePercent = if (totalSum > 0) codeCount / totalSum else 0f

                BreakdownRow(
                    label = L10n.get("filterText", selectedLanguage),
                    count = textCount,
                    percent = textPercent,
                    color = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BreakdownRow(
                    label = L10n.get("filterLink", selectedLanguage),
                    count = linkCount,
                    percent = linkPercent,
                    color = Color(0xFFFF3B30)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BreakdownRow(
                    label = L10n.get("filterCode", selectedLanguage),
                    count = codeCount,
                    percent = codePercent,
                    color = Color(0xFFD0BCFF)
                )
            }
        }

        // Optimization score or tips banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE53935).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflineBolt,
                        contentDescription = null,
                        tint = Color(0xFFE53935)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (selectedLanguage == "ar") "توفير الموارد ومساحة التخزين" else "Storage & Resource Optimization",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedLanguage == "ar") "التطبيق يعمل محليًا بالكامل بدون استهلاك للإنترنت أو البطارية." else "App runs fully offline, saving cellular data and battery life.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun StatsGridCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFF8B0000), RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(gradientColors.first().copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = gradientColors.first(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun BreakdownRow(
    label: String,
    count: Int,
    percent: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 13.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
            Text(text = "$count (${(percent * 100).toInt()}%)", fontSize = 13.sp, color = Color.LightGray)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF8B0000), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (percent > 0f) percent else 0.01f)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun WeeklyActivityGraph() {
    val points = listOf(15f, 32f, 24f, 45f, 38f, 55f, 48f) // Simulated data points
    val transitionState = remember { MutableTransitionState(0f) }
    LaunchedEffect(Unit) {
        transitionState.targetState = 1f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = transitionState.targetState,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = ""
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        val padding = 10f

        val maxVal = points.maxOrNull() ?: 100f
        val xStep = (width - padding * 2) / (points.size - 1)

        val strokePath = Path().apply {
            val startX = padding
            val startY = height - padding - ((points[0] / maxVal) * (height - padding * 2) * animatedProgress)
            moveTo(startX, startY)
            for (i in 1 until points.size) {
                val nextX = padding + i * xStep
                val nextY = height - padding - ((points[i] / maxVal) * (height - padding * 2) * animatedProgress)
                lineTo(nextX, nextY)
            }
        }

        val fillPath = Path().apply {
            val startX = padding
            val startY = height - padding - ((points[0] / maxVal) * (height - padding * 2) * animatedProgress)
            moveTo(startX, height - padding)
            lineTo(startX, startY)
            for (i in 1 until points.size) {
                val nextX = padding + i * xStep
                val nextY = height - padding - ((points[i] / maxVal) * (height - padding * 2) * animatedProgress)
                lineTo(nextX, nextY)
            }
            lineTo(padding + (points.size - 1) * xStep, height - padding)
            close()
        }

        // Draw background horizontal grid lines
        val gridCount = 3
        for (i in 0..gridCount) {
            val y = padding + i * (height - padding * 2) / gridCount
            drawLine(
                color = Color(0xFF8B0000).copy(alpha = 0.5f),
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 2f
            )
        }

        // Draw filled gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE53935).copy(alpha = 0.25f),
                    Color(0xFFFF3B30).copy(alpha = 0.0f)
                )
            )
        )

        // Draw line stroke
        drawPath(
            path = strokePath,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFE53935), Color(0xFFFF3B30))
            ),
            style = Stroke(width = 6f)
        )

        // Draw glowing nodes
        for (i in points.indices) {
            val x = padding + i * xStep
            val y = height - padding - ((points[i] / maxVal) * (height - padding * 2) * animatedProgress)
            drawCircle(
                color = Color(0xFF0D0D0D),
                radius = 10f,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color(0xFFE53935),
                radius = 6f,
                center = Offset(x, y)
            )
        }
    }
}
