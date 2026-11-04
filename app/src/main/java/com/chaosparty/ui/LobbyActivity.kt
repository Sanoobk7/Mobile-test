package com.chaosparty.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.chaosparty.engine.MAX_PLAYERS
import com.chaosparty.net.Discovery
import com.chaosparty.session.ClientSession
import com.chaosparty.session.HostSession
import com.chaosparty.session.LobbySeat
import com.chaosparty.session.SessionListener
import com.chaosparty.session.Sessions

/** Waiting room for LAN games. The host adds bots, kicks players and starts the game. */
class LobbyActivity : Activity(), SessionListener {
    private lateinit var seatsBox: LinearLayout
    private lateinit var status: TextView
    private lateinit var codeView: TextView
    private lateinit var shareBtn: android.widget.Button
    private var host: HostSession? = null
    private var inGame = false
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val isHost = intent.getBooleanExtra("host", true)
        val online = intent.getBooleanExtra("online", false)
        val root = UiKit.column(this, 20)
        root.addView(UiKit.title(this, if (isHost) "PHÒNG CỦA BẠN" else "PHÒNG CHỜ", 26f), UiKit.lp(this, 8f))
        status = UiKit.text(this, "", 14f, Palette.DIM, center = true)
        root.addView(status, UiKit.lp(this, 6f))
        codeView = UiKit.title(this, "", 44f, Palette.GOLD).apply {
            letterSpacing = 0.15f
            visibility = android.view.View.GONE
            setTextIsSelectable(true)
        }
        root.addView(codeView, UiKit.lp(this, 4f))
        shareBtn = UiKit.button(this, "📤  Gửi mã cho bạn bè", 0xFF2563EB.toInt()) { shareCode() }.apply { visibility = android.view.View.GONE }
        root.addView(shareBtn, UiKit.lp(this, 6f))
        seatsBox = UiKit.column(this, 0)
        root.addView(seatsBox, UiKit.lp(this, 12f))

