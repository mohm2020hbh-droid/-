package com.pogoascent.customization

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import com.pogoascent.levels.Progression
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.render.CharacterStyle
import com.pogoascent.render.HatKind
import com.pogoascent.render.Palette
import com.pogoascent.save.SaveManager
import kotlinx.serialization.Serializable

enum class ItemCategory(val label: String) {
  HAT("Hat"), STICK("Stick"), CLOTHES("Clothes"), SKIN("Skin"), BOOST_EFFECT("Boost Effect"), EMOTE("Pose / Emote"),
}

enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

/**
 * One cosmetic. Fields follow the task's data model: Item ID, Category, Price/Unlock, Prefab, Icon, Rarity
 * (the "Unlocked" flag lives in the save file). `unlock` is `default`, `coins`, `level:<levelId>` or `world:<worldId>`.
 */
@Serializable
data class ItemDef(
  val id: String,
  val category: ItemCategory,
  val name: String,
  val unlock: String,
  val price: Int = 0,
  /** Procedural model recipe id (see [Wardrobe.style]); no mesh files are shipped. */
  val prefab: String,
  /** A glyph shown in the wardrobe grid. */
  val icon: String,
  val rarity: Rarity = Rarity.COMMON,
  /** #RRGGBB colours: primary (and secondary where the prefab uses one). */
  val colors: List<String> = emptyList(),
)

@Serializable
data class ItemFile(val items: List<ItemDef>)

class ItemCatalog(val items: List<ItemDef>) {
  private val byId = items.associateBy { it.id }
  init {
    require(byId.size == items.size) { "duplicate item id" }
    for (c in ItemCategory.values()) require(items.any { it.category == c && it.unlock == "default" }) { "category $c needs at least one default item" }
  }
  operator fun get(id: String): ItemDef = byId[id] ?: error("Unknown item '$id'")
  fun find(id: String): ItemDef? = byId[id]
  fun inCategory(c: ItemCategory): List<ItemDef> = items.filter { it.category == c }
  fun defaultOf(c: ItemCategory): ItemDef = items.first { it.category == c && it.unlock == "default" }

  companion object {
    const val RESOURCE_PATH = "data/items.json"
    fun load(): ItemCatalog = ItemCatalog(GameJson.pretty.decodeFromString(ItemFile.serializer(), Resources.readText(RESOURCE_PATH)).items)
  }
}

enum class UnlockResult { UNLOCKED, ALREADY_OWNED, NOT_ENOUGH_COINS, LOCKED_BY_PROGRESS, UNKNOWN_ITEM }

/** Owns what the player has unlocked/equipped (stored in the save file) and turns it into a [CharacterStyle]. */
class Wardrobe(private val catalog: ItemCatalog, private val saves: SaveManager, private val worlds: WorldCatalog, private val progression: Progression) {
  fun isUnlocked(id: String): Boolean {
    val item = catalog.find(id) ?: return false
    return item.unlock == "default" || id in saves.data.unlockedItems
  }

  /** Grants progress-based items whose condition is now met (call after a level completes). Returns the newly granted ids. */
  fun grantProgressUnlocks(): List<String> {
    val granted = ArrayList<String>()
    for (item in catalog.items) {
      if (isUnlocked(item.id)) continue
      val met = when {
        item.unlock.startsWith("level:") -> progression.isCompleted(item.unlock.removePrefix("level:"))
        item.unlock.startsWith("world:") -> worlds.find(item.unlock.removePrefix("world:"))?.let { progression.isWorldUnlocked(it) } ?: false
        else -> false
      }
      if (met) granted += item.id
    }
    if (granted.isNotEmpty()) saves.update { it.copy(unlockedItems = it.unlockedItems + granted) }
    return granted
  }

  fun canAfford(id: String): Boolean = (catalog.find(id)?.price ?: Int.MAX_VALUE) <= saves.data.coins

  /** Buy a `coins` item. */
  fun purchase(id: String): UnlockResult {
    val item = catalog.find(id) ?: return UnlockResult.UNKNOWN_ITEM
    if (isUnlocked(id)) return UnlockResult.ALREADY_OWNED
    if (item.unlock != "coins") return UnlockResult.LOCKED_BY_PROGRESS
    if (saves.data.coins < item.price) return UnlockResult.NOT_ENOUGH_COINS
    saves.update { it.copy(coins = it.coins - item.price, unlockedItems = it.unlockedItems + id) }
    return UnlockResult.UNLOCKED
  }

  /** Equip an owned item (replaces the current item in its category). */
  fun equip(id: String): Boolean {
    val item = catalog.find(id) ?: return false
    if (!isUnlocked(id)) return false
    saves.update { it.copy(equipped = it.equipped + (item.category.name to id)) }
    return true
  }

  fun equipped(c: ItemCategory): ItemDef {
    val id = saves.data.equipped[c.name]
    val item = id?.let { catalog.find(it) }
    return if (item != null && isUnlocked(item.id)) item else catalog.defaultOf(c)
  }

  /** Resolve the equipped items into the renderer's style. */
  fun style(): CharacterStyle {
    fun color(item: ItemDef, i: Int = 0): FloatArray = Palette.parse(item.colors.getOrElse(i) { item.colors.firstOrNull() ?: "#FFFFFF" })
    val hat = equipped(ItemCategory.HAT)
    val stick = equipped(ItemCategory.STICK)
    val clothes = equipped(ItemCategory.CLOTHES)
    val skin = equipped(ItemCategory.SKIN)
    val trail = equipped(ItemCategory.BOOST_EFFECT)
    val emote = equipped(ItemCategory.EMOTE)
    return CharacterStyle(
      shirt = color(clothes, 0), pants = color(clothes, 1), skin = color(skin),
      stick = color(stick, 0), spring = color(stick, 1),
      hat = when (hat.prefab) {
        "hat_cap" -> HatKind.CAP; "hat_beanie" -> HatKind.BEANIE; "hat_helmet" -> HatKind.HELMET
        "hat_tophat" -> HatKind.TOPHAT; "hat_propeller" -> HatKind.PROPELLER; "hat_crown" -> HatKind.CROWN
        else -> HatKind.NONE
      },
      hatColor = color(hat), trail = color(trail),
      emote = emote.prefab.removePrefix("emote_"),
    )
  }
}
