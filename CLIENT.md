# The community client

`rscd-client.jar`, built from this tree. A desktop client, downloaded and run
like any other program.

It started life as the Ignis Isle **webclient** (`rscd-webclient.jar`,
`clientVersion = 1218`) and there is nothing web about it any more — the applet
is gone, along with the Applet API it needed. This file is the record of what
the original did and what changed, because most of what looks arbitrary in here
is a decision the browser made for it twenty years ago.

## The one thing that was actually applet-bound

Asset delivery. That's it.

`createWindow()` always opened a real AWT `GameFrame`, even in the webclient, so
the window was never the problem. The `appletMode` field is **vestigial**:
`GameWindow` and `mudclient` between them assign it in three places and *neither
class ever reads it*. It is not, and never was, a working mode switch. Do not
try to use it as one.

The real seam was where assets came from. The webclient downloaded them from
`http://WEB_IP/cache_data` into a cache directory under the user's home on
every launch, MD5-verified each one against `remotechecksum.php`, and read
them back off disk; the desktop build shipped them next to the jar instead and
skipped the check entirely, so a missing file was a hard error.

Both are gone. Nothing is written to disk now: `Config.CACHE_URL` is fetched
into memory at startup and held there for the life of the process — see
`Assets`. There is no cache directory, no staleness, and no half-updated
install to support.

## Original code, verbatim

`mudclient.main()` was:

```java
public static final void main(String[] args) throws Exception {
   GameWindowMiddleMan.clientVersion = 1218;
   mudclient mc = new mudclient();
   mc.appletMode = true;
   mc.createWindow(mc.windowWidth, mc.windowHeight + 11, "JScape", false);
}
```

Note it never called `initConfig()`, so `WEB_IP` defaulted to
`127.0.0.1:8137` and everything downloaded. `startGame()` called
`this.loadCacheFromMirrors()` unconditionally, and the splash path was built
by hand from `System.getProperty("user.home")`.

**Six separate places** built that cache path by hand rather than going
through `loadcache()`. This is why a first conversion attempt still failed
with `NullPointerException` in `EntityHandler.load()` — `loadcache()` had been
fixed, but these had not, so they read from an empty directory and handed back
nulls:

| file | what it opened directly |
|---|---|
| `mudclient.createdrive()` | the cache dir itself (`mkdir`) |
| `mudclient.loadcache()` | every file in `gamefiles` |
| `mudclient.load(String)` | arbitrary asset by name |
| `GameImage` | `Sprites.xml.data` as a `ZipFile` |
| `EngineHandle` | `Landscape.xml.data` as a `ZipFile` |
| `EntityHandler.FetchMahFile()` | the ten `*.xml.data` definition files |

All six go through `Assets` now, and none of them touch the filesystem.

## There is no webclient mode left

`-Drscd.applet=true` used to exist and no longer does. All it ever selected was
`Config.initConfig()` over `Config.initConfig(String)` — which assets to fetch
and which server to talk to, not anything applet-bound — and what it selected
was a page that has not existed for years.

The Applet API went with it. Browsers dropped plugin support between 2015 and
2017, Web Start went with JDK 11, and JDK 24 removed `java.applet` outright, so
`GameWindow` no longer extends `Applet`; it extends `Panel`, which is all the
class ever used. Browser play is cancelled permanently and this is not a
decision that can be revisited by a flag.

## Assets

**This repository ships no game assets.** They belong to the world you join:
each server publishes its own `cache_data/` over HTTP and advertises it as
`cache_url`, the client fetches all of it into memory at startup, and that is
what makes one client able to play on many servers. Ours lives in
`rscd-server/cache_data/`.

What follows describes that directory, wherever it is served from.

`cache_data/` holds all 15 files `mudclient.gamefiles` asks for:

```
Animations Doors Elevation ItemDef Landscape Loading NPCs Objects
Prayers SpellDef Sprites Textures Tiles   (all *.xml.data)
models36.jag   sounds1.mem
```

