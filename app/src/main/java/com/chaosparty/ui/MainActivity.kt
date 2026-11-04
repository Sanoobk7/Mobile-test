package com.chaosparty.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.chaosparty.net.Discovery
import com.chaosparty.session.HostSession
import com.chaosparty.session.Sessions

class MainActivity : Activity() {
    private lateinit var nameInput: EditText
    private lateinit var nameErr: TextView
    private lateinit var stats: TextView
    private lateinit var avatar: AvatarView
    private val playButtons = ArrayList<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Audio.init(this)
        val root = UiKit.column(this, 22).apply { gravity = Gravity.CENTER_HORIZONTAL }

        root.addView(UiKit.title(this, "CHAOS PARTY", 38f), UiKit.lp(this, 18f))
        root.addView(UiKit.text(this, "Cò quay hỗn loạn • 2–6 người", 14f, Palette.DIM, center = true), UiKit.lp(this, 2f))

        avatar = AvatarView(this, Prefs.avatar, 2)
        root.addView(avatar, LinearLayout.LayoutParams(UiKit.dp(this, 120f), UiKit.dp(this, 120f)).apply { topMargin = UiKit.dp(this@MainActivity, 8f) })

        val card = UiKit.card(this)
        card.addView(UiKit.text(this, "Tên của bạn (bắt buộc)", 13f, Palette.DIM))
        nameInput = UiKit.input(this, "Nhập tên để chơi...", 16)
        nameInput.setText(Prefs.name)
        card.addView(nameInput, UiKit.lp(this, 6f))
        nameErr = UiKit.text(this, "", 13f, 0xFFFF8A8A.toInt())
        card.addView(nameErr, UiKit.lp(this, 4f))
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshName()
        })
        root.addView(card, UiKit.lp(this, 8f))

        fun play(label: String, color: Int, action: () -> Unit) {
            val b = UiKit.button(this, label, color) { if (saveName()) action() }
            playButtons.add(b)
            root.addView(b, UiKit.lp(this, 12f))
        }
        play("🤖  Chơi với Bot", Palette.ACCENT) { soloDialog() }
        play("📡  Tạo phòng LAN (Host)", 0xFF7C3AED.toInt()) { openLobby(true, null) }
        play("🔎  Vào phòng LAN (Join)", 0xFF2563EB.toInt()) { scanRooms() }

        val small = UiKit.row(this)
        small.addView(UiKit.button(this, "👕 Tủ đồ", 0xFF3F3558.toInt()) { startActivity(Intent(this, SkinActivity::class.java)) }.apply { textSize = 14f; maxLines = 1 }, UiKit.lp(this, 0f, 0, 1f))
        small.addView(UiKit.button(this, "📖 Luật", 0xFF3F3558.toInt()) { startActivity(Intent(this, RulesActivity::class.java)) }.apply { textSize = 14f; maxLines = 1 },
            UiKit.lp(this, 0f, 0, 1f).apply { leftMargin = UiKit.dp(this@MainActivity, 8f) })
        small.addView(UiKit.button(this, "⚙ Cài đặt", 0xFF3F3558.toInt()) { settingsDialog(this) }.apply { textSize = 14f; maxLines = 1 },
            UiKit.lp(this, 0f, 0, 1f).apply { leftMargin = UiKit.dp(this@MainActivity, 8f) })
        root.addView(small, UiKit.lp(this, 16f))

        stats = UiKit.text(this, "", 13f, Palette.DIM, center = true)
        root.addView(stats, UiKit.lp(this, 14f))

        val frame = android.widget.FrameLayout(this)
        frame.addView(ScrollView(this).apply { addView(root); isFillViewport = true })
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        frame.addView(UiKit.text(this, "v$version", 12f, Palette.DIM), android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END,
        ).apply { val m = UiKit.dp(this@MainActivity, 10f); setMargins(m, m, m, m) })
        setContentView(frame)
        refreshName()
    }

    override fun onResume() {
        super.onResume()
        Audio.music(0)
        avatar.skin = Prefs.avatar
        avatar.invalidate()
        stats.text = "Thắng ${Prefs.wins} • Đã chơi ${Prefs.games} trận"
        if (!Prefs.seenRules) {
            Prefs.seenRules = true
            AlertDialog.Builder(this)
                .setTitle("Chào mừng tới Chaos Party!")
                .setMessage("Nhập tên rồi chọn chế độ. Bạn có muốn đọc nhanh luật chơi không?")
                .setPositiveButton("Đọc luật") { _, _ -> startActivity(Intent(this, RulesActivity::class.java)) }
                .setNegativeButton("Để sau", null)
                .show()
        }
    }

    override fun onPause() {
        super.onPause()
        Audio.pauseMusic()
    }

    private fun refreshName() {
        val err = Prefs.nameError(nameInput.text.toString())
        nameErr.text = err ?: ""
        nameErr.visibility = if (err == null) android.view.View.GONE else android.view.View.VISIBLE
        playButtons.forEach { it.isEnabled = err == null }
    }

    private fun saveName(): Boolean {
        val n = nameInput.text.toString().trim()
        val err = Prefs.nameError(n)
        if (err != null) {
            nameErr.text = err
            nameInput.requestFocus()
            return false
        }
        Prefs.name = n
        return true
    }

    private fun soloDialog() {
        val col = UiKit.column(this, 18)
        col.addView(UiKit.text(this, "Số bot", 14f, Palette.DIM))
        var bots = 3
        var level = 1
        val botRow = UiKit.row(this)
        val botButtons = (1..5).map { n ->
            UiKit.button(this, "$n", 0xFF3F3558.toInt()) {}.also { b -> botRow.addView(b, UiKit.lp(this, 0f, 0, 1f).apply { leftMargin = UiKit.dp(this@MainActivity, 4f) }) }
        }
        col.addView(botRow, UiKit.lp(this, 6f))
        col.addView(UiKit.text(this, "Độ khó", 14f, Palette.DIM), UiKit.lp(this, 14f))
        val lvRow = UiKit.row(this)
        val lvButtons = listOf("Dễ", "Thường", "Khó").map { s ->
            UiKit.button(this, s, 0xFF3F3558.toInt()) {}.also { b -> lvRow.addView(b, UiKit.lp(this, 0f, 0, 1f).apply { leftMargin = UiKit.dp(this@MainActivity, 4f) }) }
        }
        col.addView(lvRow, UiKit.lp(this, 6f))
        fun paint() {
            botButtons.forEachIndexed { i, b -> b.background = UiKit.rounded(if (i + 1 == bots) Palette.ACCENT else 0xFF3F3558.toInt(), UiKit.dp(this, 14f).toFloat()) }
            lvButtons.forEachIndexed { i, b -> b.background = UiKit.rounded(if (i == level) Palette.ACCENT else 0xFF3F3558.toInt(), UiKit.dp(this, 14f).toFloat()) }
        }
        botButtons.forEachIndexed { i, b -> b.setOnClickListener { bots = i + 1; paint() } }
        lvButtons.forEachIndexed { i, b -> b.setOnClickListener { level = i; paint() } }
        paint()
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Chơi với Bot")
            .setView(col)
            .setPositiveButton("Bắt đầu") { _, _ ->
                val s = HostSession(Prefs.name, Prefs.avatar, Prefs.gun, online = false)
                repeat(bots) { s.addBot(level) }
                Sessions.current = s
                startActivity(Intent(this, GameActivity::class.java))
                s.startGame()
            }
            .setNegativeButton("Huỷ", null)
            .create()
        dlg.show()
    }

    private fun openLobby(host: Boolean, ip: String?) {
        startActivity(Intent(this, LobbyActivity::class.java).putExtra("host", host).putExtra("ip", ip))
    }

    private fun scanRooms() {
        val wait = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Đang tìm phòng...")
            .setMessage("Hai máy cần cùng Wi-Fi hoặc cùng hotspot.")
            .setCancelable(false)
            .show()
        Thread {
            val rooms = runCatching { Discovery.scan() }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                wait.dismiss()
                val b = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                    .setNeutralButton("Nhập IP") { _, _ -> askIp() }
                    .setNegativeButton("Tìm lại") { _, _ -> scanRooms() }
                if (rooms.isEmpty()) {
                    b.setTitle("Không thấy phòng nào").setMessage("Thử tìm lại, hoặc nhập IP hiện trong phòng của chủ phòng.")
                } else {
                    b.setTitle("Chọn phòng").setItems(rooms.map { "🎲 ${it.name}   (${it.ip})" }.toTypedArray()) { _, i -> openLobby(false, rooms[i].ip) }
                }
                b.show()
            }
        }.start()
    }

    private fun askIp() {
        val input = UiKit.input(this, "VD: 192.168.1.5", 40).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(Prefs.lastIp)
        }
        val box = UiKit.column(this, 18).apply { addView(input) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("IP của chủ phòng")
            .setView(box)
            .setPositiveButton("Vào") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    Prefs.lastIp = ip
                    openLobby(false, ip)
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    companion object {
        fun settingsDialog(a: Activity, onClose: (() -> Unit)? = null) {
            val items = arrayOf("Nhạc nền", "Âm thanh", "Rung")
            val checked = booleanArrayOf(Prefs.music, Prefs.sfx, Prefs.vibrate)
            AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog)
                .setTitle("Cài đặt")
                .setMultiChoiceItems(items, checked) { _, i, v ->
                    when (i) {
                        0 -> { Prefs.music = v; Audio.refreshMusic() }
                        1 -> Prefs.sfx = v
                        2 -> Prefs.vibrate = v
                    }
                }
                .setPositiveButton("Xong") { _, _ -> onClose?.invoke() }
                .setOnDismissListener { onClose?.invoke() }
                .show()
        }
    }
}
