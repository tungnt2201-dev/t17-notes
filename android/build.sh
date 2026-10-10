#!/usr/bin/env bash
# Build T17 Notes APK bằng Android SDK (không cần Gradle).
# Biến môi trường:
#   ANDROID_HOME        thư mục Android SDK (GitHub Actions có sẵn)
#   VERSION_CODE        số phiên bản (mặc định 2)
#   VERSION_NAME        tên phiên bản (mặc định 2.0.0)
#   KEYSTORE_FILE       file khóa ký (.jks)   — bỏ trống: tự tạo khóa tạm
#   KEYSTORE_PASS       mật khẩu khóa ký
#   KEY_ALIAS           alias (mặc định t17)
set -euo pipefail
cd "$(dirname "$0")"
API=34
BT=$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)
TOOLS="$ANDROID_HOME/build-tools/$BT"
JAR="$ANDROID_HOME/platforms/android-$API/android.jar"
[ -f "$JAR" ] || { echo "Thiếu $JAR"; exit 1; }
echo "build-tools $BT · android-$API"
VC=${VERSION_CODE:-2}; VN=${VERSION_NAME:-2.0.0}

rm -rf build && mkdir -p build/classes build/compiled build/apk/assets
cp ../web/index.html build/apk/assets/index.html

javac -source 8 -target 8 -encoding UTF-8 -nowarn -bootclasspath "$JAR" -d build/classes java/vn/t17/notes/MainActivity.java
"$TOOLS/d8" --min-api 24 --lib "$JAR" --output build $(find build/classes -name '*.class')

"$TOOLS/aapt2" compile --dir res -o build/compiled/res.zip
"$TOOLS/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 24 --target-sdk-version $API \
  --version-code "$VC" --version-name "$VN" -A build/apk/assets -o build/base.apk build/compiled/res.zip
(cd build && zip -q -j base.apk classes.dex)
"$TOOLS/zipalign" -f -p 4 build/base.apk build/aligned.apk

if [ -z "${KEYSTORE_FILE:-}" ]; then
  echo "⚠ Không có khóa ký — tạo khóa tạm (bản này KHÔNG cài đè được lên bản ký bằng khóa thật)."
  KEYSTORE_FILE=build/temp.jks; KEYSTORE_PASS=temp-pass-123; KEY_ALIAS=t17
  keytool -genkeypair -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -alias t17 \
    -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=T17 Notes temp" >/dev/null
fi
"$TOOLS/apksigner" sign --ks "$KEYSTORE_FILE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "${KEY_ALIAS:-t17}" \
  --out "build/T17Notes-$VN.apk" build/aligned.apk
"$TOOLS/apksigner" verify --print-certs "build/T17Notes-$VN.apk" | head -3
echo "Xong: android/build/T17Notes-$VN.apk"
