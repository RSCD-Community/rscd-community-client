#!/bin/bash
# Install the client into the desktop (macOS).
#
# Usage: ./install-mac.sh              install for the current user
#        ./install-mac.sh --uninstall  undo everything this script did
#
# Linux has install.sh, Windows has install.bat; macOS needs its own because
# only a real .app bundle can register the rscd:// link type here -- a
# CFBundleURLTypes entry has to live in an app's Info.plist, and a bare shell
# script has no Info.plist. This script builds that .app for you with
# jpackage (part of every JDK since 14, no extra download) rather than
# asking you to hand-edit one, and installs it into ~/Applications so
#   - a Start-Menu-equivalent entry appears (Launchpad / Spotlight)
#   - rscd:// links, like a community website's Join Now button, open it
#
# Building the .app takes a little while and needs a full JDK (17 or newer,
# for the URL-scheme feature specifically) -- a plain JRE cannot do this
# step. If that is not available, this script says so plainly rather than
# failing partway through.
#
# What this does NOT do: touch anything outside ~/Applications and macOS's
# per-user Launch Services registry, or ask for your password. Nothing here
# needs sudo.

set -u
cd "$(dirname "$0")"
HERE="$(pwd)"

APP_NAME="RSCD Community Client"
APPS_DIR="$HOME/Applications"
APP_PATH="$APPS_DIR/$APP_NAME.app"
LSREGISTER="/System/Library/Frameworks/CoreServices.framework/Versions/A/Frameworks/LaunchServices.framework/Versions/A/Support/lsregister"

if [ "$(uname -s)" != "Darwin" ]; then
   echo "This installer is for macOS. Linux: ./install.sh -- Windows: install.bat" >&2
   exit 1
fi

if [ "${1:-}" = "--uninstall" ]; then
   if [ -d "$APP_PATH" ]; then
      rm -rf "$APP_PATH"
      echo "Removed \"$APP_NAME\" from ~/Applications."
   else
      echo "Nothing installed at \"$APP_PATH\" -- nothing to remove."
   fi
   # Tell Launch Services to forget it immediately instead of waiting for its
   # own background rescan; harmless if the tool has moved in a future macOS.
   [ -x "$LSREGISTER" ] && "$LSREGISTER" -f -u "$APP_PATH" 2>/dev/null
   echo "The checkout itself (this folder) is untouched -- delete it if you want it gone too."
   exit 0
fi

if [ ! -f run.sh ] || [ ! -f manifest.txt ]; then
   echo "This does not look like a complete client checkout -- run.sh or" >&2
   echo "manifest.txt is missing beside this script. Clone or unzip the whole" >&2
   echo "repository and run this from inside it." >&2
   exit 1
fi

# --- Find a JDK, not just a JRE -------------------------------------------
# jpackage ships inside the JDK; a JRE-only install (common on end-user
# machines) does not have it at all.
if [ -n "${JDK:-}" ]; then
   JAVA_BIN="$JDK/bin/java"
   JPACKAGE_BIN="$JDK/bin/jpackage"
elif [ -n "${JAVA_HOME:-}" ]; then
   JAVA_BIN="$JAVA_HOME/bin/java"
   JPACKAGE_BIN="$JAVA_HOME/bin/jpackage"
else
   JAVA_BIN="java"
   JPACKAGE_BIN="jpackage"
fi

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
   echo "" >&2
   echo "This needs Java, and 'java' was not found on this machine." >&2
   echo "" >&2
   echo "  brew install --cask temurin" >&2
   echo "  (no Homebrew? download the JDK .pkg from https://adoptium.net --" >&2
   echo "   pick 'JDK', not 'JRE': the JRE cannot build the app bundle below)" >&2
   echo "" >&2
   echo "Then run ./install-mac.sh again." >&2
   exit 1
fi

if ! "$JAVA_BIN" -version >/dev/null 2>&1; then
   echo "" >&2
   echo "A 'java' command exists on this machine, but it does not work. This" >&2
   echo "usually means the placeholder Apple ships before any real JDK is" >&2
   echo "installed. Install one and run this again:" >&2
   echo "" >&2
   echo "  brew install --cask temurin" >&2
   echo "  (or the .pkg from https://adoptium.net)" >&2
   exit 1
fi

if ! command -v "$JPACKAGE_BIN" >/dev/null 2>&1; then
   echo "" >&2
   echo "Java is installed, but it is a JRE (Java Runtime) rather than a full" >&2
   echo "JDK -- jpackage, the tool that builds the .app bundle and registers" >&2
   echo "rscd:// links, only comes with the JDK." >&2
   echo "" >&2
   echo "  brew install --cask temurin" >&2
   echo "  (or download the 'JDK' -- not 'JRE' -- package from https://adoptium.net)" >&2
   echo "" >&2
   echo "In the meantime, ./run.sh still launches the client normally --" >&2
   echo "you just will not have clickable rscd:// links until this step runs." >&2
   exit 1
fi