Three more live there and are **not** in `gamefiles`, because they are only
wanted by the world map and `gamefiles` is downloaded in full before the login
screen appears. `WorldMapPanel` fetches them itself, on its own thread, the
first time the map is opened:

```
worldmap.png   the map itself
mapkey.gif     the legend
worldmap.cal   scale / origin_x / origin_y -- how a game coordinate becomes a
               pixel in worldmap.png
```

All three are optional. A world with no `worldmap.png` gets a screen that says
so; one with no `mapkey.gif` gets no Key button; one with no `worldmap.cal`
gets the vanilla geometry, which is the fallback compiled into the panel.

## Fonts, and why panels used to overflow on Linux

There are no font files in the cache. `GameImage.loadFont` builds each of the
eight fonts at startup by asking AWT for `new Font("Helvetica", style, size)`
and rasterising the 95 printable characters itself, which is exactly what
Jagex did. Every panel in the game is therefore laid out against Helvetica's
metrics -- the options menu's longest line, "Privacy settings. Will be applied
to", is 193 pixels wide against a 193 pixel usable panel. That is not luck.

Windows and Mac map Helvetica to Arial, so it fits. Linux has neither, and
Java quietly substitutes DejaVu Sans, which is about 27% wider: the same line
measures 246 pixels and runs off the right edge of the 512 pixel window,
taking most of the panel's other lines with it. Vanilla's own text, not
anything added here.

`GameImage.helvetica(style, size)` fixes it. It asks for Helvetica first, as
Jagex did, and only if the JRE has no such family falls back through the ones
that are metrically compatible with it:

```
Helvetica -> Arial -> Liberation Sans -> Nimbus Sans -> FreeSans -> Nimbus Sans L
```

It resolves once, caches, and prints which family it settled on. All four
`new Font("Helvetica", ...)` sites now go through it: `GameImage.loadFont`,
`GameWindow.LOADING_FONT`, and the two error screens in `mudclient.method4`.

Liberation Sans is the Arial clone and Nimbus Sans the Helvetica clone; both
ship with nearly every distribution, so a packaged client should list one of
them as a dependency rather than assume it. `OverlayHarness` asserts the
widest line still fits 193 pixels, so a bad substitution fails the build
rather than the eye.

Widths measured for the options panel, worst line:

| family | bold 12 | plain 11 |
|---|---|---|
| Helvetica / Arial (Windows, Mac) | 193 | 177 |
| Liberation Sans | 193 | 177 |
| Nimbus Sans | 204 | 176 |
| FreeSans | 195 | 174 |
| **DejaVu Sans** (the bad substitute) | **246** | **209** |

## Non-vanilla features

Keys, all handled in `mudclient.handleMenuKeyDown()`:

| Key | |
|---|---|
| F1 | camera zoom toggle (vanilla) |
| F2 | script menu — `ScriptPanel`, also `/menu`, Escape closes |
| F11 | movie recorder toggle |
| F12 | screenshot |

Chat commands live on two planes. `::` is the admin plane: the client hands
the line to the server untouched and keeps nothing on it. `/` is the user
plane, STS's prefix for STS's layer — a `/` line belongs to the client, and
recognised or not it is never sent as chat and never reaches the server,
exactly as STS treated it. Both argument shapes work: `/offer(10,100)` as STS
documented, `/offer 10 100` as people type.

That means any chat line that merely starts with `/` — say `/50 each` in a
price haggle — is treated as a command attempt and eaten with an
`Unknown command` hint rather than said aloud. The same tradeoff STS made on
live RSC; nothing you type after `/` can accidentally become public chat,
which is the safer side of the bargain to be on.

Script control: `/start Name(arg,arg)`, `/stop`, `/reload`, `/scripts` (also
`/macros`, the STS name for the same listing), `/debug`, `/menu`. An
unrecognised `/` line is offered to the running script's `OnInput()` and then
dropped with a hint.

STS's convenience layer — the commands it added because the stock client made
live RSC awkward:

