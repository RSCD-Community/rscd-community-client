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

# The JVM's own defaults are sized for a server, not for a game client: a
# quarter of the machine's RAM as the heap ceiling, a parallel collector with a
# thread per core, and a code cache and metaspace to match. The client's live
# set is tiny -- around 25MB for a bot, well under 150MB with the 3D pipeline
# running -- so those defaults cost hundreds of megabytes of committed memory
# and a dozen idle GC threads for nothing. Cap them. SerialGC is the right
# collector here for the same reason: one small heap, one mutator thread that
# matters, pauses measured in single-digit milliseconds.
#
# A bot gets the tighter set because it does not render (see Config.RENDERLESS)
# and so never allocates the geometry a player does. Measured on an idle bot:
# 317MB RSS and 36 threads on the JVM defaults, 128MB and 25 threads with these.
#
# These are defaults, not a policy. Anything in JAVA_OPTS comes after them and
# therefore wins, and setting a collector there suppresses SerialGC rather than
# colliding with it ("Multiple garbage collectors selected" is a fatal error).
#
# The heap flags are a ceiling plus permission to give memory back: -Xms starts
# small, the free-ratio pair lets the collector uncommit what a quiet client is
# not using, and -Xmx is only the limit it may never pass. Without the ratios a
# heap that grew once during asset loading stays committed for the whole
# session, which on one measured bot was 65MB of resident memory holding 24MB
# of live objects.
#
# TieredStopAtLevel=1 is for bots only: C1 compiles fast and small, C2 exists to
# make hot loops faster and the hottest loop in this client is the renderer a
# bot does not run. A player keeps both compilers.
case " $* " in
   *" --bot"*) CLIENT_OPTS="-Xms16m -Xmx96m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:MaxNewSize=8m -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=64m -Xss512k -XX:ReservedCodeCacheSize=48m" ;;
   *) CLIENT_OPTS="-Xms32m -Xmx256m -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=64m" ;;
esac
case "$JAVA_OPTS" in
   *GC*) ;;
   *) CLIENT_OPTS="$CLIENT_OPTS -XX:+UseSerialGC" ;;
esac

# A bot opens no window and needs no display: headless, so there is no X
# connection, no Xvfb to keep running beside it and none of the AWT threads a
# frame brings. It also never constructs a java.awt.Font, which is what was
# really paying for the display -- finding out what Helvetica is called here
# enumerates every installed font file, 18MB of it (see GameWindow.loadingFont).
#
# Not applied when JAVA_OPTS asks for rendering: -Drscd.render=1 turns a bot
# back into a client that draws (Config.RENDERLESS), and that one wants its
# window.
case " $* " in
   *" --bot"*)
      case "$JAVA_OPTS" in
         *rscd.render*) ;;
         *) CLIENT_OPTS="$CLIENT_OPTS -Djava.awt.headless=true" ;;
      esac
      ;;
esac

# Arguments after -jar belong to the client (the settings file). System
# properties have to go before it, so they come through JAVA_OPTS:
#   JAVA_OPTS=-Drscd.cacheurl=http://localhost:8137/cache_data ./run.sh
exec "$JAVA" $CLIENT_OPTS $JAVA_OPTS -jar rscd-client.jar "$@"
