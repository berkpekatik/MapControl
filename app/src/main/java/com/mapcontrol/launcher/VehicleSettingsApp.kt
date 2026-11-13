package com.mapcontrol.launcher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Display

/**
 * Aracın kendi "Araç ayarları" ekranı. Yeri araca göre değişir:
 *  - Tiggo 7: ayrı uygulama `com.desaysv.vehiclesetting` (SVVehicleSetting), normal launcher intent'iyle açılır.
 *  - Tiggo 8 (T28): ayrı uygulama yok; araç ayarları genel Ayarlar'ın (`com.desaysv.setting`) içinde,
 *    `com.desaysv.vehiclesetting.ACTION_VEHICLE_SETTING` action'ıyla açılır (OEM SVLauncher
 *    `Packages.SV_VEHICLE_SETTING_APP` / `SV_VEHICLE_SETTING_ACTION`).
 *
 * Önce Tiggo 7 yolu denenir, çözülmezse Tiggo 8 yolu. İkisi de yoksa [intent] null döner; çağıran düğmeyi gizler.
 */
object VehicleSettingsApp {
    private const val TAG = "VehicleSettingsApp"

    /** Tiggo 7: ayrı araç ayarları uygulaması. */
    const val STANDALONE_PACKAGE = "com.desaysv.vehiclesetting"

    /** Tiggo 8: araç ayarlarını barındıran genel Ayarlar uygulaması. */
    const val SETTING_PACKAGE = "com.desaysv.setting"
    const val ACTION_VEHICLE_SETTING = "com.desaysv.vehiclesetting.ACTION_VEHICLE_SETTING"

    /** Bu araçta araç ayarlarını açan intent; hiçbiri çözülmezse null. */
    fun intent(context: Context): Intent? {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(STANDALONE_PACKAGE)?.let { return it }
        val embedded = Intent(ACTION_VEHICLE_SETTING).setPackage(SETTING_PACKAGE)
        return if (pm.resolveActivity(embedded, 0) != null) embedded else null
    }

    fun isAvailable(context: Context): Boolean = intent(context) != null

    /** Ana ekranda açar; açılacak yer yoksa ya da başlatma reddedilirse false. */
    fun open(context: Context): Boolean {
        val intent = intent(context) ?: run {
            Log.w(TAG, "Araç ayarları bulunamadı ($STANDALONE_PACKAGE / $SETTING_PACKAGE#$ACTION_VEHICLE_SETTING)")
            return false
        }
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts.toBundle())
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Araç ayarları açılamadı: $intent", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Araç ayarları açılamadı: $intent", e)
            false
        }
    }
}
