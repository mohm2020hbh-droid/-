package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.database.ClipboardEntity
import com.example.database.ClipboardType
import com.example.database.SnippetEntity
import com.example.repositories.ClipboardRepository
import com.example.repositories.SnippetRepository
import com.example.services.ClipboardForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AppScreen {
    object History : AppScreen()
    object Snippets : AppScreen()
    object Sync : AppScreen()
    object Stats : AppScreen()
    object Paywall : AppScreen()
    object Settings : AppScreen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    val clipboardRepository = ClipboardRepository(database.clipboardDao())
    val snippetRepository = SnippetRepository(database.snippetDao())

    // UI navigation state
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.History)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Clipboard History filter & search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<ClipboardType?>(null)
    val selectedTypeFilter: StateFlow<ClipboardType?> = _selectedTypeFilter.asStateFlow()

    // Combined Flow for Clipboard Items
    val clipboardItems: StateFlow<List<ClipboardEntity>> = combine(
        clipboardRepository.allItems,
        _searchQuery,
        _selectedTypeFilter
    ) { items, query, filter ->
        items.filter { item ->
            val matchesQuery = query.isEmpty() || item.content.contains(query, ignoreCase = true)
            val matchesFilter = filter == null || item.type == filter
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Snippets list with search
    private val _snippetQuery = MutableStateFlow("")
    val snippetQuery: StateFlow<String> = _snippetQuery.asStateFlow()

    val snippets: StateFlow<List<SnippetEntity>> = combine(
        snippetRepository.allSnippets,
        _snippetQuery
    ) { list, query ->
        if (query.isEmpty()) list else list.filter {
            it.name.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) || (it.shortcut?.contains(query, ignoreCase = true) == true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
    private val _retentionDays = MutableStateFlow(30)
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    private val _startOnBoot = MutableStateFlow(false)
    val startOnBoot: StateFlow<Boolean> = _startOnBoot.asStateFlow()

    private val _isForegroundServiceActive = MutableStateFlow(false)
    val isForegroundServiceActive: StateFlow<Boolean> = _isForegroundServiceActive.asStateFlow()

    // Language Preference State ("ar", "en", "system")
    private val _selectedLanguage = MutableStateFlow("system")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Reactive counts for Stats Dashboard
    val allClipboardCount: StateFlow<Int> = clipboardRepository.allItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allSnippetsCount: StateFlow<Int> = snippetRepository.allSnippets
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Premium status (0 = Free, 1 = Premium)
    private val _premiumStatus = MutableStateFlow(0)
    val premiumStatus: StateFlow<Int> = _premiumStatus.asStateFlow()

    // Sync state simulation
    private val _syncingState = MutableStateFlow("مستعد للربط")
    val syncingState: StateFlow<String> = _syncingState.asStateFlow()
    private val _pairedDevices = MutableStateFlow<List<String>>(emptyList())
    val pairedDevices: StateFlow<List<String>> = _pairedDevices.asStateFlow()

    init {
        // Load settings from SharedPrefs
        val prefs = context.getSharedPreferences("clipflow_prefs", Context.MODE_PRIVATE)
        _retentionDays.value = prefs.getInt("retention_days", 30)
        _startOnBoot.value = prefs.getBoolean("start_on_boot", false)
        _premiumStatus.value = prefs.getInt("premium_status", 0)
        _selectedLanguage.value = prefs.getString("selected_language", "system") ?: "system"
        _isForegroundServiceActive.value = ClipboardForegroundService.isServiceRunning

        viewModelScope.launch {
            // Cleanup expired entries
            clipboardRepository.cleanExpired()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: ClipboardType?) {
        _selectedTypeFilter.value = type
    }

    fun setSnippetQuery(query: String) {
        _snippetQuery.value = query
    }

    // Clipboard Actions
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("COPY", text)
        clipboard.setPrimaryClip(clip)
    }

    fun checkAndSaveClipboardOnResume() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            if (text.isNotEmpty()) {
                viewModelScope.launch {
                    val type = if (text.startsWith("http://") || text.startsWith("https://")) {
                        ClipboardType.LINK
                    } else if (text.length > 150 && (text.contains("{") || text.contains("class ") || text.contains("fun "))) {
                        ClipboardType.CODE
                    } else {
                        ClipboardType.TEXT
                    }
                    clipboardRepository.insertItem(
                        content = text,
                        type = type,
                        sourceApp = "فتح التطبيق",
                        retentionDays = _retentionDays.value
                    )
                }
            }
        }
    }

    fun togglePin(item: ClipboardEntity) {
        viewModelScope.launch {
            clipboardRepository.togglePin(item)
        }
    }

    fun deleteItem(item: ClipboardEntity) {
        viewModelScope.launch {
            clipboardRepository.deleteItem(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            clipboardRepository.deleteAll()
        }
    }

    fun deleteUnpinned() {
        viewModelScope.launch {
            clipboardRepository.deleteUnpinned()
        }
    }

    // Snippets Actions
    fun saveSnippet(name: String, content: String, shortcut: String?) {
        viewModelScope.launch {
            val entity = SnippetEntity(
                name = name,
                content = content,
                shortcut = if (shortcut?.trim()?.isEmpty() == true) null else shortcut,
                timestamp = System.currentTimeMillis()
            )
            snippetRepository.insert(entity)
        }
    }

    fun updateSnippet(snippet: SnippetEntity) {
        viewModelScope.launch {
            snippetRepository.update(snippet)
        }
    }

    fun deleteSnippet(snippet: SnippetEntity) {
        viewModelScope.launch {
            snippetRepository.delete(snippet)
        }
    }

    // Settings Actions
    fun setRetentionDays(days: Int) {
        _retentionDays.value = days
        val prefs = context.getSharedPreferences("clipflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("retention_days", days).apply()
    }

    fun toggleStartOnBoot(enable: Boolean) {
        _startOnBoot.value = enable
        val prefs = context.getSharedPreferences("clipflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("start_on_boot", enable).apply()
    }

    fun setSelectedLanguage(lang: String) {
        _selectedLanguage.value = lang
        val prefs = context.getSharedPreferences("clipflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_language", lang).apply()
    }

    fun toggleForegroundService(enable: Boolean) {
        _isForegroundServiceActive.value = enable
        val intent = Intent(context, ClipboardForegroundService::class.java)
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    fun refreshServiceStatus() {
        _isForegroundServiceActive.value = ClipboardForegroundService.isServiceRunning
    }

    // Premium Purchase Simulation
    fun purchasePremium(status: Int) {
        _premiumStatus.value = status
        val prefs = context.getSharedPreferences("clipflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("premium_status", status).apply()
    }

    fun restorePurchase() {
        // Mock restore
        purchasePremium(1)
    }

    // Sync Simulation Actions
    fun startNetworkSync() {
        viewModelScope.launch {
            _syncingState.value = "جاري البحث عن أجهزة..."
            kotlinx.coroutines.delay(2000)
            _syncingState.value = "تم الاقتران ومزامنة 4 عناصر مشفرة"
            _pairedDevices.value = listOf("Samsung Galaxy S24", "MacBook Pro M3")
        }
    }

    fun pairDeviceWithQR(code: String) {
        viewModelScope.launch {
            _syncingState.value = "جاري فك تشفير الاقتران..."
            kotlinx.coroutines.delay(1500)
            _syncingState.value = "تم الربط بنجاح مع: $code"
            _pairedDevices.value = _pairedDevices.value + code
        }
    }
}
