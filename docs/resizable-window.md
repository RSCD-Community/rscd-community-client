# Resizable window: fixed UI, growing world view

**Status: implemented, August 2026.** All four phases are in, with these
choices made where the plan left them open:

- The frame interior stays `windowHeight + 11` -- the historical off-by-one
  against the 12-row chat strip is kept on purpose, because mapping with 12
  would shrink the restored window by a pixel on every launch.
- Login/character-design menus were converted (rebuilt at a centred offset),
  not letterboxed -- `Menu` stores absolute coordinates, so building at the
  offset moves the clicks with the pixels and no mouse code changes.
  Typed username/password survive a mid-drag rebuild.
- The in-game dialogs (bank, shop, trade, duel, welcome, input, server
  message, logging out, wilderness warning, both abuse windows, both
  confirm windows) are centred through `loginOffsetX()`/`loginOffsetY()`.
  Each one hardcodes click rectangles against draw rectangles pair by pair
  through the whole method, so the offsets were applied to both sides
  together, method by method: where a dialog computes a shared origin
  (bank's `256 - c / 2`, shop/trade/duel's `byte0`/`byte1` and mouse-minus-
  base locals) the origin was shifted once and everything follows; where it
  uses raw literals on both sides (welcome, wilderness, abuse, input) every
  literal got the offset inline. At the minimum size both offsets are 0.
- WorldMapPanel already sized itself to the live window and needed nothing;
  ScriptPanel/CalculatorPanel/WorldsPanel got the scrim-then-translate
  treatment; alerts and ScriptPrompt already tracked the live size.
- Chat scrollback above the visible rows is lost on resize (the chat Menu
  owns its history and is rebuilt); vanilla lost the same on every relog.

Two fixes from the first play-test:

- The right-click menu's open clamp used vanilla literals (510/315 for
  windowWidth-2 / windowHeight-19). Beyond 512x334 the menu was clamped up
  to a thousand pixels from the cursor and drawRightClickMenu's
  mouse-left-the-menu check closed it the same frame -- right-click looked
  dead over the inventory and the far map. Made relative. The other literal
  mouse gates (abuse window, wilderness warning) were self-consistent with
  their equally literal draw rectangles; both sides have since been offset
  together as part of centring the dialogs.
- The chat-tab strip artwork ends at 512; the strip is now finished by
  smearing its last drawn column across the remaining width
  (extendChatStrip). The first fix only covered the two pre-login sprite
  2022 sites; the second play-test showed the in-game bar is a different
  sprite (2023, drawn 4 rows higher in drawChatMessageTabs -- those rows
  are just the raised tab tops), so the smear now runs there too. The
  strip body spans the same CHAT_TABS_HEIGHT rows on both, and the
  artwork's right edge is plain bar, so one smear serves every caller.

An aspect-ratio lock was considered during the same play-test and dropped:
the wide-window render is correct perspective (a larger FOV through the same
lens), not a skew, as the owner confirmed on a second look.

The objective, in the owner's words: the top-right in-game menus and the
bottom control bar stay exactly their current size, the current 512x346 is
the MINIMUM, and dragging the window bigger grows the world view -- you just
see more of the map. No scaling, no zoomed pixels, no layout redesign.

This document is the complete implementation plan. It was written after
auditing the actual code, and the single most important finding is this:

**The engine already supports arbitrary window sizes.** Jagex shipped RSC at
512x346, 780x558 and 1024x768, and this codebase descends from that client.
The right-side UI is written against `gameGraphics.menuDefaultWidth` (the
live framebuffer width -- e.g. `drawInventoryMenu` starts at
`menuDefaultWidth - 248`, the icon-bar hover rects at `menuDefaultWidth - 35`
in `checkMouseOverMenus`), and the bottom bar against `this.windowHeight`
(e.g. `drawChatMessageTabs` at `windowHeight - 4`, the message scrollbar gate
at `windowHeight - 66`). There are only a handful of true hardcoded-512
sites in `mudclient.java`. The work is therefore NOT a coordinate sweep of
the whole client; it is:

1. plumbing a live resize through the frame -> buffer -> camera,
2. rebuilding the handful of `Menu` objects whose coordinates were computed
   at construction time,
3. fixing the few genuinely hardcoded sites, and
4. centring the client's own fixed-geometry panels.

Keep it that small. Anything beyond the steps below is scope creep.

## Vocabulary / load-bearing facts (verified against source)

- `mudclient.windowWidth` / `windowHeight` -- the world-view size, set to
  512/334 exactly once, in `resetVariables()` region at mudclient.java:10807.
  `surfaceHeight()` = `windowHeight + CHAT_TABS_HEIGHT(12)` = the full
  drawing buffer height (346) -- the chat-tab strip lives in the extra rows.