| | |
|---|---|
| `/offer id amount` / `/stake id amount` | add a stack to the open trade or duel window — two names, one command, since only one window can be open |
| `/withdraw id amount` | from the open bank screen |
| `/deposit id amount` | to the open bank screen |
| `/hop world` | relog into another world of the same server |
| `/reset` | zero the "Exp gained" counter on the F5 status overlay |

All of them check the client's own books first (bank count, inventory count,
the 12-slot offer cap, non-stackables one at a time), so a typo is a chat
line rather than a silent nothing from the server.

The script menu is drawn over the game in the client's own render loop and
takes the frame's mouse click while it is open. It is built on first use, so a
player who never presses F2 never allocates it.

### The resizable window

The frame can be dragged to any size; vanilla's 512x334 is the minimum, not
the shape. Nothing scales: the right-hand panels keep hanging off the right
edge, the chat keeps sitting on the bottom edge, the sprites stay their
pixel size — a bigger window is simply more world through the same lens,
which is why it pairs with the F1 zoom and the fog toggle. The engine was
never actually 512-bound (Jagex shipped 780x558 and 1024x768 builds of this
same code); what was bound was the plumbing, and that is what changed:
GameFrame reports every drag, the game thread applies the size between ticks
(new framebuffer, camera re-pointed at it, the edge-anchored menus rebuilt
against the new edges), and the size is written to `settings.ini`
(`window_width`/`window_height`, the world view not the frame) once the drag
has settled for a second, so the client opens where you left it.

The screens that are genuinely 512-shaped — login, character design, the
Worlds list, the F2 script menu, the calculators — sit centred in the larger
window instead of growing. The world map already sizes itself to the window
and gets the room for free. The chat-tab artwork (one sprite on the login
screens, another in game) is 512 wide; past it the strip is finished by
smearing the artwork's last column across the remainder (the right edge is
plain bar, so the match is exact and no tab label repeats). The in-game
dialogs — bank, shop, trade, duel, welcome, add-a-friend and the rest — sit
centred through the same offsets as the login screens, applied to each
dialog's draw coordinates and its click tests together so the clicks cannot
drift from the pixels; at the minimum size the offsets are zero and nothing
moves. The right-click menu opens at the cursor instead, and
vanilla's literal clamp (510/315, windowWidth-2 and windowHeight-19 in
disguise) dragged it back into the old corner where the mouse-left-the-menu
check closed it instantly — right-click looked dead everywhere beyond
512x334 until the clamp was made relative. A resize also rebuilds the chat
menu, which costs the scrollback above the visible rows — the same thing a
relog cost in vanilla.

### The shop rows

The shop's bottom panel is the final official client's, not RSCD's. RSCD had
replaced vanilla's buy-one/sell-one links with a "how many?" prompt on every
click — convenient for bulk, infuriating for a shop holding one of a thing.
Jagex solved the same problem themselves before the end: the last official
client (Feb 2015) shows `<item>: buy for Ngp each` and a `Buy: 1 5 10 50 X`
row, with 5/10/50 only appearing when the shop actually stocks that many
(mirrored for selling against what you hold), and only X asking a question —
the same contract as the bank's amount row. The geometry, click bands,
availability gates and empty-state strings were lifted from a decompile of
the signed 2015 jar; the wire stays ours (the fixed amounts send the usual
buy/sell packets with the same stock-and-coins clamps the prompt applies,
and X still opens the in-client prompt).

### The world map

The World Map button on the F2 menu, `WorldMapPanel`. Full screen over the
menu that opened it, so Escape comes back to the menu rather than out to the
game.

| | |
|---|---|
| drag | pan |
| wheel | zoom about the pointer, or scroll the legend while it is under it |
| `+` `=` / `-` `_` | zoom about the centre, as the buttons on the strip do |
| `f` | fit the whole map |
| `c` | centre on the player |
| `k` | the legend |
| arrows | pan, or scroll the legend while it is up |
| Escape | closes the legend, then the map |

It takes the entire keyboard while it is up -- the arrow keys are the camera's
otherwise -- and it takes the mouse as a held button rather than a click,
because panning is a drag.

