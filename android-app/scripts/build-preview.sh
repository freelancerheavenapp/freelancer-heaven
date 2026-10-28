#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_ROOT="$PWD"
SDK="${ANDROID_HOME:-/workspace/.android-sdk}"
BT="$SDK/build-tools/36.0.0"
PLATFORM="$SDK/platforms/android-36/android.jar"
OUT="$TASK_ROOT/build/package"
mkdir -p "$OUT"/{compiled,gen,classes,dex,base}
test -f app/src/main/assets/timer-metronome.glb
test -f app/src/main/assets/metronome-static.png
for ART in filament-android gltfio-android; do
  mkdir -p "vendor/$ART"
  if [ ! -f "vendor/$ART/classes.jar" ]; then
    curl -fsSL "https://repo.maven.apache.org/maven2/com/google/android/filament/$ART/1.56.0/$ART-1.56.0.aar" -o "vendor/$ART.aar"
    unzip -qo "vendor/$ART.aar" -d "vendor/$ART"
  fi
done
CP="$TASK_ROOT/vendor/filament-android/classes.jar:$TASK_ROOT/vendor/gltfio-android/classes.jar"
"$BT/aapt2" compile --dir app/src/main/res -o "$OUT/compiled/res.zip"
"$BT/aapt2" link --proto-format -I "$PLATFORM" --manifest app/src/main/AndroidManifest.xml -A app/src/main/assets --java "$OUT/gen" -o "$OUT/proto.zip" "$OUT/compiled/res.zip"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
java -jar /workspace/.tools/ecj.jar -1.8 -proc:none -bootclasspath "$PLATFORM" -classpath "$CP" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt" vendor/filament-android/classes.jar vendor/gltfio-android/classes.jar
cd "$OUT/base"
unzip -qo ../proto.zip
mkdir -p manifest dex lib
mv AndroidManifest.xml manifest/AndroidManifest.xml
cp ../dex/*.dex dex/
cp -a "$TASK_ROOT/vendor/filament-android/jni/." lib/
cp -a "$TASK_ROOT/vendor/gltfio-android/jni/." lib/
zip -qr ../base.zip .
java -jar /workspace/.tools/bundletool.jar build-bundle --modules="$OUT/base.zip" --output="$TASK_ROOT/build/Freelancer-Time-Tracker-UI3D.aab" --overwrite
java -jar /workspace/.tools/bundletool.jar validate --bundle="$TASK_ROOT/build/Freelancer-Time-Tracker-UI3D.aab"
