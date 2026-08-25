package com.example.salutepc

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

class SaluteNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.os.Handler(mainLooper).post {
            Toast.makeText(applicationContext, "Слушатель уведомлений ПОДКЛЮЧЁН", Toast.LENGTH_LONG).show()
        }
        Log.d("SalutePC", "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("SalutePC", "Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val full = "$title | $text | $bigText | $subText"
        val fullLower = full.lowercase()

        // Показываем ВСЕ уведомления
        android.os.Handler(mainLooper).post {
            Toast.makeText(
                applicationContext,
                "Пакет: $packageName\n$full",
                Toast.LENGTH_LONG
            ).show()
        }

        Log.d("SalutePC", "Package: $packageName | Full: $full")

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        // Очень широкая проверка на Салют/Сбер
        val isInteresting = packageName.lowercase().let { pkg ->
            pkg.contains("salute") || pkg.contains("sber") || 
            pkg.contains("dialog") || pkg.contains("assistant") ||
            pkg.contains("smart") || pkg.contains("home")
        }

        if (!isInteresting && !fullLower.contains("компьютер")) return

        when {
            fullLower.contains("включи") && fullLower.contains("компьютер") -> {
                val mac = prefs.getString("mac", null)
                if (!mac.isNullOrBlank()) {
                    Thread {
                        try {
                            WakeOnLan.send(mac)
                            android.os.Handler(mainLooper).post {
                                Toast.makeText(applicationContext, "✅ WoL отправлен!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SalutePC", "WoL error", e)
                        }
                    }.start()
                }
            }
            fullLower.contains("выключи") && fullLower.contains("компьютер") -> {
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
                            conn.responseCode
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