The wheel is the one input the client did not already have. `java.awt.Event`
predates the mouse wheel, so there is no old-model id for one, and asking for
the new model at all switches AWT out of delivering the old events this client
is built on -- adding a `MouseWheelListener` and nothing else silently kills
every click and keystroke in the game. `GameFrame` therefore opts in to the 1.1
model wholesale and converts back to `Event` itself; see the comment above
`processEvent`. The wheel goes straight to `GameWindow.mouseWheel` instead, and
belongs to the map alone.

Notches arrive on the AWT thread and are banked rather than acted on, then
spent in one step at the top of the next frame, so a fast flick is one zoom
rather than a queue of them arriving over the following second.

The strip along the bottom overlaps the rows the chat tabs claim, and the tab
handler clears the mouse button on its way out. Anything drawn over the game
view has to say so -- `panelOwnsScreen()` in `mudclient` -- or its bottom
twenty-odd pixels are disarmed before it is ever asked about them.

The projection is `px = origin_x - scale * gameX`, `py = origin_y + scale *
gameY`, read from `worldmap.cal` so a world that ships its own map ships its
own geometry. `origin_x` is subtracted because game x grows westward while
pixels grow eastward.

There is a "you are here" marker only when the player is on the surface. Every
storey of the world is stacked 944 tiles apart in one coordinate space, so a
player in a dungeon has a y that projects perfectly well onto a forest they are
nowhere near; underground the strip says where they are in words instead.

The decoded map is around 55MB, four times everything else the client holds, so
it is dropped when the map closes and when the player leaves the world. The
megabyte it was decoded from is kept, which makes re-opening a decode rather
than another download.

### The calculators

The Calculators button on the F2 menu, `CalculatorPanel`. Two pages: a picker
that is the script list pointed at `calculators/`, then the calculator itself
-- inputs down the left strip, output in the pane, the layout every RSC
calculator page has had since tip.it.

A calculator is a `.java` in `calculators/` that extends `Calculator`
(`calculators/README.md` is the author-facing guide). It is loaded through its
own `ScriptRunner` -- same in-process compiler, same preamble, same prebuilt
`.class` fallback for a plain JRE, with output in `calculators-bin/` so a
calculator and a script may share a name. The calculator declares inputs as
fields and does all its computation in `compute(Output)`; the panel renders
both sides through `Skin` and recomputes on every input change. `compute()`
runs on the render thread, and a throw is caught and drawn where the answers
would have been.

Inputs preload from the logged-in character (`.def(baseLevel(STRENGTH))`) and
are typed over freely. The panel takes the whole keyboard while open, the way
the world map does -- that is what lets it have text fields where ScriptPanel
cannot: nothing typed reaches the game menu, so nothing lands in the chat
line. Escape backs out one layer at a time (dropdown, caret, calculator,
picker, back to the script menu); F2 straight out to the game.

Seventeen stock calculators ship — the full classic tip.it set. `MaxMeleeHit`
and `CombatLevel` compute with the server's own formulas and weapon power
table, `Experience` is the authentic curve for any skill, and the skill
calculators (`Cooking` through `Woodcutting`) carry the classic rate tables.

### The movie recorder

F11, or the Record button on the F2 menu. Output goes to
`media/<character name>/movieN.avi` beside the jar — `media/` relative to the
jar directory, not the launch directory, so it does not scatter files wherever
the shortcut happened to point. Screenshots land in the same folder.

It was three classes over the Java Media Framework writing a QuickTime `.mov`,
and it had two problems. JMF is a 2 MB Sun binary under a licence that forbids
redistribution, so it could not ship with an open-source client. And it had
never worked: nothing in the client ever put a frame into the queue the encoder
read from, so every recording was a header with no video after it. The zero
byte files were not a bug in the encoder — there was no producer at all.

`recorder/Recorder.java` is now a JDK-only MJPG AVI writer, about 250 lines:
each frame is a JPEG at quality 0.8 and the AVI is roughly two hundred bytes of
header around them. `ImageIO` has had a JPEG encoder since 1.4, so there is no
dependency. `mudclient.captureFrame()` is the producer that was missing, called
from `method4()` once the frame is drawn.

