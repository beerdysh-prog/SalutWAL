package com.example.salutepc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    fun send(macAddress: String, broadcastIp: String = "255.255.255.255", port: Int = 9) {
        val macBytes = macAddress
            .split(":", "-")
            .map { it.trim().toInt(16).toByte() }
            .toByteArray()

        require(macBytes.size == 6) { "Неверный MAC-адрес" }

        val packetData = ByteArray(6 + 16 * 6)
        // 6 байт 0xFF
        for (i in 0 until 6) packetData[i] = 0xFF.toByte()
        // 16 повторений MAC
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packetData, 6 + i * 6, 6)
        }

        val address = InetAddress.getByName(broadcastIp)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(packetData, packetData.size, address, port))
        }
    }
}
