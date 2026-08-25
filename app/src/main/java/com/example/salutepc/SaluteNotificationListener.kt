package com.example.salutepc

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class SaluteNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.lowercase()

        // Подставь актуальный пакет Салюта, если нужно
        val isSalute = packageName.contains("salute") ||
                packageName.contains("sber") ||
                packageName == "ru.sberbank.salute" ||
                packageName == "ru.sberdevices.salute"

        if (!isSalute) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val full = "$title $text $bigText".lowercase()
        Log.d("SalutePC", "Notification: $full")

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        when {
            full.contains("включи компьютер") -> {
                val mac = prefs.getString("mac", null)
                if (!mac.isNullOrBlank()) {
                    try {
                        WakeOnLan.send(mac)
                        Log.d("SalutePC", "WoL sent to $mac")
                    } catch (e: Exception) {
                        Log.e("SalutePC", "WoL error", e)
                    }
                }
            }
            full.contains("выключи компьютер") -> {
                val ip = prefs.getString("ip", null)
                val port = prefs.getString("port", "8765") ?: "8765"
                val token = prefs.getString("token", "") ?: ""
                if (!ip.isNullOrBlank()) {
                    Thread {
                        try {
                            val url = URL("http://$ip:$port/shutdown")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.doOutput = true
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            conn.outputStream.use { it.write("token=$token".toByteArray()) }
                            Log.d("SalutePC", "Shutdown response: ${conn.responseCode}")
                            conn.disconnect()
                        } catch (e: Exception) {
                            Log.e("SalutePC", "Shutdown error", e)
                        }
                    }.start()
                }
            }
        }
    }
}