Capture is sampled to `movie_fps` (default 5) rather than taken every tick: the
game runs at 50 ticks a second and a frame is 512×357 of int, so capturing
every one would be about 35 MB/s into a queue no encoder drains that fast. The
queue holds 64 frames — around thirteen seconds of slack — and past that a
frame is dropped rather than blocking the render thread, because a recording
that stutters is better than a client that does. The count of dropped frames is
reported when you stop.

### The two STS settings

The F2 settings page has ten rows. The first four are client flags a script
can already set through the Methods API. Rows five and six are STS's, and they
are the reason the stat panel is back to Jagex's two tabs. Then three that
came off Jagex's Game options menu (hide roofs, auto screenshots, fightmode
selector), which the server still remembers. The last, Fog of war — STS's
name for it — is this client's own. The fog is two numbers on the camera:
`zoom4`, the depth past which the renderer dims the world toward black, and
`zoom1/2`, the far clip where it stops drawing at all — the black wall.
Vanilla never showed either, because its camera was pinned close; this client
can zoom out (`cameraHeight` runs 300–1500, a dolly of up to 3000), which
drags the wall straight into frame. Turning the row off pushes fog and clip
to 12000, past the whole loaded section, so everything the client has is
drawn and none of it fades — the black that remains is the section boundary
itself, and it walks with you. Being client-side only, it is remembered in
`settings.ini` (key `fog`) rather than by the server. The two STS rows:

| Row | |
|---|---|
| Status overlay | STS's status menu — name, combat level, coords, pid, fatigue and exp gained, written as plain text over the top-left of the game view at x=10, one line every 12px, white with `@red@` markers. It was never a panel in STS and it is not one here. Off by default. |
| Show HP | STS's show-HP. Vanilla draws a health bar only while `combatTimer` is running, so a mob you are not fighting has none even though the server has already told you its health. With this on the bar stays for as long as that health is known. Off by default. |

"Known" is `Mob.hitPointsBase > 0`: the client sets that field nowhere but the
damage update, and it is also the divisor the bar width is computed with, so a
mob the server has said nothing about is both unrevealed and unsafe to divide
by. `healthBarShows(Mob)` is the one place that decides, and both draw sites —
players and NPCs — go through it.

### AutoLogin

`autoLogin` is **off by default**; `AutoLogin(true)` or the F2 row turns it on.
With it on, the Logout button is a round trip — you log out and the client puts
you straight back in, which is what a bot wants and not what a person clicking
Logout means. That is what it did in STS, and it is why it is opt-in here.

It covers logging back in **from the login screen** — after a logout, a kick,
or a reconnect that failed. STS logged straight back in from there and now so
does `updateLoginScreen()`, which is the half a bot actually needs.

It does **not** gate `lostConnection()`. A socket that dies under you
reconnects with the credentials `login()` stashed whether autoLogin is on or
off, because that is vanilla's own behaviour and predates any of this.

The retry starts at two seconds and grows by two seconds a try up to a minute,
because a minute is how long the server holds *that username is already logged
in* and a client retrying every two seconds spends that minute hammering the
login server.

## Scripting: three tiers

Botting is a supported feature here, not something tolerated, and the scripts
people still have on disk were written for three different bots. All three run,
from the same folder, under the same `/start`:

| Tier | File | Base class | Shape |
|---|---|---|---|
| STS | `Name.java` | `Methods` | `MainBody()` — the script owns the loop |
| TextScript | `Name.txt` | — | `[label]` blocks, one command a line, no Java |
| APOS | `Name.java` | `Script` | `init()` / `main()` / `paint()` — the client owns the loop |

`ScriptRunner.load()` picks: a `.java` wins over a `.txt` of the same name, and
a compiled `Name.java` is loaded through a constructor taking `mudclient` if it
has one and `Extension` otherwise, which is the only thing that distinguishes
an STS script from an APOS one. Scripts carry no `package` line and no imports,
so the source is staged with `import org.rscdaemon.client.*;` on top — that one
line is the whole compatibility layer for both Java tiers.

