#!/bin/bash
# Launch the desktop client.
#
# Usage: ./run.sh [settings.ini]
#
# Everything is relative to this script, so a fresh git clone runs without
# editing anything. Java is found through JDK, then JAVA_HOME, then PATH.
# If rscd-client.jar is missing and a JDK is installed, this builds it first,
# so "clone and ./run.sh" is the whole setup.
#
# No --add-opens, no lib/ directory, no JDK pin. The client used to need all
# three because XStream reflected into java.base to read the definition files;
# it reads them itself now (client/util/XmlObjects), so any Java 8 or later
# runs it as-is. The -Drscd.applet=true mode this file used to mention is gone
# with the Applet API.

cd "$(dirname "$0")" || exit 1

if [ -n "$JDK" ]; then
   JAVA="$JDK/bin/java"
elif [ -n "$JAVA_HOME" ]; then
   JAVA="$JAVA_HOME/bin/java"
else
   JAVA=java
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
   echo "" >&2
   echo "The client needs Java, and 'java' was not found on this machine." >&2
   echo "" >&2
   echo "  Ubuntu/Debian:  sudo apt install default-jre" >&2
   echo "  Fedora/RHEL:    sudo dnf install java-latest-openjdk" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "  Anywhere else:  https://adoptium.net" >&2
   echo "" >&2
   echo "If Java is installed but not on your PATH, set JAVA_HOME to its folder." >&2
   exit 1
fi
# Existing is not the same as working: macOS ships a placeholder
# /usr/bin/java that is present before Java is installed and only prints
# an error when run. Catch that here with a friendly message instead of
# letting Apple's cryptic one through.
if ! "$JAVA" -version >/dev/null 2>&1; then
   echo "" >&2
   echo "A 'java' command exists on this machine, but it does not work." >&2
   echo "On macOS this means Java is not really installed yet -- Apple ships a" >&2
   echo "placeholder that only prints an error until the real thing is installed." >&2
   echo "" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "                  (no Homebrew? download the .pkg from https://adoptium.net)" >&2
   echo "  Anywhere else:  reinstall Java from https://adoptium.net" >&2
   exit 1
fi


# Any Java 8 or later runs the client. "1.8.0_431" is 8; "17.0.2" is 17.
JAVA_MAJOR=$("$JAVA" -version 2>&1 | sed -n '1s/.*version "\{0,1\}\([0-9][0-9.]*\).*/\1/p' | sed 's/^1\.//; s/\..*//')
if ! [ "${JAVA_MAJOR:-8}" -ge 8 ] 2>/dev/null; then
   echo "This Java is older than Java 8, which is the oldest the client supports." >&2
   echo "Install a newer one from https://adoptium.net" >&2
   exit 1
fi

# A release download ships rscd-client.jar; a bare git clone does not. If it is
# missing but a compiler is around, build it now rather than telling the user
# to go run another script first.
if [ -n "$JDK" ]; then
   JAVAC="$JDK/bin/javac"
elif [ -n "$JAVA_HOME" ]; then
   JAVAC="$JAVA_HOME/bin/javac"
else
   JAVAC=javac
fi
if [ ! -f rscd-client.jar ]; then
   if [ -x ./build.sh ] && command -v "$JAVAC" >/dev/null 2>&1; then
      echo "rscd-client.jar is not built yet -- building it now (first run only)..."
      ./build.sh || exit 1
   else
      echo "" >&2
      echo "rscd-client.jar is missing, and there is no Java compiler (JDK) here" >&2
      echo "to build it with. Two ways forward, either is fine:" >&2
      echo "" >&2
      echo "  1. Download a release zip -- it includes rscd-client.jar prebuilt." >&2
      echo "  2. Install a JDK (https://adoptium.net, or your package manager's" >&2
      echo "     default-jdk) and run this script again; it will build the jar." >&2
      exit 1
   fi
fi

# Arguments after -jar belong to the client (the settings file). System
# properties have to go before it, so they come through JAVA_OPTS:
#   JAVA_OPTS=-Drscd.cacheurl=http://localhost:8137/cache_data ./run.sh
exec "$JAVA" $JAVA_OPTS -jar rscd-client.jar "$@"
