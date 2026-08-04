package com.example.services

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.database.AppDatabase
import com.example.database.ClipboardEntity
import com.example.database.SnippetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class COPYKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var database: AppDatabase

    // Lifecycle implementation for ComposeView in Service context
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@COPYKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@COPYKeyboardService)
            
            setContent {
                var selectedTab by remember { mutableStateOf(0) }
                var clipboardList by remember { mutableStateOf(emptyList<ClipboardEntity>()) }
                var snippetsList by remember { mutableStateOf(emptyList<SnippetEntity>()) }

                LaunchedEffect(Unit) {
                    serviceScope.launch {
                        database.clipboardDao().getAllItems().collect { items ->
                            clipboardList = items.take(5)
                        }
                    }
                    serviceScope.launch {
                        database.snippetDao().getAllSnippets().collect { snippets ->
                            snippetsList = snippets
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    Column {
                        // Header Tab Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                TextButton(
                                    onClick = { selectedTab = 0 },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("الحافظة الأخيرة (5)", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { selectedTab = 1 },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("المقتطفات", fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Text(
                                text = "COPY KB",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // Tab Content
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(8.dp)
                        ) {
                            if (selectedTab == 0) {
                                if (clipboardList.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لا توجد عناصر منسوخة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        clipboardList.forEach { item ->
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ),
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        insertText(item.content)
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(12.dp)
                                                        .fillMaxSize(),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = item.content,
                                                        fontSize = 13.sp,
                                                        maxLines = 4,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = when (item.type.name) {
                                                            "LINK" -> "🔗 رابط"
                                                            "CODE" -> "💻 كود"
                                                            "IMAGE" -> "🖼️ صورة"
                                                            else -> "📝 نص"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (snippetsList.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لا توجد مقتطفات مضافة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        snippetsList.forEach { snippet ->
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                                ),
                                                modifier = Modifier
                                                    .width(150.dp)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        insertText(snippet.content)
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(12.dp)
                                                        .fillMaxSize(),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = snippet.name,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = snippet.content,
                                                            fontSize = 12.sp,
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                    if (!snippet.shortcut.isNullOrEmpty()) {
                                                        Text(
                                                            text = "اختصار: ${snippet.shortcut}",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rootLayout.addView(composeView)
        return rootLayout
    }

    private fun insertText(text: String) {
        val ic = currentInputConnection
        ic?.commitText(text, 1)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }
}
