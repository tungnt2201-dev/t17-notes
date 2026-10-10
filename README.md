# T17 Notes

Ghi chú & đọc sách **mã hóa AES-256-GCM ngay trên máy** — không máy chủ, không gửi dữ liệu đi đâu.

- **Ghi chú** kiểu Evernote: sổ, thẻ, checklist, tìm không dấu, xuất `.enex` / Markdown / TXT / HTML.
- **Thư viện ebook**: lật trang 1–2 trang, 12 giao diện, phông, cỡ chữ, lề, giãn dòng; bookmark, highlight 5 màu, ghi chú.
- **Thư mục mã hóa**: thêm cả thư mục (tên thư mục / tên file dạng `D1_…`, `F1_…`) vào thư viện, hiện tên thật khi nhập đúng mật khẩu; mã hóa một thư mục ra ổ đĩa.
- Tương thích định dạng `T17ENC1` của T17 Studio / `encryp.py`.

## Dùng ngay
- **Web:** GitHub Pages của repo này (Settings → Pages → Source: *GitHub Actions*).
- **Android:** tab *Actions → Build APK* → tải artifact `T17Notes-apk`, hoặc mục *Releases* khi có tag `v*`.

## Cấu trúc
```
web/index.html              toàn bộ ứng dụng (1 file)
android/                    vỏ Android (WebView) — build không cần Gradle
  java/…/MainActivity.java  chọn/lưu file, chọn cả thư mục, chia sẻ, nút Back, chặn chụp màn hình
  build.sh                  javac → d8 → aapt2 → zipalign → apksigner
.github/workflows/          build-apk.yml (APK), pages.yml (web)
```

## Khóa ký APK (quan trọng)
Để bản mới **cài đè** được lên bản cũ (giữ nguyên ghi chú), mọi APK phải ký cùng một khóa.
Thêm 2 secret trong *Settings → Secrets and variables → Actions*:

| Secret | Giá trị |
|---|---|
| `T17_KEYSTORE_B64` | nội dung base64 của `t17-release.jks` (`base64 -w0 t17-release.jks`, Windows: `certutil -encode`) |
| `T17_KEYSTORE_PASS` | mật khẩu khóa |

Không có secret → workflow tự tạo khóa tạm (bản đó không cài đè được lên bản đã cài).