### TextScript

`TextScript v1.6` was itself a script *for* STS: one compiled class, shipped
inside the bot, that read a `.txt` of labelled blocks. It is why the
`Textscripts` folder of a 2006 install is full of files by people who never
wrote a line of Java. `TextScript.java` is a re-implementation of it, and all
eleven scripts that survive in `STS203C/Textscripts` run untouched.

```
[main]
autologin(true)
display(@ran@Anything Picker)
goto([pickup])

[pickup]
pickupitembyid(441)
wait(350)
goto([pickup])
```

That is `Picker.txt`, complete. `[label]` is a jump target and not a scope —
execution falls off the bottom of one block into the next, which several of the
surviving scripts rely on — execution starts at line 1 rather than at `[main]`,
`|` is a comment, `#name#` is a variable and `$name$` is a value read off the
game. Items are addressed **by item id, never by slot**: `useitem(330)` eats a
cake.

The command set is the original's — 131 names, 53 conditionals and the rest
actions — recovered from `TextScript.class`'s own string table rather than
guessed from the surviving scripts. Two names are aliases the original spelled
two ways in one release (`gotoifitemcountinvis` / `gotoifcountinvitemis`), and
exactly one name is new: `gotoifnotsleeping`, because everybody typed it by
accident for `gotoifnotisleeping`.

A line that is not a command does not stop the script loading. `Edge.txt` ends
with eighty dashes drawn under its last block and ran for years because
execution reached `end()` first; unknown lines are kept in place and fail only
if reached, and are listed once at start-up so a real typo is still visible.

### APOS

A separate, later bot with an unrelated API. The difference that matters is who
owns the loop: an APOS script never loops and never sleeps, it answers `main()`
with how many milliseconds to leave it alone for.

```java
public class TPM_Position extends Script {
    public TPM_Position(Extension e) { super(e); }
    public void init(String params) { }
    public int  main()  { return random(1, 500); }
    public void paint() { drawString("Position: " + getX() + "," + getY(), 115, 40, 1, 0xFFFFFF); }
}
```

`Script.java` is that framework: `MainBody()` calls `init()` once and then
`main()` forever, and everything else is the APOS surface — 114 methods, the
88 the surviving scripts call plus the overloads and pairs that go with them —
mapped onto `Methods`. Naming is APOS's `lowerCamelCase`, against STS's
`PascalCase`; they are different APIs that happen to share a client and no
attempt is made to unify them. Nothing in `Script` is `final`, because two of
the surviving scripts override `talkToNpc()` and `useOnNpc()` and call `super`
from inside them.

`paint()` reaches the screen through `ToShow()`. APOS's `drawString()` takes a
font and a colour per line where STS's `Stats` had only text and coordinates,
so `Stats` gained two more arrays; the three-argument constructor a 2006 script
calls still exists and still means what it meant, and leaves them null, which
`drawScriptOverlay()` reads as "font 1, white" — exactly what STS drew.

Two classes exist under `com/aposbot/` because ten of the sixteen scripts
import them by name to build an AWT settings window: `Constants.ICONS` and
`StandardCloseHandler`. Nothing was copied from APOS to write them.

Four things could not be honoured, all of them things this client lacks rather
than things APOS did not do. `onChatMessage`'s two booleans were "sender is a
moderator / an administrator" and arrive false, because the client is not told.
`autohop()` is stored and never acted on: nothing here hops worlds by itself.
`isSleeping()` is always false and `useSleepingBag()` still uses the bag,
because this client has no sleep screen. And `onTradeRequest()` is recovered
from the server message `"<name> wishes to trade with you"`, there being no
trade-request packet to hook.

`useItemOnObject()` is the one place the corpus contradicts its own
documentation: the first argument is documented as an inventory slot and both
call sites pass an item id — one of them after working the slot out and
discarding it. Both readings are accepted, slot first.

## Stat panel tabs

Stats | Quests, which is what Jagex shipped and all they shipped. RSCD had
replaced Quests with an Info tab; the tab is gone and its contents live in the
status overlay below, where extras belong.

