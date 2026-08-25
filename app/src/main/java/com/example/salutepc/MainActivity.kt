package com.example.salutepc

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etMac: EditText
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var etToken: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etMac = findViewById(R.id.etMac)
        etIp = findViewById(R.id.etIp)
        etPort = findViewById(R.id.etPort)
        etToken = findViewById(R.id.etToken)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        etMac.setText(prefs.getString("mac", ""))
        etIp.setText(prefs.getString("ip", ""))
        etPort.setText(prefs.getString("port", "8765"))
        etToken.setText(prefs.getString("token", ""))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putString("mac", etMac.text.toString().trim())
                .putString("ip", etIp.text.toString().trim())
                .putString("port", etPort.text.toString().trim())
                .putString("token", etToken.text.toString().trim())
                .apply()
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Статус: настройки сохранены"
        }

        findViewById<Button>(R.id.btnNotificationAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnTestOn).setOnClickListener {
            val mac = etMac.text.toString().trim()
            if (mac.isBlank()) {
                Toast.makeText(this, "Укажи MAC-адрес", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    WakeOnLan.send(mac)
                    runOnUiThread {
                        Toast.makeText(this, "WoL отправлен", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Статус: WoL отправлен на $mac"
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        findViewById<Button>(R.id.btnTestOff).setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim().ifBlank { "8765" }
            val token = etToken.text.toString().trim()
            if (ip.isBlank()) {
                Toast.makeText(this, "Укажи IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    val url = java.net.URL("http://$ip:$port/shutdown")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.outputStream.use { it.write("token=$token".toByteArray()) }
                    val code = conn.responseCode
                    conn.disconnect()
                    runOnUiThread {
                        Toast.makeText(this, "Ответ сервера: $code", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Статус: выключение отправлено ($code)"
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }
}
