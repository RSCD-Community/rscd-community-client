#!/bin/bash
# Compile the client and repack the jar run.sh launches.
#
# Usage: ./build.sh
#
# This exists because "javac -d build" alone changes nothing a player sees:
# run.sh runs rscd-client.jar, so a build that stops at build/ leaves the old
# jar in place and the new code apparently does not work.
#
# Any JDK 8 or later will do; set JDK or JAVA_HOME to choose one. The class
# files target Java 8 either way (--release 8 on a modern JDK, -source/-target
# on JDK 8 itself, which predates the --release flag), so the packed jar runs
# on whatever JRE a player already has. That is a distribution choice, not a
# constraint: raise RELEASE freely when bundling a runtime with jpackage.

set -e
cd "$(dirname "$0")"

JDK="${JDK:-${JAVA_HOME:-}}"
if [ -n "$JDK" ]; then
   JAVAC="$JDK/bin/javac"
   JAVA="$JDK/bin/java"
   JAR="$JDK/bin/jar"
else
   JAVAC=javac
   JAVA=java
   JAR=jar
fi
RELEASE="${RELEASE:-8}"

if ! command -v "$JAVAC" >/dev/null 2>&1; then
   echo "" >&2
   echo "Building needs a JDK (the Java compiler), and 'javac' was not found." >&2
   echo "" >&2
   echo "  Ubuntu/Debian:  sudo apt install default-jdk" >&2
   echo "  Fedora/RHEL:    sudo dnf install java-latest-openjdk-devel" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "  Anywhere else:  https://adoptium.net (pick the JDK, not the JRE)" >&2
   echo "" >&2
   echo "If Java is installed but not on your PATH, set JAVA_HOME to its folder." >&2
   echo "" >&2
   echo "Only playing, not changing the code? You do not need to build at all:" >&2
   echo "./run.sh runs the rscd-client.jar that ships with a release." >&2
   exit 1
fi
# Existing is not the same as working: macOS ships a placeholder
# /usr/bin/javac that is present before Java is installed and only prints
# an error when run. Catch that here with a friendly message instead of
# letting Apple's cryptic one through.
if ! "$JAVAC" -version >/dev/null 2>&1; then
   echo "" >&2
   echo "A 'javac' command exists on this machine, but it does not work." >&2
   echo "On macOS this means Java is not really installed yet -- Apple ships a" >&2
   echo "placeholder that only prints an error until the real thing is installed." >&2
   echo "" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "                  (no Homebrew? download the .pkg from https://adoptium.net)" >&2
   echo "  Anywhere else:  reinstall Java from https://adoptium.net" >&2
   exit 1
fi


# JDK 8's javac has no --release flag (it arrived in JDK 9), so pick the
# spelling this javac understands. "javac 1.8.0_431" is 8; "javac 17.0.2" is 17.
JAVAC_VERSION=$("$JAVAC" -version 2>&1 | sed 's/^javac //; s/^1\.//; s/[.-].*//')
if ! [ "${JAVAC_VERSION:-0}" -ge 8 ] 2>/dev/null; then
   echo "This javac reports version \"$("$JAVAC" -version 2>&1)\"; Java 8 is the oldest supported." >&2
   echo "Install a newer JDK from https://adoptium.net" >&2
   exit 1
fi
if [ "$JAVAC_VERSION" = "8" ]; then
   TARGET_FLAGS="-source $RELEASE -target $RELEASE"
else
   TARGET_FLAGS="--release $RELEASE"
fi

# ':' on unix, ';' on Windows. This file runs under git-bash too, and java there
# is a Windows program that does not understand a unix classpath.
case "$(uname -s 2>/dev/null)" in
   MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
   *) SEP=':' ;;
esac

rm -rf build
mkdir -p build
"$JAVAC" -nowarn $TARGET_FLAGS -d build $(find src -name '*.java')
"$JAR" cfm rscd-client.jar manifest.txt -C build .
# jar always writes a fresh file, which starts non-executable regardless of
# what the old one was -- double-clicking rscd-client.jar in a file manager
# needs the bit set, so a plain rebuild would otherwise silently break that
# every time until someone remembers to chmod it back by hand.
chmod +x rscd-client.jar

# The client has no dependencies any more. If a Class-Path ever comes back the
# jar has stopped standing alone, and run.sh's bare "java -jar" would fail in
# the field instead of here.
if unzip -p rscd-client.jar META-INF/MANIFEST.MF 2>/dev/null | grep -q '^Class-Path:'; then
   echo "FAIL: manifest declares a Class-Path; this jar is meant to stand alone" >&2
   exit 1
fi

# Entries must use '/'. The original webclient jar was packed with '\' on all
# 46 entries, which no classloader could read -- and javap still resolved the
# classes, so it looked fine. Only a load proves a jar loads.
if "$JAR" tf rscd-client.jar | grep -q '\\'; then
   echo "FAIL: jar contains backslash entries" >&2
   exit 1
fi

# And a real load, because javap normalises separators and would pass anyway.
# The one-class checker is generated here rather than kept as a stray .java in
# the repository root; build-check/ keeps it off the jar's own classpath, so
# the classes below genuinely resolve from the jar, not from build/.
rm -rf build-check
mkdir -p build-check
cat > build-check/JarLoads.java <<'EOF'
/* Loads one class by name and exits 0/1. Exists so build.sh can prove the
   packed jar actually loads -- see the backslash-entry story above. */
public class JarLoads {
   public static void main(String[] args) throws Exception {
      Class.forName(args[0], false, JarLoads.class.getClassLoader());
   }
}
EOF
"$JAVAC" -nowarn -d build-check build-check/JarLoads.java
for c in org.rscdaemon.client.mudclient \
         org.rscdaemon.client.ScriptPanel \
         org.rscdaemon.client.ScriptRunner \
         org.rscdaemon.client.WorldsPanel \
         org.rscdaemon.client.util.XmlObjects; do
   "$JAVA" -cp "build-check${SEP}rscd-client.jar" JarLoads "$c" || {
      echo "FAIL: $c did not load from the packed jar" >&2
      exit 1
   }
done
rm -rf build-check

CLASSES=$("$JAR" tf rscd-client.jar | grep -c '\.class$')
echo "rscd-client.jar repacked: $CLASSES classes, no dependencies"
echo "Run ./run.sh to play."
