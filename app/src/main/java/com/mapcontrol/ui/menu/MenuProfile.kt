package com.mapcontrol.ui.menu

import android.content.Context
import com.mapcontrol.trip.AppBackup

/** Yan menü sekme kimlikleri (MainActivity / SideRailBuilder ile aynı sayılar). */
object MenuTabs {
    const val WIFI = 0
    const val FILE = 1
    const val PROJECTION = 3
    const val APPS = 5
    const val DRIVE_MODE = 6
    const val SETTINGS = 7
    const val WELCOME_SOUND = 8
    const val VEHICLE_INFO = 9
    const val EQUALIZER = 11
    const val CLIMATE_AUTOMATION = 12
    const val FACE_RECOGNITION = 13
    const val CAMERA_360 = 14
    const val SPLIT_SCREEN = 15
    const val SUNROOF_AUTOMATION = 16
    const val HOME_SCREEN = 17
}

/**
 * Kurulum anketindeki kartlar: kullanıcı seçtiği kartların menülerini görür. Sıra ankette gösterilen sıradır.
 * Kart adı prefs'e yazılır; yeniden adlandırılırsa eski seçim kaybolur.
 */
enum class MenuCard(val tabs: Set<Int>, val beta: Boolean = false) {
    LAUNCHER(setOf(MenuTabs.HOME_SCREEN)),
    MAP(setOf(MenuTabs.PROJECTION, MenuTabs.SPLIT_SCREEN)),
    SOUND(setOf(MenuTabs.EQUALIZER)),
    WELCOME(setOf(MenuTabs.WELCOME_SOUND)),
    AUTOMATION(setOf(MenuTabs.CLIMATE_AUTOMATION, MenuTabs.SUNROOF_AUTOMATION)),
    MEMORY(setOf(MenuTabs.DRIVE_MODE)),
    CAMERA(setOf(MenuTabs.CAMERA_360, MenuTabs.FACE_RECOGNITION), beta = true),
    TECH(setOf(MenuTabs.VEHICLE_INFO)),
}

/**
 * Menü görünürlüğü. Yalnız menü satırlarını gizler; hiçbir özelliğin ayarını değiştirmez.
 * Kayıt yoksa (güncellemeyle gelen kullanıcı, anketi atlayan) her şey görünür.
 */
object MenuProfile {

    const val KEY_CARDS = "menuCards"

    /** Ankete bakılmaksızın her zaman görünen menüler. */
    @JvmField
    val ALWAYS: Set<Int> = setOf(MenuTabs.WIFI, MenuTabs.APPS, MenuTabs.FILE, MenuTabs.SETTINGS)

    /** [cards] null: hepsi. Hiçbir karta ait olmayan sekmeler (ör. günlük) her zaman görünür. */
    @JvmStatic
    fun isVisible(tab: Int, cards: Set<MenuCard>?): Boolean {
        if (cards == null || tab in ALWAYS) return true
        val owners = MenuCard.values().filter { tab in it.tabs }
        return owners.isEmpty() || owners.any { it in cards }
    }

    /** Bütün kartlar seçildiyse "hepsi" (null) saklanır: sonradan eklenen menüler de görünür. */
    @JvmStatic
    fun normalize(cards: Set<MenuCard>?): Set<MenuCard>? =
        if (cards == null || cards.containsAll(MenuCard.values().toList())) null else cards

    @JvmStatic
    fun encode(cards: Set<MenuCard>): Set<String> = cards.mapTo(LinkedHashSet()) { it.name }

    /** Bilinmeyen adlar (eski/yeni sürüm) yok sayılır. */
    @JvmStatic
    fun decode(raw: Set<String>?): Set<MenuCard>? =
        raw?.mapNotNullTo(LinkedHashSet()) { name -> MenuCard.values().firstOrNull { it.name == name } }

    @JvmStatic
    fun cards(context: Context): Set<MenuCard>? =
        decode(prefs(context).getStringSet(KEY_CARDS, null))

    @JvmStatic
    fun save(context: Context, cards: Set<MenuCard>?) {
        val normalized = normalize(cards)
        val edit = prefs(context).edit()
        if (normalized == null) edit.remove(KEY_CARDS) else edit.putStringSet(KEY_CARDS, encode(normalized))
        edit.apply()
    }

    @JvmStatic
    fun isTabVisible(context: Context, tab: Int): Boolean = isVisible(tab, cards(context))

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppBackup.FILE_MAIN, Context.MODE_PRIVATE)
}
