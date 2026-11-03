package com.chaosparty.ui

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import com.chaosparty.engine.Item
import com.chaosparty.engine.RoundEvent

class RulesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.column(this, 20)
        root.addView(UiKit.title(this, "LUẬT CHƠI", 28f), UiKit.lp(this, 6f))
        fun h(s: String) = root.addView(UiKit.text(this, s, 18f, Palette.GOLD), UiKit.lp(this, 18f))
        fun t(s: String) = root.addView(UiKit.text(this, s, 15f), UiKit.lp(this, 6f))

        h("Mục tiêu")
        t("2–6 người ngồi quanh bàn lục giác, mỗi người 5 máu. Người cuối cùng còn sống thắng.")
        h("Mỗi round")
        t("• Vòng quay chọn ngẫu nhiên sự kiện của round.\n• Súng được nạp 3–8 viên, xáo trộn. Bảng trên cùng luôn hiện còn bao nhiêu viên mỗi màu.\n• Mỗi người lấy 2 món từ hộp đồ: cất vào túi (4 ô) hoặc vứt. Đã cất thì chỉ dùng mới mất.\n• Round kết thúc khi súng hết đạn.")
        h("Lượt của bạn")
        t("Dùng bao nhiêu đồ tuỳ thích, rồi chạm vào một người (hoặc chính bạn) và bấm BẮN. Lượt đi theo chiều kim đồng hồ.")
        h("3 loại đạn")
        t("🔴 Đỏ: người trúng mất 1 máu, hết lượt.\n⚫ Đen: không gây sát thương. Bắn người khác thì hết lượt; tự bắn mình thì được bắn tiếp.\n🩷 Hồng: người trúng hồi 1 máu (kể cả kẻ địch!), hết lượt.\n🛡 Giáp đỡ sát thương trước khi mất máu.")
        h("Sự kiện (vòng quay 8 ô)")
        t("• Bình thường ×4\n" + listOf(RoundEvent.COLORBLIND, RoundEvent.MADNESS, RoundEvent.ROTATION, RoundEvent.WEREWOLF)
            .joinToString("\n") { "• ${Texts.event(it)}: ${Texts.eventDesc(it)}" } +
            "\nMa sói luôn ngụy trang thành Bình thường trên vòng quay. Phiếu hoà thì chọn ngẫu nhiên. Tự bắn mình = tự bỏ phiếu cho mình.")
        h("Đồ vật")
        t(listOf(Item.SYRINGE, Item.CHISEL, Item.BOMB, Item.ARMOR, Item.MAGNIFIER, Item.UNO, Item.ADRENALINE, Item.GLUE)
            .joinToString("\n\n") { "• ${Texts.item(it)}: ${Texts.itemDesc(it)}" } +
            "\n\nTối đa 2 quả bom cùng tồn tại. Không ai biết bom còn bao lâu mới nổ. Bom nổ xong thì biến mất.")
        h("Mẹo")
        t("• Chạm giữ (hoặc chạm) vào ô đồ để xem mô tả.\n• Nút 😀 để gửi biểu cảm cho cả bàn.\n• Chơi LAN: các máy cùng Wi-Fi hoặc cùng hotspot của một máy. Chủ phòng có thể thêm bot vào ghế trống.\n• Người rời giữa trận sẽ được bot chơi thay.")
        root.addView(UiKit.button(this, "Đã hiểu!", Palette.ACCENT) { finish() }, UiKit.lp(this, 20f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
