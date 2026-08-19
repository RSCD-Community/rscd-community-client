# rscd:// join links

The website's Play Game page renders one **Join Now** link per online world:

    rscd://host:port/?name=Server%20Name&world=1&protocol=10

The client accepts such a link as a command-line argument — the OS invokes it
that way once the scheme is registered:

    java -jar rscd-client.jar "rscd://127.0.0.1:43594/?name=RSCD%20Test"

Only the host and port decide anything (`Config.applyJoinUri`); `name` labels
the connection on the login screen, `world` and `protocol` are informational.
A link overrides the remembered default server for that launch only — nothing
is written to `settings.ini`. An unparseable link is ignored and the client
starts normally.

## Registering the scheme

Each platform has its own installer, in this folder's root; run the one for
your OS and it does the rest for you (menu entry / Start Menu shortcut /
`.app` bundle, plus the rscd:// registration itself). `--uninstall` on any
of them undoes exactly what it did and nothing else.

- **Linux** — `./install.sh`. Writes `~/.local/share/applications/rscd.desktop`
  and runs `xdg-mime default rscd.desktop x-scheme-handler/rscd`.
- **Windows** — `install.bat`. Writes the `HKEY_CURRENT_USER\Software\Classes\rscd`
  keys and a Start Menu shortcut.
- **macOS** — `./install-mac.sh`. The scheme has to be declared by a real
  `.app` bundle's `Info.plist`, so this script builds one with `jpackage
  --mac-url-scheme rscd` (needs a JDK 17+, not just a JRE) and installs it to
  `~/Applications`. That declaration is necessary and not sufficient: it only
  makes macOS *route* the link to the app, which then arrives as an Apple
  event rather than as an argument. `MacJoinLinks` is the half that catches
  it — see below.

The pieces below are what those scripts generate, kept here for reference
if you ever need to do it by hand — e.g. packaging for a Linux distro that
wants its own desktop-file convention.

**Linux**, the raw desktop entry (`install.sh` writes the equivalent,
pointed at `run.sh` so a fresh clone still auto-builds on first launch):

    [Desktop Entry]
    Type=Application
    Name=RSCD Community Client
    Exec=/path/to/run.sh %u
    Icon=/path/to/install/rscd-icon.png
    MimeType=x-scheme-handler/rscd;

**Windows**, the raw registry keys (`install.bat` writes these with the
real `javaw.exe` and jar paths for the machine it runs on):

    HKEY_CURRENT_USER\Software\Classes\rscd
        (Default) = "URL:RSCD Community"
        URL Protocol = ""
    HKEY_CURRENT_USER\Software\Classes\rscd\shell\open\command
        (Default) = "C:\path\to\javaw.exe" -jar "C:\path\to\rscd-client.jar" "%1"

**macOS**, the `Info.plist` entry `--mac-url-scheme rscd` generates:

    <key>CFBundleURLTypes</key>
    <array>
      <dict>
        <key>CFBundleURLName</key>
        <string>RSCD Community</string>
        <key>CFBundleURLSchemes</key>
        <array><string>rscd</string></array>
      </dict>
    </array>

## When a client is already open

Clicking a link should not give you a second copy of the game. What stops it
differs by platform, because the platforms deliver the link differently.

**Linux and Windows** start a *new process* every time — `run.sh %u` and
`"%1"` respectively; the OS has no idea anything is already running. So the
new process asks before it opens a window. `SingleInstance` has the running
client listen on an ephemeral loopback port and write that port plus a random
token to `~/.rscd/instance` (owner-readable only). The new process reads the
file, offers the link, and exits only if the answer is `TAKEN`.

**macOS** does the opposite: LaunchServices routes the link to the `.app` that
already claims the scheme, so no second process starts and nothing lands in
`argv`. The link arrives as a `kAEGetURL` Apple event, which the JDK forwards
only to a registered open-URI handler — `MacJoinLinks` registers one. Without
it the event is simply dropped and the click does nothing at all, on first
launch as much as on a later one.

Both routes end at the same rule: **a link is taken only if nobody is signed
in.** A player mid-game is never interrupted and their session is never closed
to make room for a link. On Linux and Windows the click then opens its own
window, exactly as it did before any of this existed. On macOS there is no
second process to open one, so the click does nothing — the session is
protected either way, but that is the visible difference between them.

Everything else falls through to a normal launch: no lock file, a stale one, a
client that was killed, a wrong token, a malformed link, or no answer within
two seconds. That is deliberate — every failure mode lands on the behaviour we
already had.

The token is not ceremony. Without it any local process could tell the client
to go and sit on a sign-in screen belonging to a server of its choosing, which
is a plausible way to collect a password on a shared machine.