- `gameGraphics` = `new GameImageMiddleMan(windowWidth, surfaceHeight(),
  4000, this)` at mudclient.java:5361, followed immediately by
  `setDimensions(0, 0, windowWidth, surfaceHeight())`.
  `GameImage` allocates `imagePixelArray = new int[width * height]` and is
  its own `ImageProducer` (see `imageConsumer.setDimensions/setPixels` in
  GameImage.java) -- the AWT `Image` blitted to screen is produced from that
  array.
- `gameCamera` = `new Camera(gameGraphics, 15000, 15000, 1000)`. The Camera
  constructor CACHES `gameImage.imagePixelArray` into `anIntArray437` and
  `menuDefaultWidth/2` into `halfWidth/halfHeight`. `setCameraSize(...)`
  (Camera.java:224) re-derives the halves and allocates
  `cameraVariables[halfWindowHeight2 + halfWindowHeight]` -- one per screen
  row -- so variable height is already designed for. It does NOT re-read
  `imagePixelArray`; that is one of the two real resize seams.
- `GameFrame` (a `Frame`) already takes a `resizable` flag and already
  measures its real insets (`decorations()`); the client currently passes
  `false` from mudclient.java:771:
  `mc.createWindow(mc.windowWidth, mc.windowHeight + 11, "RSCD Community Client", false)`.
  (Note the `+ 11` -- the historical constant predates `CHAT_TABS_HEIGHT`;
  when touching this line make the inner size `surfaceHeight()` and verify
  the bottom row of the chat tabs is not clipped, which is presumably why 11
  vs 12 never mattered under the old fixed insets.)
- Fixed-geometry screens of our own (do NOT relayout these): `ScriptPanel`,
  `CalculatorPanel`, `WorldMapPanel`, `WorldsPanel` (SCREEN_W/H 512x346),
  and the login backdrop (`LOGIN_VIEW_WIDTH/HEIGHT` 512x200 sprites).
  Precedent for handling them: the pre-login Worlds screen already centres
  itself -- mudclient.java ~9980:
  `offsetX = Math.max(0, (canvasWidth() - WorldsPanel.SCREEN_W) / 2)`.

## Phase 1 -- resize plumbing (the only new machinery)

### 1a. GameFrame delivers resize events

In `GameFrame`: add `AWTEvent.COMPONENT_EVENT_MASK` to the `enableEvents`
call, and in `processEvent` handle `ComponentEvent.COMPONENT_RESIZED`:
compute the inner size (`getSize()` minus `decorations()` insets, same
arithmetic the paint path already uses) and hand it to the game with a new
method on `GameWindow` (e.g. `frameResized(int innerW, int innerH)`).

Do NOT apply anything from the AWT thread. `frameResized` only stores
`pendingWidth/pendingHeight` volatiles.

### 1b. mudclient applies the pending size between frames

At the top of the game loop / start of `drawGame()` (before anything reads
`windowWidth`), if a pending size differs from current:

```
newW = clamp(pendingW, 512, 2560);          // world view width
newH = clamp(pendingH - CHAT_TABS_HEIGHT, 334, 1440);
```

Then, in order:

1. `this.windowWidth = newW; this.windowHeight = newH;`
2. `gameGraphics.resize(newW, surfaceHeight())` -- new method, see 1c.
3. `gameGraphics.setDimensions(0, 0, newW, surfaceHeight());`
4. `gameCamera.setCameraSize(newW/2, newH/2, newW/2, newH/2, newW,
   this.cameraSizeInt);` -- same call shape as mudclient.java:5383. Keeping
   `cameraSizeInt` unchanged keeps the pixel scale identical, so a bigger
   viewport shows MORE WORLD at the same size -- exactly the objective.
5. Rebuild the constructed-at-fixed-coordinates menus (Phase 2).
6. Persist: `Config.settings().set("window_width", ...)/("window_height",
   ...); save();` -- but only when the size has been stable for ~500ms or on
   focus events, so a drag does not write the file dozens of times. (A
   simple `lastResizeAt` timestamp check in the same loop is enough.)

Startup: read `window_width`/`window_height` from settings (defaults
512/346), clamp identically, use for the initial `windowWidth/windowHeight`
before `createWindow`, and flip the `createWindow` resizable flag to `true`.
Enforce the minimum at the AWT level too if cheap
(`gameFrame.setMinimumSize`), but the clamp in 1b is the authority --
some window managers ignore minimum sizes.

