package com.mapcontrol.launcher

import android.content.Context
import android.content.SharedPreferences
import com.mapcontrol.trip.AppBackup

/**
 * Uygulama tepsisi: kullanıcı uygulamaları her zaman görünür, sistem uygulamalarını kullanıcı kendisi ekler.
 * Eklenenler `|` ile ayrılmış paket listesidir ve `AppBackup` yedeğine (`launcher.quickApps`) dahildir.
 */
internal object TrayApps {
    /** `AppBackup` en fazla bu kadar paket kabul eder; fazlası yedeğin bu alanını geçersiz kılar. */
    const val MAX_ADDED = 35
    private const val KEY = "launcher_quick_apps"
    private const val SEPARATOR = "|"

    fun parse(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }.distinct()

    fun encode(packages: List<String>): String = packages.joinToString(SEPARATOR)

    fun add(packages: List<String>, pkg: String): List<String> =
        if (pkg in packages || packages.size >= MAX_ADDED) packages else packages + pkg

    fun remove(packages: List<String>, pkg: String): List<String> = packages - pkg

    /** Tepsideki satırlar: kullanıcı uygulamaları ve eklenen, hâlâ başlatılabilir sistem uygulamaları. */
    fun <T> visible(user: List<T>, system: List<T>, added: List<String>, pkg: (T) -> String): List<T> =
        user + system.filter { pkg(it) in added }

    /** Ekleme listesi: henüz eklenmemiş sistem uygulamaları; sınır dolduysa boş. */
    fun <T> addable(system: List<T>, added: List<String>, pkg: (T) -> String): List<T> =
        if (added.size >= MAX_ADDED) emptyList() else system.filter { pkg(it) !in added }

    fun load(context: Context): List<String> = parse(prefs(context).getString(KEY, null))

    fun save(context: Context, packages: List<String>) {
        prefs(context).edit().putString(KEY, encode(packages)).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(AppBackup.FILE_MAIN, Context.MODE_PRIVATE)
}
