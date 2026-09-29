package com.carom.game

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import com.carom.core.level.LevelRepository
import com.carom.core.level.LevelSource
import com.carom.core.progress.KeyValueStore
import com.carom.core.progress.Progress
import com.carom.core.progress.Settings

/** Long-lived game state shared by all screens: the level list, progress and settings. */
class GameApp(val levels: LevelRepository, store: KeyValueStore) {
    val progress = Progress(store, levels.ids)
    val settings = Settings(store)

    companion object {
        fun create(context: Context) = GameApp(
            LevelRepository(AssetLevelSource(context.assets)),
            PrefsStore(context.getSharedPreferences("carom", Context.MODE_PRIVATE)),
        )
    }
}

/** Levels are the JSON files in `assets/levels/`. Dropping in a new file adds a level. */
class AssetLevelSource(private val assets: AssetManager, private val dir: String = "levels") : LevelSource {
    override fun list(): List<String> =
        assets.list(dir).orEmpty().filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }

    override fun read(id: String): String = assets.open("$dir/$id.json").bufferedReader().use { it.readText() }
}

/** Saves to SharedPreferences; apply() writes asynchronously so saving never stalls a frame. */
class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