### 1c. `GameImage.resize(int width, int height)`

New method on `GameImage` (NOT a new instance -- the instance owns every
loaded sprite and the whole client holds references to it):

- reallocate `imagePixelArray = new int[width * height]`
- update `menuDefaultWidth/menuDefaultHeight` (and the `imageWidthUnused/
  imageHeightUnused` pair set beside them in the constructor)
- recreate whatever the constructor built to hand pixels to AWT: the
  `ImageProducer` consumer registration and the component-created `Image`.
  Read the constructor and `createImageMethod/drawGraphics` path first and
  mirror it exactly -- the consumer must get a fresh `setDimensions`, and a
  stale `Image` of the old size must not be blitted again.

Then in `Camera.setCameraSize`, add one line: re-read
`this.anIntArray437 = this.gameImage.imagePixelArray;` (the constructor
already proves the field access works; `gameImage` is a field). That makes
`setCameraSize` the complete camera-side resize and nothing else in Camera
changes. Verify with grep that nothing else caches `imagePixelArray`
(auditted: Camera is the only outside cacher; GameImage's own methods read
the field live).

`EngineHandle` and `Model` do not cache screen dimensions -- do not touch
them.

## Phase 2 -- rebuild the built-once Menus

`Menu` components store absolute coordinates from construction time. Four
in-game builders bake in the then-current size:

- `drawGameMenu()` (mudclient.java:6547) -- the chat input + message panes.
  It ALREADY reconstructs from scratch (`this.gameMenu = new Menu(...)`),
  so it is the rebuild mechanism for free. First make its coordinates
  relative -- they are pure bottom-anchored constants baked from 334:
  `269 = windowHeight - 65`, `324 = windowHeight - 10`, widths
  `502/498 = windowWidth - 10/14`. Convert, then simply call
  `drawGameMenu()` again during the resize step. Chat text typed mid-resize
  is lost with the old handle; preserve it by reading
  `gameMenu.getText(chatHandle)` before and `updateText` after if trivial,
  otherwise accept the reset (one line of typed chat, during a window drag,
  is a fair casualty -- note it in the commit).
- `spellMenu`, `friendsMenu`, `questMenu` (mudclient.java:5365-5373, built
  once at startup) -- the right-side panel content lists. Audit each
  builder: their x origins are the right-panel edge (expect constants baked
  from 512 or already `menuDefaultWidth`-relative). Make them
  `windowWidth`-relative where they are not, extract the three builds into
  a small `buildSideMenus()` if they are inline, and call it from the
  resize step.

The login-screen menus (`menuLogin`, `menuNewUser`, `menuWelcome` at
mudclient.java:9979-9998, plus `characterDesignMenu`) are built once at
startup with coordinates centred on the 512x346 surface. Audit their
builders the same way: convert the x-centres to `windowWidth/2`-relative
and y to proportional/centred equivalents, and re-run the builders on
resize. The login backdrop sprite is fixed 512-wide: draw it centred
(`(windowWidth - 512) / 2`) with black either side -- do not scale it.
The Worlds screen already centres itself; use its `offsetX` code as the
model. If the login-menu audit turns out hairy, the acceptable minimal
fallback is: while `loggedIn != 1`, letterbox everything pre-login into a
centred 512x346 region (draw offset + mouse offset) rather than converting
every coordinate -- choose whichever is genuinely less change after reading
the builders, and say which was chosen.

## Phase 3 -- the true hardcoded sites

All of them in `mudclient.java` (current line numbers):

- `:2357` and `:2387` -- `g.fillRect(0, 0, 512, 356)` in `method4()` (the
  asset-load failure screen) and its sibling. Use
  `windowWidth`/`surfaceHeight()`... note these draw through a raw AWT
  `Graphics`, not the game raster, so use the frame's inner size.
- `:10807-10808` -- the 512/334 initialisation; becomes the
  settings-loaded value (Phase 1b).
- `createWindow(...windowHeight + 11...)` at `:771` (Phase 1a note).
- `drawBankBox()` `:2453` (`c = 408, c1 = 334`) and every other fixed-size
  dialog (`drawShopBox`, `drawTradeWindow`, `drawDuelWindow`, the confirm
  windows, `drawWelcomeBox`, `drawServerMessageBox`, `drawInputBox`,
  `drawWildernessWarningBox`, `drawLoggingOutBox`, the abuse windows,
  `drawQuestionMenu`): these keep their sizes -- verify each computes its
  top-left from the live size (centre = `(windowWidth - w) / 2` style). Any
  that use absolute literals (they assumed 512x334), convert to centred.
  Their internal layout is relative to that top-left; do not touch it.
  The click-handling twin of each dialog uses the same rectangle -- keep
  draw and click deriving from one computed origin so they cannot drift.
