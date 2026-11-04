package com.chaosparty.ui

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var p: SharedPreferences
    private var defaultRelay = ""

    fun init(ctx: Context) {
        if (!::p.isInitialized) {
            p = ctx.applicationContext.getSharedPreferences("chaos", Context.MODE_PRIVATE)
            defaultRelay = ctx.getString(com.chaosparty.R.string.relay_url)
        }
    }

    /** Address of the online relay server; empty when none is set up yet. */
    var relayUrl: String
        get() = (p.getString("relay", null) ?: defaultRelay).trim()
        set(v) = p.edit().putString("relay", v.trim()).apply()
    var lastCode: String
        get() = p.getString("code", "") ?: ""
        set(v) = p.edit().putString("code", v).apply()

    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v).apply()
    var wins: Int
        get() = p.getInt("wins", 0)
        set(v) = p.edit().putInt("wins", v).apply()
    var games: Int
        get() = p.getInt("games", 0)
        set(v) = p.edit().putInt("games", v).apply()
    var avatar: Int
        get() = p.getInt("avatar", 0)
        set(v) = p.edit().putInt("avatar", v).apply()
    var gun: Int
        get() = p.getInt("gun", 0)
        set(v) = p.edit().putInt("gun", v).apply()
    var table: Int
        get() = p.getInt("table", 0)
        set(v) = p.edit().putInt("table", v).apply()
    var music: Boolean
        get() = p.getBoolean("music", true)
        set(v) = p.edit().putBoolean("music", v).apply()
    var sfx: Boolean
        get() = p.getBoolean("sfx", true)
        set(v) = p.edit().putBoolean("sfx", v).apply()
    var vibrate: Boolean
        get() = p.getBoolean("vibrate", true)
        set(v) = p.edit().putBoolean("vibrate", v).apply()
    var lastIp: String
        get() = p.getString("ip", "") ?: ""
        set(v) = p.edit().putString("ip", v).apply()
    var seenRules: Boolean
        get() = p.getBoolean("seenRules", false)
        set(v) = p.edit().putBoolean("seenRules", v).apply()

    /** Validates a player name; returns an error message or null when OK. */
    fun nameError(n: String): String? = when {
        n.isBlank() -> "Bạn phải nhập tên để chơi"
        n.trim().length < 2 -> "Tên cần ít nhất 2 ký tự"
        n.trim().length > 16 -> "Tên tối đa 16 ký tự"
        else -> null
    }
}

class AvatarSkin(val name: String, val body: Int, val accessory: Int, val unlock: Int)
class GunSkin(val name: String, val body: Int, val accent: Int, val grip: Int, val unlock: Int)
class TableSkin(val name: String, val felt: Int, val feltDark: Int, val rim: Int, val unlock: Int)

object Skins {
    // accessory: 0 none, 1 cap, 2 party hat, 3 sunglasses, 4 headband, 5 crown, 6 horns, 7 halo, 8 top hat, 9 flower
    val avatars = listOf(
        AvatarSkin("Bánh Dẻo", 0xFFFFC857.toInt(), 0, 0),
        AvatarSkin("Bạc Hà", 0xFF6EE7B7.toInt(), 1, 0),
        AvatarSkin("Kẹo Dâu", 0xFFFF8FAB.toInt(), 2, 0),
        AvatarSkin("Ngầu Lòi", 0xFF7DD3FC.toInt(), 3, 1),
        AvatarSkin("Võ Sĩ", 0xFFFFA36C.toInt(), 4, 2),
        AvatarSkin("Hoàng Đế", 0xFFC4B5FD.toInt(), 5, 3),
        AvatarSkin("Quỷ Nhỏ", 0xFFF87171.toInt(), 6, 5),
        AvatarSkin("Thiên Thần", 0xFFF1F5F9.toInt(), 7, 8),
        AvatarSkin("Quý Ông", 0xFF94A3B8.toInt(), 8, 12),
        AvatarSkin("Hoa Hậu", 0xFFFDE68A.toInt(), 9, 20),
    )
    val guns = listOf(
        GunSkin("Thép", 0xFF9AA4B2.toInt(), 0xFF5B6472.toInt(), 0xFF6B4226.toInt(), 0),
        GunSkin("Vàng Ròng", 0xFFFFD23F.toInt(), 0xFFB8860B.toInt(), 0xFF3B2A12.toInt(), 1),
        GunSkin("Neon", 0xFF22D3EE.toInt(), 0xFFFF3D7F.toInt(), 0xFF1E1B4B.toInt(), 3),
        GunSkin("Kẹo Ngọt", 0xFFFFB3D9.toInt(), 0xFF7DD3FC.toInt(), 0xFFFFF1A8.toInt(), 5),
        GunSkin("Gỗ Mun", 0xFF8B5A2B.toInt(), 0xFF3E2A14.toInt(), 0xFF1C130A.toInt(), 10),
        GunSkin("Ngân Hà", 0xFF6D28D9.toInt(), 0xFFF0ABFC.toInt(), 0xFF0F0A2A.toInt(), 15),
    )
    val tables = listOf(
        TableSkin("Nỉ Xanh", 0xFF1F7A4D.toInt(), 0xFF135235.toInt(), 0xFF7A4A24.toInt(), 0),
        TableSkin("Nhung Đỏ", 0xFF9B1C31.toInt(), 0xFF5E0F1D.toInt(), 0xFF3A2414.toInt(), 0),
        TableSkin("Đêm Tím", 0xFF4C1D95.toInt(), 0xFF2E1065.toInt(), 0xFF1F1530.toInt(), 2),
        TableSkin("Biển Sâu", 0xFF0E6E8C.toInt(), 0xFF084457.toInt(), 0xFF5A6B7A.toInt(), 4),
        TableSkin("Hoàng Kim", 0xFF2B2B2B.toInt(), 0xFF141414.toInt(), 0xFFC9A227.toInt(), 7),
    )

    fun avatar(i: Int) = avatars[i.mod(avatars.size)]
    fun gun(i: Int) = guns[i.mod(guns.size)]
    fun table(i: Int) = tables[i.mod(tables.size)]
}