        if (isHost) {
            val s = HostSession(Prefs.name, Prefs.avatar, Prefs.gun, online = true, relayUrl = if (online) Prefs.relayUrl else null)
            host = s
            Sessions.current = s
            if (online) {
                status.text = "Đang tạo phòng online..."
                s.open { err -> onClosed(err) }
            } else {
                multicastLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                    ?.createMulticastLock("chaos")?.apply { setReferenceCounted(false); runCatching { acquire() } }
                s.open { err -> status.text = err }
                val ips = Discovery.localIps()
                status.text = if (ips.isEmpty()) "Chưa có Wi-Fi! Hãy bật Wi-Fi hoặc hotspot." else "IP phòng: ${ips.joinToString(" • ")}\nBạn bè chọn \"Vào phòng LAN\" để tìm phòng này."
            }

            val botRow = UiKit.row(this)
            listOf("+ Bot dễ" to 0, "+ Bot thường" to 1, "+ Bot khó" to 2).forEachIndexed { i, (label, lv) ->
                botRow.addView(UiKit.button(this, label, 0xFF3F3558.toInt()) { s.addBot(lv) }.apply { textSize = 14f },
                    UiKit.lp(this, 0f, 0, 1f).apply { if (i > 0) leftMargin = UiKit.dp(this@LobbyActivity, 6f) })
            }
            root.addView(botRow, UiKit.lp(this, 16f))
            root.addView(UiKit.button(this, "▶  BẮT ĐẦU", Palette.ACCENT) {
                if (s.seatCount < 2) {
                    status.text = "Cần ít nhất 2 người (thêm bot hoặc chờ bạn bè)"
                } else {
                    s.startGame()
                }
            }, UiKit.lp(this, 12f))
            root.addView(UiKit.text(this, "Chạm vào một người/bot để mời ra khỏi phòng.", 12f, Palette.DIM, center = true), UiKit.lp(this, 8f))
            s.attach(this)
            onLobby(s.lobbySeats(), 0)
        } else {
            val s = ClientSession(Prefs.name, Prefs.avatar, Prefs.gun)
            Sessions.current = s
            s.attach(this)
            val ok = { status.text = "Đã vào phòng • chờ chủ phòng bắt đầu" }
            if (online) {
                val code = intent.getStringExtra("code") ?: ""
                status.text = "Đang vào phòng $code..."
                s.connectOnline(Prefs.relayUrl, code, ok) { err -> onClosed(err) }
            } else {
                val ip = intent.getStringExtra("ip") ?: ""
                status.text = "Đang kết nối tới $ip..."
                s.connect(ip, ok) { err -> onClosed(err) }
            }
        }
        root.addView(UiKit.button(this, "Rời phòng", 0xFF3F3558.toInt()) { finish() }, UiKit.lp(this, 12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        Audio.music(0)
        if (inGame) {
            inGame = false
            if (Sessions.current == null) {
                // The player left the room from inside the game.
                finish()
                return
            }
            Sessions.current?.attach(this)
            val h = host
            if (h != null) onLobby(h.lobbySeats(), 0) else Sessions.current?.lastLobby?.let { onLobby(it.first, it.second) }
        }
    }

    override fun onLobby(seats: List<LobbySeat>, me: Int) {
        seatsBox.removeAllViews()
        for (i in 0 until MAX_PLAYERS) {
            val seat = seats.getOrNull(i)
            val row = UiKit.row(this).apply {
                background = UiKit.rounded(if (i == me && seat != null) 0xCC3A2466.toInt() else 0xCC1A1030.toInt(), UiKit.dp(this@LobbyActivity, 14f).toFloat())
                val p = UiKit.dp(this@LobbyActivity, 8f)
                setPadding(p, p, p, p)
            }
            if (seat != null) {
                row.addView(AvatarView(this, seat.avatar), LinearLayout.LayoutParams(UiKit.dp(this, 48f), UiKit.dp(this, 48f)))
                val label = (if (seat.host) "👑 " else if (seat.bot) "🤖 " else "🙂 ") + seat.name +
                    (if (seat.bot) "  (${listOf("dễ", "thường", "khó")[seat.level.coerceIn(0, 2)]})" else "") +
                    (if (i == me) "  • bạn" else "")
                row.addView(UiKit.text(this, label, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = UiKit.dp(this@LobbyActivity, 10f) })
                val h = host
                if (h != null && i > 0) row.setOnClickListener {
                    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                        .setMessage("Mời ${seat.name} ra khỏi phòng?")
                        .setPositiveButton("Mời ra") { _, _ -> h.removeSeat(i) }
                        .setNegativeButton("Huỷ", null).show()
                }
            } else {
                row.addView(UiKit.text(this, "   Ghế trống", 15f, Palette.DIM).apply { gravity = Gravity.CENTER_VERTICAL },
                    LinearLayout.LayoutParams(0, UiKit.dp(this, 48f), 1f))
            }
            seatsBox.addView(row, UiKit.lp(this, 6f))
        }
        if (host == null) status.text = "Đã vào phòng (${seats.size}/$MAX_PLAYERS) • chờ chủ phòng bắt đầu"
    }

    override fun onRoomCode(code: String) {
        codeView.text = code
        codeView.visibility = android.view.View.VISIBLE
        shareBtn.visibility = android.view.View.VISIBLE
        status.text = "Mã phòng online • bạn bè mở Chaos Party, chọn \"Nhập mã\""
    }

    override fun onRejected(msg: String) {
        status.text = msg
    }

    private fun shareCode() {
        val code = host?.roomCode ?: return
        val text = "Vào chơi Chaos Party với mình! Mở game → 🌐 Chơi online → Nhập mã: $code"
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Gửi mã phòng"))
        }
    }

    override fun onStart(you: Int) {
        if (inGame) return
        inGame = true
        Sessions.current?.detach(this)
        startActivity(Intent(this, GameActivity::class.java))
    }

    override fun onClosed(reason: String) {
        if (isFinishing) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Rời phòng")
            .setMessage(reason)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { multicastLock?.release() }
        Sessions.current?.detach(this)
        if (isFinishing) Sessions.current = null
    }
}