- `drawRightClickMenu`/`drawInventoryRightClickMenu` position at the mouse
  -- fine as-is; only verify their clamp-to-edge uses windowWidth/Height,
  not 512/334 literals.
- Sleep-word screen (fatigue captcha) if it draws full-screen: same
  centre-or-relative audit.

Grep patterns for the audit (numbers that bake in the old size without
containing "512"): `494`, `502`, `498`, `510`, `269`, `324`, `329`, `330`,
`346`, `356`. Judge each hit in context -- most are unrelated ids or
sprite numbers; only screen-position uses matter.

## Phase 4 -- the client's own screens

One shared offset, computed where needed:

```
panelOffsetX = (windowWidth - 512) / 2;        // >= 0 by the min clamp
panelOffsetY = (surfaceHeight() - 346) / 2;
```

- `ScriptPanel`, `CalculatorPanel`, `WorldMapPanel`: drawn through
  `Skin.open(...)` (a Graphics2D). In each panel's `draw`, first paint the
  scrim across the FULL window (it already takes windowWidth/surfaceHeight
  -- keep that), then `g.translate(panelOffsetX, panelOffsetY)` before the
  content so the 512x346 layout sits centred. In each panel's mouse/click
  and `update` handlers, subtract the same offsets from mouseX/mouseY
  before hit-testing. The offsets should come from one accessor on
  mudclient, not be recomputed in five places. WorldMapPanel is
  full-screen by design -- decide whether it should instead grow with the
  window (its map surface would love the room); if that is more than a
  trivial width/height read, leave it centred at 512x346 and file it as a
  follow-up. Do not restructure panel internals.
- `WorldsPanel` pre-login: already centred (the `offsetX` block) -- verify
  it uses the live canvas size and needs nothing.
- `ScriptPrompt` and `Skin.alert`: they take width/height parameters and
  self-centre -- verify, expect no change.
- The movie recorder captures `gameImage` -- it follows the new size
  automatically. A resize mid-recording changes the frame size in the
  stream; note it as a known minor artifact, do not fix.

## What NOT to do

- No pixel scaling / zoom modes, no fullscreen mode, no separate render
  thread, no double-buffer rework.
- No relayout of the game UI: the inventory stays 5 wide, the minimap stays
  its size, the chat stays 5 lines. Fixed UI, bigger world -- that is the
  whole feature.
- Do not recreate `GameImageMiddleMan`/`Camera`/`EngineHandle` instances on
  resize -- resize the existing ones (Phase 1c). Every subsystem holds
  references to them, and GameImage owns all loaded sprites.
- Do not touch `EngineHandle`, `Model`, `Scene` math, or anything in
  `util/` beyond the two settings keys.
- Keep the house style: Java-6-ish source (no diamond operator, no
  try-with-resources), 3-space indent, comments in the codebase's own voice
  explaining WHY, not what.

## Verification protocol

After each phase, `./build.sh` must pass (it compiles and does a real-load
test of the jar). The build machine has no display for gameplay testing --
the owner play-tests. Structure the work so each phase is independently
runnable and report what to look at:

1. Phase 1 alone: window drags bigger, world view grows (more map visible),
   right-side icons/panels and chat follow the edges. Expected breakage at
   this point: chat input row still at old y (Phase 2), dialogs off-centre
   (Phase 3), F2 screens top-left (Phase 4).
2. Phase 2: chat input usable at every size; spell/friends/quest tabs
   fully clickable; login screen usable at non-default sizes.
3. Phase 3: open bank/shop/trade/duel at a large size -- each dialog
   centred, every button clicks where it draws (click-vs-draw drift is THE
   bug class of this project; test clicks near dialog edges).
4. Phase 4: F2 menu, calculators, world map, prompts centred; Escape/keys
   unchanged; mouse hits track the centred content.
5. Regression at exactly 512x346: pixel-identical behaviour to today is
   the acceptance bar for the minimum size -- the vanilla look must survive.
6. Cross-check the F1 zoom, the new fog-of-war-off mode, and zooming out
   fully at a big window size: `setCameraSize` + the existing per-frame
   zoom1-4 block interact here, and this combination is the showcase
   (big window + zoom out + no fog = the whole loaded map).

## Settings

- `window_width` / `window_height` in settings.ini -- last applied size,
  clamped on read. Absent keys = 512x346, today's exact behaviour.
- No new F2 settings row: the window edge IS the control.