# --mac-url-scheme was added to jpackage in JDK 17; earlier JDKs have
# jpackage but silently cannot register a URL scheme with it.
JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 | sed -n '1s/.*version "\{0,1\}\([0-9][0-9.]*\).*/\1/p' | sed 's/^1\.//; s/\..*//')
if ! [ "${JAVA_MAJOR:-0}" -ge 17 ] 2>/dev/null; then
   echo "" >&2
   echo "This Java is version ${JAVA_MAJOR:-unknown}, but registering rscd:// links needs" >&2
   echo "JDK 17 or newer (jpackage's --mac-url-scheme option arrived in 17)." >&2
   echo "" >&2
   echo "  brew install --cask temurin      (installs the current version)" >&2
   echo "  or pick 'JDK 17' or later at https://adoptium.net" >&2
   echo "" >&2
   echo "./run.sh works fine on this Java in the meantime; only the link" >&2
   echo "registration in this script needs the newer one." >&2
   exit 1
fi

# --- Build the jar if it is not there yet ----------------------------------
if [ ! -f rscd-client.jar ]; then
   if [ -x ./build.sh ]; then
      echo "rscd-client.jar is not built yet -- building it now (first run only)..."
      ./build.sh || exit 1
   else
      echo "rscd-client.jar is missing and build.sh is not here to make it --" >&2
      echo "this does not look like a complete checkout." >&2
      exit 1
   fi
fi

# --- Make a .icns from the shipped .png -------------------------------------
# jpackage on macOS requires .icns specifically; sips and iconutil are both
# part of every macOS install, so this needs nothing extra.
ICON_ICNS=""
if [ -f install/rscd-icon.png ] && command -v sips >/dev/null 2>&1 && command -v iconutil >/dev/null 2>&1; then
   ICONSET="$(mktemp -d)/rscd.iconset"
   mkdir -p "$ICONSET"
   for size in 16 32 128 256 512; do
      sips -z "$size" "$size" install/rscd-icon.png --out "$ICONSET/icon_${size}x${size}.png" >/dev/null 2>&1
      double=$((size * 2))
      sips -z "$double" "$double" install/rscd-icon.png --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null 2>&1
   done
   ICNS_OUT="$(mktemp -d)/rscd-icon.icns"
   if iconutil -c icns -o "$ICNS_OUT" "$ICONSET" >/dev/null 2>&1; then
      ICON_ICNS="$ICNS_OUT"
   fi
   rm -rf "$(dirname "$ICONSET")"
fi
if [ -z "$ICON_ICNS" ]; then
   echo "Note: could not build an app icon (sips/iconutil unavailable or failed)." >&2
   echo "Installing without a custom icon -- everything else still works." >&2
fi

# --- Build the .app with jpackage -------------------------------------------
mkdir -p "$APPS_DIR"
[ -d "$APP_PATH" ] && rm -rf "$APP_PATH"
BUILD_TMP="$(mktemp -d)"
JPACKAGE_ARGS=(
   --type app-image
   --name "$APP_NAME"
   --input "$HERE"
   --main-jar rscd-client.jar
   --main-class org.rscdaemon.client.mudclient
   --mac-url-scheme rscd
   --dest "$BUILD_TMP"
)
[ -n "$ICON_ICNS" ] && JPACKAGE_ARGS+=(--icon "$ICON_ICNS")

echo "Building \"$APP_NAME.app\" (this can take a minute)..."
if ! "$JPACKAGE_BIN" "${JPACKAGE_ARGS[@]}"; then
   echo "" >&2
   echo "jpackage failed -- see the message above for why. Nothing was" >&2
   echo "installed or changed." >&2
   rm -rf "$BUILD_TMP"
   exit 1
fi

mv "$BUILD_TMP/$APP_NAME.app" "$APP_PATH"
rm -rf "$BUILD_TMP"

# Ad-hoc code signing: an unsigned app jpackage produces is fine to run
# locally, but without even an ad-hoc signature Gatekeeper sometimes reports
# it as "damaged" instead of the normal unidentified-developer prompt. This
# signs it with no identity (self-signed, local-only) -- it does not submit
# anything to Apple and does not require a developer account.
if command -v codesign >/dev/null 2>&1; then
   codesign --force --deep --sign - "$APP_PATH" >/dev/null 2>&1
fi

# Nudge Launch Services to notice the new app and its URL scheme now,
# instead of waiting for its own background rescan.
[ -x "$LSREGISTER" ] && "$LSREGISTER" -f "$APP_PATH" 2>/dev/null

echo ""
echo "Installed \"$APP_NAME.app\" to ~/Applications."
echo "  - Find it in Launchpad or Spotlight, same as any other app."
echo "  - rscd:// links now open it -- try a Join Now link on a community"
echo "    website, or run:"
echo "      open 'rscd://127.0.0.1:43594/?name=Local%20Test'"
echo ""
echo "First launch: macOS will say the app is from an unidentified developer"
echo "(it was built locally, not downloaded from the App Store or notarized)."
echo "Right-click the app in Launchpad and choose Open once to allow it --"
echo "after that it opens normally, including from rscd:// links."
echo ""
echo "To undo: ./install-mac.sh --uninstall"
