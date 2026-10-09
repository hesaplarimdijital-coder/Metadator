#!/usr/bin/env bash
# Starts an Android emulator with no window of its own and waits until it has booted.
#
# The device is made to look like what the app is meant for here: a display as wide as a
# monitor (1920 x 1080 at the density of one), with a keyboard that has real keys.
#
# Usage: start_emulator.sh [system image]
set -euo pipefail

sdk="${ANDROID_HOME:?the Android SDK is where ANDROID_HOME says}"
tools="$sdk/cmdline-tools/latest/bin"
image="${1:-system-images;android-35;google_apis;x86_64}"

# The licenses are accepted on the runner already; sdkmanager asks again all the same.
yes 2>/dev/null | "$tools/sdkmanager" --licenses >/dev/null 2>&1 || true
"$tools/sdkmanager" --install emulator platform-tools "$image" >/dev/null

echo no | "$tools/avdmanager" create avd --force --name smoke --package "$image" >/dev/null

config="$HOME/.android/avd/smoke.avd/config.ini"
for key in hw.lcd.width hw.lcd.height hw.lcd.density hw.keyboard hw.ramSize hw.cpu.ncore \
  disk.dataPartition.size; do
  sed -i "/^$key *=/d" "$config"
done
cat >> "$config" <<'CONFIG'
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=160
hw.keyboard=yes
hw.ramSize=3072
hw.cpu.ncore=2
disk.dataPartition.size=3G
CONFIG

nohup "$sdk/emulator/emulator" -avd smoke -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -camera-back none -camera-front none >emulator.log 2>&1 &

adb="$sdk/platform-tools/adb"
timeout 300 "$adb" wait-for-device
booted=""
for _ in $(seq 1 240); do
  booted="$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [ "$booted" = "1" ] && break
  sleep 2
done
if [ "$booted" != "1" ]; then
  echo "The emulator did not boot. What it said last:"
  tail -n 60 emulator.log
  exit 1
fi

# The screen stays on and unlocked, and no keyboard is drawn over the app: this device has keys.
"$adb" shell input keyevent 82
"$adb" shell wm dismiss-keyguard
"$adb" shell svc power stayon true
"$adb" shell settings put secure show_ime_with_hard_keyboard 0
# What the first minute after a boot is busy with would otherwise be taken for the app's slowness.
sleep 20
"$adb" shell getprop ro.build.fingerprint
"$adb" shell wm size
"$adb" shell wm density
