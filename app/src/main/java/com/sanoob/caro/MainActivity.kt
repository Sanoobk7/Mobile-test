package com.sanoob.caro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var nameInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("caro", MODE_PRIVATE)
        nameInput = findViewById(R.id.nameInput)
        nameInput.setText(prefs.getString("name", "Người chơi"))

        findViewById<Button>(R.id.btnAi).setOnClickListener { start(GameActivity.MODE_AI) }
        findViewById<Button>(R.id.btnLocal).setOnClickListener { start(GameActivity.MODE_LOCAL) }
        findViewById<Button>(R.id.btnHost).setOnClickListener { start(GameActivity.MODE_HOST) }
        findViewById<Button>(R.id.btnJoin).setOnClickListener { scanRooms() }
    }

    private fun playerName(): String {
        val name = nameInput.text.toString().trim().ifEmpty { "Người chơi" }.take(20)
        getSharedPreferences("caro", MODE_PRIVATE).edit().putString("name", name).apply()
        return name
    }

    private fun start(mode: String, hostIp: String? = null) {
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_MODE, mode)
                .putExtra(GameActivity.EXTRA_NAME, playerName())
                .putExtra(GameActivity.EXTRA_HOST_IP, hostIp)
        )
    }

    private fun scanRooms() {
        val searching = AlertDialog.Builder(this)
            .setTitle("Đang tìm phòng…")
            .setMessage("Hãy chắc chắn hai máy cùng Wi-Fi hoặc cùng hotspot.")
            .setCancelable(false)
            .show()
        Thread {
            val rooms = runCatching { LanDiscovery.scan() }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                searching.dismiss()
                showRooms(rooms)
            }
        }.start()
    }

    private fun showRooms(rooms: List<LanDiscovery.Room>) {
        val labels = rooms.map { "${it.name}  (${it.ip})" }.toTypedArray()
        val b = AlertDialog.Builder(this)
            .setTitle(if (rooms.isEmpty()) "Không thấy phòng nào" else "Chọn phòng")
            .setNeutralButton("Nhập IP") { _, _ -> askIp() }
            .setNegativeButton("Tìm lại") { _, _ -> scanRooms() }
        if (rooms.isEmpty()) {
            b.setMessage("Bạn có thể thử lại, hoặc nhập IP hiện trên máy tạo phòng.")
        } else {
            b.setItems(labels) { _, i -> start(GameActivity.MODE_JOIN, rooms[i].ip) }
        }
        b.show()
    }

    private fun askIp() {
        val input = EditText(this).apply {
            hint = "VD: 192.168.1.5"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        AlertDialog.Builder(this)
            .setTitle("Nhập IP máy tạo phòng")
            .setView(input)
            .setPositiveButton("Vào") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isEmpty()) Toast.makeText(this, "Chưa nhập IP", Toast.LENGTH_SHORT).show()
                else start(GameActivity.MODE_JOIN, ip)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
