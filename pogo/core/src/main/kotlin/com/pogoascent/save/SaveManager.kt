package com.pogoascent.save

import com.pogoascent.core.GameJson
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Where the save text lives. File on Android/JVM, in-memory in tests. */
interface SaveStorage {
  fun readMain(): String?
  fun readBackup(): String?
  /** Persist [text] atomically: after this returns, either the old or the new content is on disk – never a torn file. */
  fun write(text: String)
  fun delete()
}

class MemorySaveStorage : SaveStorage {
  var main: String? = null
  var backup: String? = null
  override fun readMain() = main
  override fun readBackup() = backup
  override fun write(text: String) { if (main != null) backup = main; main = text }
  override fun delete() { main = null; backup = null }
}

/** java.io only (no java.nio.file: unavailable below Android 8). Write temp → fsync → rotate old to .bak → rename. */
class FileSaveStorage(private val file: File) : SaveStorage {
  private val tmp = File(file.path + ".tmp")
  private val bak = File(file.path + ".bak")

  override fun readMain(): String? = readOrNull(file)
  override fun readBackup(): String? = readOrNull(bak)

  private fun readOrNull(f: File): String? = try {
    if (f.exists()) f.readText(Charsets.UTF_8) else null
  } catch (e: Exception) { null }

  override fun write(text: String) {
    file.parentFile?.mkdirs()
    FileOutputStream(tmp).use { out ->
      out.write(text.toByteArray(Charsets.UTF_8))
      out.flush()
      out.fd.sync()
    }
    if (file.exists()) {
      bak.delete()
      file.renameTo(bak)
    }
    if (!tmp.renameTo(file)) {
      // very rare (cross-device): fall back to copy
      file.writeText(text, Charsets.UTF_8)
      tmp.delete()
    }
  }

  override fun delete() { file.delete(); tmp.delete(); bak.delete() }
}

/** Upgrades older save JSON to [SaveData.CURRENT_VERSION]. Add a branch per version bump. */
object SaveMigrator {
  fun migrate(root: JsonObject): JsonObject {
    var obj = root
    var version = obj["version"]?.jsonPrimitive?.int ?: 0
    if (version < 1) {
      // v0 → v1: files written before versioning existed carried no "version" key.
      obj = JsonObject(obj + ("version" to JsonPrimitive(1)))
      version = 1
    }
    return obj
  }
}

enum class LoadOutcome { LOADED, FRESH, RECOVERED_FROM_BACKUP, CORRUPT_RESET }

/**
 * Owns the in-memory [SaveData] and writes it back. Never throws on bad data: a corrupt main file falls back to the backup,
 * and if that fails too the game starts fresh (the broken text is kept as `corruptText` for support/debugging).
 */
class SaveManager(private val storage: SaveStorage) {
  var data: SaveData = SaveData(); private set
  var corruptText: String? = null; private set
  var lastLoad: LoadOutcome = LoadOutcome.FRESH; private set

  fun load(): LoadOutcome {
    val main = storage.readMain()
    if (main == null) {
      val bak = storage.readBackup()
      if (bak != null) {
        val parsed = parse(bak)
        if (parsed != null) { data = parsed; lastLoad = LoadOutcome.RECOVERED_FROM_BACKUP; return lastLoad }
      }
      data = SaveData(); lastLoad = LoadOutcome.FRESH; return lastLoad
    }
    val parsed = parse(main)
    if (parsed != null) { data = parsed; lastLoad = LoadOutcome.LOADED; return lastLoad }
    corruptText = main
    val bak = storage.readBackup()?.let { parse(it) }
    if (bak != null) { data = bak; lastLoad = LoadOutcome.RECOVERED_FROM_BACKUP; return lastLoad }
    data = SaveData(); lastLoad = LoadOutcome.CORRUPT_RESET
    return lastLoad
  }

  private fun parse(text: String): SaveData? = try {
    val migrated = SaveMigrator.migrate(GameJson.compact.parseToJsonElement(text).jsonObject)
    val d = GameJson.compact.decodeFromJsonElement(SaveData.serializer(), migrated)
    d.copy(settings = d.settings.sanitized())
  } catch (e: Exception) { null }

  fun save() { storage.write(GameJson.compact.encodeToString(SaveData.serializer(), data)) }

  /** Atomic update helper: mutate, then persist. */
  fun update(save: Boolean = true, block: (SaveData) -> SaveData) {
    data = block(data)
    if (save) save()
  }

  fun reset() { storage.delete(); data = SaveData() }
}
