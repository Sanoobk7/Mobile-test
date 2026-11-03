package com.chaosparty.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import com.chaosparty.engine.Action
import com.chaosparty.engine.Ev
import com.chaosparty.engine.GameView
import com.chaosparty.session.HostSession
import com.chaosparty.session.Session
import com.chaosparty.session.SessionListener
import com.chaosparty.session.Sessions

class GameActivity : Activity(), SessionListener, GameScreenHost {
    private var session: Session? = null
    private lateinit var screen: GameScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Audio.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val s = Sessions.current
        if (s == null) {
            finish()
            return
        }
        session = s
        newScreen()
        s.attach(this)
    }

    private fun newScreen() {
        screen = GameScreen(this, this, session?.online == true)
        setContentView(screen)
        UiKit.immersive(this)
    }

    override fun onResume() {
        super.onResume()
        UiKit.immersive(this)
        Audio.music(1)
    }

    override fun onPause() {
        super.onPause()
        Audio.pauseMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.detach(this)
        // A solo game ends with its screen; LAN sessions belong to the lobby.
        if (isFinishing && session?.online == false) Sessions.current = null
    }

    // ---------------------------------------------------------------- session events

    override fun onStart(you: Int) {
        // The host started a new game: fresh table.
        newScreen()
    }

    override fun onStep(events: List<Ev>, view: GameView) = screen.pushStep(events, view)
    override fun onEmote(player: Int, emote: Int) = screen.showEmote(player, emote)
    override fun onRejected(msg: String) = screen.showToast(msg)
    override fun onBackToLobby() = finish()

    override fun onClosed(reason: String) {
        if (isFinishing) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Kết thúc")
            .setMessage(reason)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    // ---------------------------------------------------------------- screen callbacks

    override fun sendAction(a: Action) {
        session?.act(a)
    }

    override fun sendEmote(e: Int) {
        session?.emote(e)
    }

    override fun openMenu() {
        val items = arrayOf("Tiếp tục", "Cài đặt", "Đổi bàn (skin)", "Rời trận")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Tạm dừng")
            .setItems(items) { _, i ->
                when (i) {
                    1 -> MainActivity.settingsDialog(this) { UiKit.immersive(this) }
                    2 -> {
                        Prefs.table = (Prefs.table + 1) % Skins.tables.size
                        while (Skins.table(Prefs.table).unlock > Prefs.wins) Prefs.table = (Prefs.table + 1) % Skins.tables.size
                        screen.tableSkinChanged()
                    }
                    3 -> confirmLeave()
                }
                UiKit.immersive(this)
            }
            .show()
    }

    private fun confirmLeave() {
        val s = session
        val msg = when {
            s is HostSession && s.online -> "Bạn là chủ phòng. Rời trận sẽ đóng phòng của mọi người."
            s?.online == true -> "Rời trận? Một bot sẽ chơi thay bạn."
            else -> "Rời trận?"
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setMessage(msg)
            .setPositiveButton("Rời") { _, _ -> leave() }
            .setNegativeButton("Ở lại", null)
            .show()
    }

    private fun leave() {
        val s = session
        if (s?.online == true) {
            // Leaving a LAN game also leaves the room.
            Sessions.current = null
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = confirmLeave()

    override fun gameOverButtons(): List<String> {
        val s = session
        return when {
            s is HostSession && !s.online -> listOf("Chơi lại", "Về menu")
            s is HostSession -> listOf("Ván mới", "Về phòng chờ", "Đóng phòng")
            else -> listOf("Về phòng chờ", "Rời phòng")
        }
    }

    override fun onGameOverButton(index: Int) {
        val s = session
        when {
            s is HostSession && !s.online -> if (index == 0) s.startGame() else finish()
            s is HostSession -> when (index) {
                0 -> s.startGame()
                1 -> s.backToLobby()
                else -> leave()
            }
            else -> if (index == 0) finish() else leave()
        }
    }

    override fun onGameOver(won: Boolean) {
        Prefs.games = Prefs.games + 1
        if (won) Prefs.wins = Prefs.wins + 1
    }
}