The Stats tab is Jagex's, including the part that looks like a mistake: the
right-hand skill column is drawn a row *higher* than the left, at `i1 - 13`, so
it starts level with the "Skills" header and ends a row early. The gap that
leaves at the bottom right is where Quest Points goes, with Fatigue opposite it
on the left. RSCD had levelled the columns up, moved fatigue beside the header
and added a fourth line under the divider in each branch — which is what made
everything below read a row out and hung the last line off the bottom edge.

Fatigue is the one number that is deliberately not Jagex's arithmetic. They
sent 0..750 and divided by 750 here; rscd-server sends a percentage already, so
it is printed as it arrives.

The quest list is `mudclient.QUEST_NAMES` — Jagex's fifty, in Jagex's order.
The index is the id: it is what the server's completion packet is keyed on and
what a script passes to `Methods.QuestDone()`, so nothing may be reordered or
removed. Completion arrives on opcode 5 (one byte per quest, 1 for finished)
and quest points as a trailing byte on the stats packet, opcode 180. rscd-server
sends neither yet, so the list reads all-red and the count reads zero.

## Build

```
./build.sh
```

which is:

```
javac -nowarn --release 8 -d build $(find src -name '*.java')
jar cfm rscd-client.jar manifest.txt -C build .
```

Compiling to `build/` alone changes nothing a player sees — `run.sh` runs
`rscd-client.jar`, so a build that stops there leaves the old jar in place and
the new code looks broken. `build.sh` repacks, checks there is no `Class-Path`
and that the entries use `/`, and loads five classes out of the packed jar to
prove it.

Any JDK 8 or later builds it; set `JDK=` to choose one. It used to pin
`/opt/jdk/jdk1.8.0_431` because XStream's `Sun14ReflectionProvider` probed a
`sun.misc.Unsafe` field removed after 8. XStream is gone — `client/util/XmlObjects`
reads the definition files — and the pin, the `lib/` directory and the
`Add-Opens` manifest line went with it. `--release 8` is kept so the class files
run on whatever JRE a player happens to have, which is a distribution choice
now rather than a constraint.

When repacking by hand, **zip entries must use `/` separators**. The original
`rscd-webclient.jar` was written with `\` on all 46 entries, which made it
unloadable by any classloader — `Class.forName` threw `ClassNotFoundException`
even though `javap` resolved the classes fine (it normalises). A `javap` check
does not prove a jar loads; only a runtime `Class.forName` does.

## The duplicate binary loads, and why they could never have worked

`EntityHandler.load()` used to end with:

```java
PersistenceManager.load(FetchMahFile("models36.jag"));
PersistenceManager.load(FetchMahFile("sounds1.mem"));
```

Every other file it loads is gzipped XStream XML. Those two are Jagex's own
packed archive format, and `PersistenceManager.load` opens a `GZIPInputStream`,
so both calls could only throw `Not in GZIP format`, print it, and return null
— into nothing, because neither result was assigned. That was the two startup
warnings, and it meant both files were read twice per launch.

The real reads are `mudclient.loadModels()` and `mudclient.loadSounds()`, which
go through `GameWindow.load(byte[])` and actually unpack the archive. Both lines
are gone; `EntityHandler.load()` is now silent, and `AssetHarness` asserts that
— it captures stderr across the call and fails on any output, as well as
checking all ten definition tables still populate (798 npcs, 1290 items, 1207
objects, 409 model names) and that every object still resolves a model.

`Unable to init sounds: ArrayIndexOutOfBoundsException: 100000` was also listed
here. It does not reproduce on the current build — three consecutive launches
initialised sound with no output at all. The 100000 is `GameImage.aByteArray351`,
a shared scratch buffer, which `loadSounds()` does not touch; whatever path used
to route sound data through it is no longer taken. Noted rather than declared
fixed, because the cause was never pinned down.

## Known issues

None outstanding at startup: a launch prints only the Helvetica substitution
notice, the asset fetch, and the load steps.
