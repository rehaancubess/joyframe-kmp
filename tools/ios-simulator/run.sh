#!/bin/sh
# Builds Lake Lab for the iOS simulator and launches it, without an Xcode project.
# Usage: tools/ios-simulator/run.sh [gradle args, e.g. -Pjoyframe.demoBoat=path/to/boat.glb]
# Uses the booted simulator; otherwise boots LAKELAB_SIMULATOR (default "iPhone 17 Pro").
set -eu
cd "$(dirname "$0")/../.."
./gradlew :sample:linkDebugFrameworkIosSimulatorArm64 :sample:assembleIosSimulatorArm64MainResources "$@"

FRAMEWORKS=sample/build/bin/iosSimulatorArm64/debugFramework
RESOURCES=sample/build/generated/compose/resourceGenerator/assembledResources/iosSimulatorArm64Main
APP=build/ios-simulator/LakeLab.app
rm -rf "$APP"
mkdir -p "$APP/compose-resources"
cp tools/ios-simulator/Info.plist "$APP/"
# Compose looks for resources under compose-resources/ in the app bundle.
cp -R "$RESOURCES/." "$APP/compose-resources/"

xcrun -sdk iphonesimulator swiftc -parse-as-library -module-name LakeLabHost -target arm64-apple-ios17.2-simulator \
    -F "$FRAMEWORKS" -framework LakeLab \
    -framework Metal -framework MetalKit -framework QuartzCore -framework CoreGraphics -framework CoreText \
    -framework GameController -framework AVFAudio -framework UIKit -framework Foundation -lc++ \
    -o "$APP/LakeLab" tools/ios-simulator/LakeLabApp.swift
codesign --force --sign - "$APP" >/dev/null

# Use the booted simulator, or boot one by name (several may share a name across runtimes).
if [ -n "${LAKELAB_SIMULATOR:-}" ] || ! xcrun simctl list devices booted | grep -q Booted; then
    xcrun simctl boot "${LAKELAB_SIMULATOR:-iPhone 17 Pro}" 2>/dev/null || true
fi
open -a Simulator
xcrun simctl terminate booted io.github.rehaancubess.joyframe.lakelab 2>/dev/null || true
xcrun simctl install booted "$APP"
xcrun simctl launch booted io.github.rehaancubess.joyframe.lakelab
