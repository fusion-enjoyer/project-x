package com.ekosistem.notlar

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle

/**
 * Yalnızca debug: emülatörde widget'ı sürüklemeden eklemek için.
 *
 *   adb shell am start -n com.ekosistem.notlar/.WidgetTestActivity --es saglayici GorevlerWidget
 *
 * Başlatıcı "Ana ekrana eklensin mi?" diye sorar (Android 8+).
 */
class WidgetTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ad = intent.getStringExtra("saglayici")
        if (ad != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager.getInstance(this)
                .requestPinAppWidget(ComponentName(this, "$packageName.$ad"), null, null)
        }
        finish()
    }
}
