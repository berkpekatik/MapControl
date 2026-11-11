package com.mapcontrol.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mapcontrol.launcher.HomeKey

/**
 * Uygulama simgesinin arayüzsüz girişi: launcher modu açıksa ana ekranımız, kapalıysa normal uygulama açılır. Karar burada
 * verildiği için ayarların açılış gradyanı yalnızca ayarlar gerçekten açılırken görünür.
 */
class AppEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!HomeKey.openLauncherOnAppStart(this, intent, false, false)) {
            // Düz istek: görevin kök isteğiyle eşleşir, açık uygulama yeni örnek kurulmadan öne gelir.
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
