# Chaos Party

Game cò quay hỗn loạn cho Android. 2–6 người ngồi quanh bàn lục giác, chơi với bot hoặc với bạn bè qua mạng LAN/Wi-Fi.
APK chỉ khoảng 100 KB: hình vẽ bằng code, âm thanh và nhạc được tạo ra khi mở game lần đầu.

## Cài đặt
1. Tải `ChaosParty.apk` (mục **Actions → Build APK → Artifacts**, hoặc file đính kèm khi được gửi).
2. Mở file trên điện thoại, cho phép "Cài ứng dụng không rõ nguồn gốc".
3. Android 8.0 trở lên.

## Chơi LAN
- Tất cả máy cùng một Wi-Fi, hoặc bắt hotspot của một máy.
- Một người bấm **Tạo phòng LAN**. Người đó có thể thêm bot (dễ/thường/khó) vào ghế trống, hoặc mời người ra.
- Những người còn lại bấm **Vào phòng LAN**: game tự tìm phòng. Nếu không thấy thì nhập IP hiện trong phòng.
- Người rời giữa trận sẽ được bot chơi thay. Mỗi lượt có 45 giây.

## Luật tóm tắt
- **Đạn:** đỏ (-1 máu, hết lượt), đen (không sát thương; tự bắn mình thì được bắn tiếp), hồng (người trúng +1 máu, hết lượt).
- **Vòng quay 8 ô mỗi round:** 4 Bình thường, Mù màu, Điên loạn, Xoay vòng, Ma sói (ngụy trang thành Bình thường).
- **Đồ vật (4 ô):** Kim tiêm, Cái đục, Bom, Giáp, Kính lúp, Uno đảo chỗ, Adrenaline, Keo mờ ám.
- Luật đầy đủ có trong game (nút **Luật**).

## Build
```
./gradlew :app:assembleRelease      # APK
./gradlew :app:bundleRelease        # AAB để đăng CH Play
./gradlew :app:testDebugUnitTest --tests 'com.chaosparty.engine.*'
```
Để ký bản phát hành, đặt `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) ở thư mục gốc,
hoặc thêm các secret `CP_KEYSTORE_B64`, `CP_KEYSTORE_PASSWORD`, `CP_KEY_ALIAS`, `CP_KEY_PASSWORD` cho GitHub Actions.
Nếu không có, APK được ký bằng khoá debug (cài được, nhưng không đăng CH Play được).

## Cấu trúc
- `engine/`: toàn bộ luật chơi và bot, thuần Kotlin, có test giả lập 3000 trận.
- `net/`, `session/`: mạng LAN (TCP + dò phòng bằng UDP broadcast). Host giữ luật; mỗi máy chỉ nhận thông tin được phép thấy.
- `ui/`: màn hình vẽ bằng Canvas, âm thanh tổng hợp, skin.
