package com.example.salutepc

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

class SaluteNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SalutePC"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        showToast("Слушатель уведомлений ПОДКЛЮЧЁН")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener disconnected — пытаемся переподключиться")
        showToast("Слушатель отключился, переподключаем...")

        // Просим систему переподключить сервис
        try {
            requestRebind(ComponentName(this, SaluteNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName ?: return

            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val infoText = extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""

            val full = listOf(title, text, bigText, subText, infoText)
                .filter { it.isNotBlank() }
                .joinToString(" | ")

            val fullLower = full.lowercase()

            Log.d(TAG, "Notification from [$packageName]: $full")

            // Показываем Toast только для интересных уведомлений (чтобы не спамить)
            val isInterestingPackage = packageName.lowercase().let { pkg ->
                pkg.contains("salute") ||
                pkg.contains("sber") ||
                pkg.contains("dialog") ||
                pkg.contains("assistant") ||
                pkg.contains("smartapp") ||
                pkg.contains("iot")
            }

            val containsComputer = fullLower.contains("компьютер") ||
                    fullLower.contains("комп ") ||
                    fullLower.contains("пк")

            if (isInterestingPackage || containsComputer) {
                showToast("Уведомление: $packageName\n$full")
            }

            // Если нет слова "компьютер" — дальше не проверяем
            if (!containsComputer) return

            val prefs = getSharedPreferences("settings", MODE_PRIVATE)

            when {
                // Включение
                fullLower.contains("включи") || fullLower.contains("включ") -> {
                    val mac = prefs.getString("mac", null)?.trim()
                    if (mac.isNullOrBlank()) {
                        showToast("MAC-адрес не задан в настройках")
                        return
                    }

                    Thread {
                        try {
                            // Пробуем обычный broadcast и направленный (если указан)
                            WakeOnLan.send(mac)

                            val broadcast = prefs.getString("broadcast", null)?.trim()
                            if (!broadcast.isNullOrBlank() && broadcast != "255.255.255.255") {
                                WakeOnLan.send(mac, broadcast)
                            }

                            showToast("✅ WoL отправлен на $mac")
                            Log.d(TAG, "WoL successfully sent to $mac")
                        } catch (e: Exception) {
                            Log.e(TAG, "WoL error", e)
                            showToast("Ошибка WoL: ${e.message}")
                        }
                    }.start()
                }

                // Выключение
                fullLower.contains("выключи") || fullLower.contains("выключ") -> {
                    val ip = prefs.getString("ip", null)?.trim()
                    val port = prefs.getString("port", "8765")?.trim() ?: "8765"
                    val token = prefs.getString("token", "") ?: ""

                    if (ip.isNullOrBlank()) {
                        showToast("IP компьютера не задан")
                        return
                    }

                    Thread {
                        try {
                            val url = URL("http://$ip:$port/shutdown")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.doOutput = true
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            conn.outputStream.use { it.write("token=$token".toByteArray()) }

                            val code = conn.responseCode
                            conn.disconnect()

                            showToast("Выключение отправлено (код $code)")
                            Log.d(TAG, "Shutdown response code: $code")
                        } catch (e: Exception) {
                            Log.e(TAG, "Shutdown error", e)
                            showToast("Ошибка выключения: ${e.message}")
                        }
                    }.start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted error", e)
        }
    }

    private fun showToast(message: String) {
        android.os.Handler(mainLooper).post {
            try {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Toast error", e)
            }
        }
    }
}
