package com.example.salutepc

import android.app.Notification
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
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

    private val handler = Handler(Looper.getMainLooper())
    private var rebindAttempts = 0

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebindAttempts = 0
        Log.d(TAG, "Listener connected")
        showToast("Слушатель ПОДКЛЮЧЁН")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
        showToast("Слушатель отключился, переподключаем...")

        // Пробуем переподключиться несколько раз с задержкой
        scheduleRebind()
    }

    private fun scheduleRebind() {
        if (rebindAttempts >= 5) {
            Log.e(TAG, "Слишком много попыток переподключения")
            return
        }

        rebindAttempts++
        val delay = (rebindAttempts * 2000).toLong() // 2, 4, 6, 8, 10 сек

        handler.postDelayed({
            try {
                Log.d(TAG, "requestRebind attempt $rebindAttempts")
                requestRebind(ComponentName(this, SaluteNotificationListener::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "requestRebind error", e)
            }
        }, delay)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName ?: return

            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            val full = listOf(title, text, bigText, subText)
                .filter { it.isNotBlank() }
                .joinToString(" | ")

            val fullLower = full.lowercase()

            Log.d(TAG, "[$packageName] $full")

            val containsComputer = fullLower.contains("компьютер") ||
                    fullLower.contains("комп") ||
                    fullLower.contains("пк")

            // Показываем Toast только если есть слово про компьютер
            if (containsComputer) {
                showToast("Уведомление:\n$full")
            } else {
                return
            }

            val prefs = getSharedPreferences("settings", MODE_PRIVATE)

            when {
                fullLower.contains("включи") || fullLower.contains("включ") -> {
                    val mac = prefs.getString("mac", null)?.trim()
                    if (mac.isNullOrBlank()) {
                        showToast("MAC не задан")
                        return
                    }

                    Thread {
                        try {
                            // Основной broadcast
                            WakeOnLan.send(mac)

                            // Дополнительный, если указан
                            val broadcast = prefs.getString("broadcast", null)?.trim()
                            if (!broadcast.isNullOrBlank()) {
                                WakeOnLan.send(mac, broadcast)
                            }

                            showToast("✅ WoL отправлен")
                            Log.d(TAG, "WoL sent to $mac")
                        } catch (e: Exception) {
                            Log.e(TAG, "WoL error", e)
                            showToast("Ошибка WoL: ${e.message}")
                        }
                    }.start()
                }

                fullLower.contains("выключи") || fullLower.contains("выключ") -> {
                    val ip = prefs.getString("ip", null)?.trim()
                    val port = prefs.getString("port", "8765")?.trim() ?: "8765"
                    val token = prefs.getString("token", "") ?: ""

                    if (ip.isNullOrBlank()) {
                        showToast("IP не задан")
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

                            showToast("Выключение отправлено ($code)")
                            Log.d(TAG, "Shutdown code: $code")
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
        handler.post {
            try {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
    }
}
