#!/bin/bash
# Install the client into the desktop (Linux).
#
# Usage: ./install.sh              install for the current user
#        ./install.sh --uninstall  undo everything this script did
#
# What installing means here: this checkout stays where it is and keeps
# working exactly as before; what gets installed is the desktop wiring around
# it, all per-user (no sudo):
#
#   - an application menu entry ("RSCD Community Client", with icon)
#   - the rscd:// link type, handled by this client -- so the community
#     website's Join Now buttons launch the game pointed at the right world
#
# The rscd:// part is the reason this script exists. A join link is just
#     rscd://host:port/?name=World%20Name
# and the client takes it as a command-line argument; registering the scheme
# is what makes the browser hand it over. See install/join-links.md for the
# details, including what a link can and cannot change.
#
# Windows: use install.bat. macOS: use install-mac.sh -- the scheme needs
# an .app bundle there, which this script builds for you via jpackage.

set -u
cd "$(dirname "$0")"
HERE="$(pwd)"

APPS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
DESKTOP_FILE="$APPS_DIR/rscd.desktop"

if [ "${1:-}" = "--uninstall" ]; then
   rm -f "$DESKTOP_FILE"
   command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" 2>/dev/null
   echo "Removed the menu entry and the rscd:// registration."
   echo "The checkout itself is untouched -- delete the folder if you want it gone too."
   exit 0
fi

if [ "$(uname -s)" = "Darwin" ]; then
   echo "This installer is for Linux. On macOS, run ./install-mac.sh instead --" >&2
   echo "it builds a proper .app bundle for you (needed for rscd:// links" >&2
   echo "to work there) rather than doing the Linux-style desktop-entry dance." >&2
   exit 1
fi

# The desktop entry launches through run.sh, so it inherits the same Java
# discovery and friendly failure messages a terminal launch gets. %u is how
# the browser's rscd:// link arrives as an argument.
if [ ! -f run.sh ]; then
   echo "run.sh is missing beside this script -- this does not look like a" >&2
   echo "complete client checkout. Clone or unzip the whole repository." >&2
   exit 1
fi

# Make sure there is something to launch before wiring the desktop to it:
# a release zip ships rscd-client.jar, a bare clone builds it on first run.
if [ ! -f rscd-client.jar ]; then
   echo "Note: rscd-client.jar is not built yet. It will build itself the"
   echo "first time the client runs (needs a JDK); nothing to do now."
fi

mkdir -p "$APPS_DIR"
cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Name=RSCD Community Client
Comment=RuneScape Classic community client
Exec="$HERE/run.sh" %u
Icon=$HERE/install/rscd-icon.png
Terminal=false
Categories=Game;
MimeType=x-scheme-handler/rscd;
EOF

# Registering the MimeType alone is not enough on every desktop; naming the
# entry as the scheme's default handler is what makes browsers stop asking.
if command -v xdg-mime >/dev/null 2>&1; then
   xdg-mime default rscd.desktop x-scheme-handler/rscd
else
   echo "Warning: xdg-mime is not installed, so the rscd:// link type could" >&2
   echo "not be set as handled by this client. The menu entry still works;" >&2
   echo "install xdg-utils and re-run this for the links." >&2
fi
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" 2>/dev/null

echo "Installed for $USER:"
echo "  - Application menu: RSCD Community Client"
if command -v xdg-mime >/dev/null 2>&1; then
   HANDLER=$(xdg-mime query default x-scheme-handler/rscd 2>/dev/null)
   echo "  - rscd:// links open: ${HANDLER:-(nothing reported -- log out and in, then check again)}"
   echo ""
   echo "Try it: click a Join Now link on a community website, or run"
   echo "  xdg-open 'rscd://127.0.0.1:43594/?name=Local%20Test'"
fi
echo ""
echo "To undo: ./install.sh --uninstall"
