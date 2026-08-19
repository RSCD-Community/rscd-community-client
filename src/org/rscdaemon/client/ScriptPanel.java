package org.rscdaemon.client;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.rscdaemon.client.util.Config;

/*
 * The script menu -- SkullOrca's bot panel, drawn inside the client.
 *
 * SkullOrca could not do this. It loaded Jagex's signed, obfuscated jar
 * reflectively into a Swing JPanel, so it had no way to draw a single pixel
 * inside the game view. What it did instead was a swap: BotPanel.paint() drew
 * the buttons onto the panel, and the applet was hidden on top of them with
 *
 *    clientInstance.setVisible( false )   // Bot.java:327 -- menu visible
 *    clientInstance.setVisible( true  )   // Bot.java:476 -- "Back to Game"
 *
 * That is why the SkullOrca source has no hotkey and no way back to the menu:
 * once the heavyweight applet was showing it owned the keyboard, and BotPanel's
 * KeyAdapter never fired again. The menu opened once, at startup, after the
 * botrsc.com login, and "Back to Game" was a one-way door.
 *
 * Here the client is ours, so the menu is drawn over the game in the client's
 * own render loop, from the client's own pixel buffer, and F2 toggles it. The
 * game keeps running underneath -- a script does not pause because someone
 * opened the menu to look at it.
 *
 * The layout is SkullOrca's, deliberately: the button geometry is his own --
 * 115x35 down the left at x=5, 40 apart -- which lands inside 512x334 without
 * changing a number, and so are the two scroll triangles on the right edge of
 * the list and the centred alert box across the middle.
 *
 * The colours are not. This was 0,170,0 on black, brightening to 0,255,0 under
 * the cursor, because that is what a bot panel written against a Jagex applet
 * in 2008 looked like. It is now drawn through Skin, which is the same theme
 * the Worlds screen uses -- carved stone and gold off the loading splash. Two
 * screens this client added, one look. Nothing in the geometry moved.
 */
final class ScriptPanel {
   /* Vertical rhythm for the status block. Skin's fonts set the rest. */
   private static final int LINE = 14;

   /* The content pane: outer frame, then the list box inside it. SkullOrca drew
      125,5,384,334 and 130,10,374,284 on a 514x344 panel; these are the same
      rectangles trimmed to the 512x334 game view. */
   private static final int PANE_X = 125;
   private static final int PANE_Y = 5;
   private static final int PANE_W = 382;
   private static final int PANE_H = 324;
   private static final int LIST_X = 130;
   private static final int LIST_Y = 10;
   private static final int LIST_W = 372;
   private static final int LIST_H = 264;
   /* 16 rows of 14px starting 24px down leaves the list box's own border
      clear at 248 of 264. */
   private static final int ROWS = 16;

   private static final int PAGE_SCRIPTS = 0;
   private static final int PAGE_SETTINGS = 1;

   private final mudclient rs;
   private final Button[] buttons;

   private boolean open;
   private int page = PAGE_SCRIPTS;

   /* Refreshed when the menu opens and when Load is pressed, not per frame:
      listing a directory 50 times a second to draw the same names is waste. */
   private String[] names = new String[0];
   private int listOffset;

   private String alertMessage = "";
   private long alertUntil;

   /* Hold-to-repeat for the two scroll triangles; see ScrollRepeat's own
      comment for why Menu's lists never needed one of these. */
   private final ScrollRepeat scrollUpRepeat = new ScrollRepeat();
   private final ScrollRepeat scrollDownRepeat = new ScrollRepeat();

   ScriptPanel(mudclient rs) {
      this.rs = rs;
      this.buttons = this.buildButtons();
   }

   boolean isOpen() {
      return this.open;
   }

   void toggle() {
      if (this.open) {
         this.close();
      } else {
         this.open();
      }
   }

   void open() {
      this.open = true;
      this.refresh();
   }

   void close() {
      this.open = false;
   }

   /*
    * SkullOrca's showAlertMessage( String , int ) -- a message across the middle
    * for n seconds, used for everything that has no better place to go.
    */
   void showAlertMessage(String message, int seconds) {
      this.alertMessage = message;
      this.alertUntil = System.currentTimeMillis() + seconds * 1000L;
   }

   private void refresh() {
      this.names = this.rs.scripts().list();
      /* Clamp to the last full page rather than jumping to the top -- deleting
         one script while scrolled should not lose the reader's place. */
      if (this.listOffset > this.maxOffset()) {
         this.listOffset = this.maxOffset();
      }
   }

   /*
    * ---- widgets ----
    *
    * ButtonUI, ported. SkullOrca's fields and accessors kept as they were --
    * text, position, tabIndex, polygon, clicked() -- because the anonymous
    * subclass with an overridden clicked() is how every button in that source
    * was written, and there is no reason to invent a different shape.
    *
    * polygon is gone: GameImage has no fillPolygon, so the two buttons that
    * used one (Start's triangle, Stop's square) draw their glyph from the
    * icon field instead.
    */
   private abstract static class Button {
      static final int ICON_NONE = 0;
      static final int ICON_PLAY = 1;
      static final int ICON_STOP = 2;

      final String text;
      final Rectangle position;
      final int icon;

      Button(String text, Rectangle position) {
         this(text, position, ICON_NONE);
      }

      Button(String text, Rectangle position, int icon) {
         this.text = text;
         this.position = position;
         this.icon = icon;
      }

      abstract void clicked();

      /** Drawn dim and refusing clicks. */
      boolean enabled() {
         return true;
      }

      /** Red rather than green, the way SkullOrca drew Stop. */
      boolean danger() {
         return false;
      }

      /** What to write on it now, for buttons whose label changes. */
      String label() {
         return this.text;
      }

      /** Which glyph now, for the one button that is Start or Stop. */
      int iconNow() {
         return this.icon;
      }

      boolean hit(int x, int y) {
         return x >= this.position.x
            && x <= this.position.x + this.position.width
            && y >= this.position.y
            && y <= this.position.y + this.position.height;
      }
   }

   private Button[] buildButtons() {
      return new Button[]{
         /* SkullOrca's Load did nothing -- clicked() was empty, because the
            script handler behind it was never written. Here it has somewhere to
            go, so it opens the picker. */
         new Button("Load", new Rectangle(5, 15, 75, 35)) {
            @Override
            void clicked() {
               ScriptPanel.this.page = PAGE_SCRIPTS;
               ScriptPanel.this.listOffset = 0;
               ScriptPanel.this.refresh();
               if (ScriptPanel.this.names.length == 0) {
                  ScriptPanel.this.showAlertMessage("NO SCRIPTS IN " + mudclient.scriptDirectory().getName(), 3);
               }
            }
         },
         /* One button, two states: SkullOrca declared Start and Stop as separate
            overlapping buttons at 85,15 and commented Start out. Nothing is
            gained by shipping the same bug. */
         new Button("Stop", new Rectangle(85, 15, 35, 35), Button.ICON_STOP) {
            @Override
            void clicked() {
               ScriptRunner runner = ScriptPanel.this.rs.scripts();
               if (runner.isRunning()) {
                  String was = runner.getScriptName();
                  runner.stop();
                  ScriptPanel.this.showAlertMessage("STOPPED " + was.toUpperCase(), 2);
               } else if (runner.getLastName().length() > 0) {
                  /* A server restart kills the script; Play brings back the
                     last one -- same arguments AND the same recorded prompt
                     answers, so a kicked bot is one click from working again
                     with nothing to re-type. Changing the setup means picking
                     the script from the list instead. */
                  ScriptPanel.this.resume(runner.getLastName());
               } else {
                  ScriptPanel.this.showAlertMessage("A SCRIPT MUST BE LOADED FIRST", 2);
               }
            }

            @Override
            boolean danger() {
               return ScriptPanel.this.rs.scripts().isRunning();
            }

            @Override
            int iconNow() {
               return ScriptPanel.this.rs.scripts().isRunning() ? Button.ICON_STOP : Button.ICON_PLAY;
            }
         },
         new Button("Screenshot", new Rectangle(5, 55, 115, 35)) {
            @Override
            void clicked() {
               /* Straight into the client's own F12 path, which already writes
                  into MEDIA_DIR/<character>/screenshotN.png. */
               if (ScriptPanel.this.rs.takeScreenshot(false)) {
                  ScriptPanel.this.showAlertMessage("SCREENSHOT SAVED", 2);
               } else {
                  ScriptPanel.this.showAlertMessage("COULD NOT SAVE SCREENSHOT", 2);
               }
            }
         },
         new Button("Record", new Rectangle(5, 95, 115, 35)) {
            @Override
            void clicked() {
               ScriptPanel.this.rs.toggleRecording();
               ScriptPanel.this.showAlertMessage(ScriptPanel.this.rs.recording ? "RECORDING" : "MOVIE SAVED", 2);
            }

            @Override
            String label() {
               return ScriptPanel.this.rs.recording ? "Stop Rec" : "Record";
            }

            @Override
            boolean danger() {
               return ScriptPanel.this.rs.recording;
            }
         },
         new Button("Settings", new Rectangle(5, 135, 115, 35)) {
            @Override
            void clicked() {
               ScriptPanel.this.page = PAGE_SETTINGS;
            }
         },
         /* Both of these were buttons in SkullOrca with empty clicked() bodies.
            Both are now built. */
         new Button("World Map", new Rectangle(5, 175, 115, 35)) {
            @Override
            void clicked() {
               /* Over the menu rather than instead of it, so Escape comes back
                  here and not straight out to the game. */
               ScriptPanel.this.rs.worldMapPanel().open();
            }
         },
         new Button("Calculators", new Rectangle(5, 215, 115, 35)) {
            @Override
            void clicked() {
               /* Instead of the menu, not over it: the calculator screen has
                  its own left strip where these buttons are, and its Escape
                  walks back here (see CalculatorPanel.handleKey). */
               ScriptPanel.this.close();
               ScriptPanel.this.rs.calculatorPanel().open();
            }
         },
         new Button("Back to Game", new Rectangle(5, 295, 115, 35)) {
            @Override
            void clicked() {
               ScriptPanel.this.close();
            }
         }
      };
   }

   /*
    * ---- input ----
    *
    * The frame's click, taken before the game sees it. Returning it to the game
    * would walk the character to wherever the button happened to be.
    */
   void handleClick(int mouseX, int mouseY, int button) {
      if (button == 0) {
         return;
      }

      // The panel is drawn centred; bring the click into its coordinates.
      mouseX -= this.rs.loginOffsetX();
      mouseY -= this.rs.loginOffsetY();

      for (int i = 0; i < this.buttons.length; i++) {
         Button b = this.buttons[i];
         if (b.hit(mouseX, mouseY)) {
            // Including the dim ones: their whole job is to say they are not
            // built yet, and a button that swallows the click without a word is
            // exactly what SkullOrca shipped.
            b.clicked();
            return;
         }
      }

      // The two scroll triangles, at the right edge of the list box.
      if (mouseX >= LIST_X + LIST_W - 14 && mouseX <= LIST_X + LIST_W - 4) {
         if (mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15) {
            if (this.listOffset > 0) {
               this.listOffset--;
            }

            return;
         }

         if (mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5) {
            if (this.listOffset < this.maxOffset()) {
               this.listOffset++;
            }

            return;
         }
      }

      int row = this.rowAt(mouseX, mouseY);
      if (row == -1) {
         return;
      }

      if (this.page == PAGE_SCRIPTS) {
         int index = this.listOffset + row;
         if (index < this.names.length) {
            this.run(this.names[index]);
         }
      } else if (row < SETTINGS.length) {
         this.toggleSetting(row);
      }
   }

   /*
    * Called every tick regardless of whether there was a click this tick, so
    * the scroll triangles can repeat while the button stays down instead of
    * moving the list one row per press. handleClick above still owns the
    * single-row move on the click itself; this only ever fires the row after
    * that, once the hold has run long enough -- see ScrollRepeat.
    */
   void tick(int mouseX, int mouseY, int mouseHeld) {
      if (!this.open) {
         this.scrollUpRepeat.fire(false);
         this.scrollDownRepeat.fire(false);
         return;
      }

      mouseX -= this.rs.loginOffsetX();
      mouseY -= this.rs.loginOffsetY();

      boolean held = mouseHeld != 0;
      int x = LIST_X + LIST_W - 14;
      boolean onUp = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15;
      boolean onDown = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5;

      if (this.scrollUpRepeat.fire(held && onUp) && this.listOffset > 0) {
         this.listOffset--;
      }

      if (this.scrollDownRepeat.fire(held && onDown) && this.listOffset < this.maxOffset()) {
         this.listOffset++;
      }
   }

   /*
    * The mouse wheel, arriving from GameFrame by way of mudclient's
    * handleMouseWheel. Only spent over the list box itself, the same as
    * WorldMapPanel treats its own legend.
    */
   void wheel(int notches, int mouseX, int mouseY) {
      if (!this.open || notches == 0) {
         return;
      }

      mouseX -= this.rs.loginOffsetX();
      mouseY -= this.rs.loginOffsetY();

      if (mouseX < LIST_X || mouseX > LIST_X + LIST_W || mouseY < LIST_Y || mouseY > LIST_Y + LIST_H) {
         return;
      }

      int scrolled = this.listOffset + notches * 3;
      if (scrolled < 0) {
         scrolled = 0;
      } else if (scrolled > this.maxOffset()) {
         scrolled = this.maxOffset();
      }

      this.listOffset = scrolled;
   }

   /** Escape closes it, the way every menu drawn over a game has since. */
   boolean handleKey(int key) {
      if (this.open && key == 27) {
         this.close();
         return true;
      }

      return false;
   }

   private int maxOffset() {
      int count = this.page == PAGE_SCRIPTS ? this.names.length : SETTINGS.length;
      return count > ROWS ? count - ROWS : 0;
   }

   private int rowAt(int mouseX, int mouseY) {
      if (mouseX < LIST_X + 4 || mouseX > LIST_X + LIST_W - 16) {
         return -1;
      }

      int top = LIST_Y + 24;
      if (mouseY < top || mouseY >= top + ROWS * LIST_ROW) {
         return -1;
      }

      return (mouseY - top) / LIST_ROW;
   }

   private static final int LIST_ROW = 14;

   /*
    * Start a script by name, with no arguments -- the picker has nowhere to
    * type them.
    *
    * There is no text field on this panel on purpose. GameWindow.keyDown() is
    * final and appends every typed character to inputText/inputMessage before
    * any hook of ours is reached, so an on-panel text box would type into the
    * game's chat line at the same time. /start Name(arg,arg) still takes
    * arguments and always will.
    */
   private void run(String name) {
      this.run(name, new String[0]);
   }

   private void run(String name, String[] args) {
      ScriptRunner runner = this.rs.scripts();

      try {
         Methods loaded = runner.load(name);
         runner.start(loaded, name, args);
         this.showAlertMessage("STARTED " + name.toUpperCase(), 2);
      } catch (ScriptRunner.ScriptException var4) {
         // The full compiler output is worth more than a two-second box, so it
         // also goes to the chat log where it can be scrolled back through.
         this.showAlertMessage("COULD NOT COMPILE " + name.toUpperCase(), 3);
         this.rs.displayMessage("@gry@ " + var4.getMessage(), 3, 0);
      }
   }

   /* Play: the last script again, exactly as it was set up. Its prompts are
      answered from the recording, so nothing asks for anything. */
   private void resume(String name) {
      ScriptRunner runner = this.rs.scripts();

      try {
         Methods loaded = runner.load(name);
         runner.startAuto(loaded, name);
         this.showAlertMessage("STARTED " + name.toUpperCase(), 2);
      } catch (ScriptRunner.ScriptException var4) {
         this.showAlertMessage("COULD NOT COMPILE " + name.toUpperCase(), 3);
         this.rs.displayMessage("@gry@ " + var4.getMessage(), 3, 0);
      }
   }

   /*
    * ---- settings ----
    *
    * The first four are client flags a script can already set through the
    * Methods API; the panel is a second way in, not a second copy of the state.
    *
    * The next two are STS's, and are the reason the stat panel is Jagex's two
    * tabs again: the overlay is where the extra goes.
    *
    * The last three came off the Game options menu, where RSCD had added them
    * under a "Client assists" header that Jagex's account-management text used
    * to occupy. Same reasoning: the game's own menus go back to what Jagex
    * drew, and everything after that is on this page. They are still server-
    * side settings and still go up as packet 157.
    */
   private static final String[] SETTINGS = {
      "Graphics",
      "Auto login",
      "Force stat menu",
      "Script debug",
      "Autocast",
      "Status overlay",
      "Show HP",
      "Hide roofs",
      "Auto screenshots",
      "Fightmode selector",
      "Fog of war"
   };

   private boolean settingValue(int i) {
      switch (i) {
         case 0:
            return this.rs.drawGfx;
         case 1:
            return this.rs.autoLogin;
         case 2:
            return this.rs.forceStatMenu;
         case 3:
            return this.rs.scriptDebug;
         case 4:
            return this.rs.autocastEnabled;
         case 5:
            return this.rs.statusOverlay;
         case 6:
            return this.rs.showHealthBars;
         case 7:
            /* showRoof is "roofs are drawn", so the row that says Hide roofs
               is on when the flag is off. The Game options menu read it the
               same way round. */
            return !this.rs.showRoof;
         case 8:
            return this.rs.autoScreenshot;
         case 9:
            return this.rs.combatWindow;
         case 10:
            return this.rs.showFog;
         default:
            return false;
      }
   }

   private void toggleSetting(int i) {
      switch (i) {
         case 0:
            this.rs.drawGfx = !this.rs.drawGfx;
            break;
         case 1:
            this.rs.autoLogin = !this.rs.autoLogin;
            break;
         case 2:
            this.rs.forceStatMenu = !this.rs.forceStatMenu;
            break;
         case 3:
            this.rs.scriptDebug = !this.rs.scriptDebug;
            break;
         case 4:
            this.rs.autocastEnabled = !this.rs.autocastEnabled;
            break;
         case 5:
            this.rs.statusOverlay = !this.rs.statusOverlay;
            break;
         case 6:
            this.rs.showHealthBars = !this.rs.showHealthBars;
            break;
         case 7:
            this.rs.setClientAssist(4, !this.rs.showRoof);
            break;
         case 8:
            this.rs.setClientAssist(5, !this.rs.autoScreenshot);
            break;
         case 9:
            this.rs.setClientAssist(6, !this.rs.combatWindow);
            break;
         case 10:
            /* Client-side only, so it is ours to remember: the server has no
               say in how far a player's world fades. */
            this.rs.showFog = !this.rs.showFog;
            Config.settings().set("fog", this.rs.showFog);
            Config.settings().save();
      }
   }

   /*
    * ---- drawing ----
    *
    * Called last in the frame so it sits over the panels, the chat and the
    * script's own ToShow() overlay.
    */
   void draw(GameImageMiddleMan gg, int mouseX, int mouseY) {
      Graphics2D g = Skin.open(gg, this.rs.windowWidth, this.rs.surfaceHeight());
      /* The layout is fixed 512x334; in a bigger window it sits centred. The
         scrim stays full-window, everything after it draws translated, and the
         mouse is shifted the same amount so hover and hit agree. */
      int ox = this.rs.loginOffsetX();
      int oy = this.rs.loginOffsetY();

      try {
         if (this.open) {
            /* Not a solid fill: SkullOrca had to blank the panel because the game
               was a separate component, but leaving the world faintly visible
               behind the menu is how you watch what a script is doing while you
               are reading about it. */
            Skin.scrim(g, this.rs.windowWidth, this.rs.surfaceHeight(), 226);
            g.translate(ox, oy);
            mouseX -= ox;
            mouseY -= oy;

            for (int i = 0; i < this.buttons.length; i++) {
               this.drawButton(g, this.buttons[i], mouseX, mouseY);
            }

            Skin.panel(g, PANE_X, PANE_Y, PANE_W, PANE_H);
            Skin.well(g, LIST_X, LIST_Y, LIST_W, LIST_H);

            if (this.page == PAGE_SCRIPTS) {
               this.drawScripts(g, mouseX, mouseY);
            } else {
               this.drawSettings(g, mouseX, mouseY);
            }

            this.drawScrollArrows(g, mouseX, mouseY);
            this.drawStatus(g);
            g.translate(-ox, -oy);
         }

         /* Outside the if: an alert raised by something other than the menu -- a
            script that died, a screenshot taken with F12 -- still has to show. */
         this.drawAlert(g);
      } finally {
         g.dispose();
      }
   }

   private void drawButton(Graphics2D g, Button b, int mouseX, int mouseY) {
      int x = b.position.x;
      int y = b.position.y;
      int w = b.position.width;
      int h = b.position.height;

      boolean hover = b.hit(mouseX, mouseY);
      int state;
      if (!b.enabled()) {
         state = Skin.DISABLED;
      } else if (b.danger()) {
         state = Skin.DANGER;
      } else {
         state = hover ? Skin.HOVER : Skin.NORMAL;
      }

      /* The one button that carries a glyph instead of a word gets a null
         label, so Skin draws the face and the glyph goes on top of it. */
      int glyph = b.iconNow();
      Skin.button(g, x, y, w, h, glyph == Button.ICON_NONE ? b.label() : null, state);

      if (glyph != Button.ICON_NONE) {
         int ink = state == Skin.DISABLED ? Skin.TEXT_OFF : state == Skin.DANGER ? Skin.EMBER_TEXT : Skin.GOLD_HI;
         g.setColor(Skin.colour(ink));
         if (glyph == Button.ICON_STOP) {
            g.fillRect(x + w / 2 - 5, y + h / 2 - 5, 11, 11);
         } else {
            g.fillPolygon(
               new int[] { x + w / 2 - 5, x + w / 2 + 7, x + w / 2 - 5 },
               new int[] { y + h / 2 - 7, y + h / 2, y + h / 2 + 7 },
               3);
         }
      }
   }

   private void drawScripts(Graphics2D g, int mouseX, int mouseY) {
      Skin.heading(g, LIST_X + 8, LIST_Y + 17, LIST_W - 24, "SCRIPTS");

      if (this.names.length == 0) {
         Skin.text(g, "Nothing in " + mudclient.scriptDirectory().getPath(),
            LIST_X + 8, LIST_Y + 42, Skin.FONT_BODY, Skin.TEXT_DIM);
         return;
      }

      int hovered = this.rowAt(mouseX, mouseY);
      String running = this.rs.scripts().isRunning() ? this.rs.scripts().getScriptName() : null;

      for (int row = 0; row < ROWS; row++) {
         int index = this.listOffset + row;
         if (index >= this.names.length) {
            break;
         }

         int y = LIST_Y + 24 + row * LIST_ROW;
         boolean isRunning = this.names[index].equals(running);
         Skin.row(g, LIST_X + 4, y, LIST_W - 20, LIST_ROW, row == hovered, isRunning);

         Skin.text(g, this.names[index], LIST_X + 10, y + 11, Skin.FONT_BODY,
            isRunning ? Skin.GOLD_MAX : Skin.TEXT);
         if (isRunning) {
            Skin.lamp(g, LIST_X + LIST_W - 78, y + 7, true);
            Skin.text(g, "running", LIST_X + LIST_W - 70, y + 11, Skin.FONT_SMALL, Skin.RUNE);
         }
      }
   }

   private void drawSettings(Graphics2D g, int mouseX, int mouseY) {
      Skin.heading(g, LIST_X + 8, LIST_Y + 17, LIST_W - 24, "SETTINGS");

      int hovered = this.rowAt(mouseX, mouseY);

      for (int row = 0; row < SETTINGS.length; row++) {
         int y = LIST_Y + 24 + row * LIST_ROW;
         boolean on = this.settingValue(row);
         Skin.row(g, LIST_X + 4, y, LIST_W - 20, LIST_ROW, row == hovered, false);

         Skin.text(g, SETTINGS[row], LIST_X + 10, y + 11, Skin.FONT_BODY, Skin.TEXT);
         Skin.text(g, on ? "ON" : "OFF", LIST_X + LIST_W - 60, y + 11, Skin.FONT_SMALL,
            on ? Skin.RUNE : Skin.TEXT_OFF);
      }

      int foot = LIST_Y + 24 + (SETTINGS.length + 1) * LIST_ROW;
      Skin.rule(g, LIST_X + 10, foot - 11, LIST_W - 30);
      String[] note = {
         "The first four are the flags SetGfx, AutoLogin,",
         "ForceStatMenu and /debug set from a script.",
         "Then STS's two: text over the game view, and HP",
         "bars for anything already seen. The next three",
         "came off the Game options menu, and the server",
         "still remembers them. Fog of war, STS's name",
         "for it, off draws the whole loaded map."
      };

      for (int i = 0; i < note.length; i++) {
         Skin.text(g, note[i], LIST_X + 10, foot + i * LINE, Skin.FONT_SMALL, Skin.TEXT_DIM);
      }
   }

   private void drawScrollArrows(Graphics2D g, int mouseX, int mouseY) {
      if (this.maxOffset() != 0) {
         int x = LIST_X + LIST_W - 14;
         boolean upHot = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15;
         boolean downHot = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5;
         Skin.arrow(g, x, LIST_Y + 5, 10, 10, true, upHot);
         Skin.arrow(g, x, LIST_Y + LIST_H - 15, 10, 10, false, downHot);
      }
   }

   private void drawStatus(Graphics2D g) {
      ScriptRunner runner = this.rs.scripts();
      int y = LIST_Y + LIST_H + 20;

      String name = runner.getScriptName();
      if (name.length() == 0) {
         name = "none";
      }

      boolean live = runner.isRunning();
      Skin.text(g, "script: " + name, PANE_X + 10, y, Skin.FONT_BODY, Skin.GOLD_HI);
      Skin.text(g, "state: " + (live ? "running" : "idle"), PANE_X + 10, y + LINE, Skin.FONT_BODY,
         live ? Skin.RUNE : Skin.TEXT_DIM);
      Skin.text(g, "uptime: " + elapsed(runner.runtimeMillis()), PANE_X + 10, y + LINE * 2,
         Skin.FONT_BODY, Skin.TEXT_DIM);
      Skin.textRight(g, "F2 closes this menu", PANE_X + PANE_W - 10, y + LINE * 2,
         Skin.FONT_SMALL, Skin.TEXT_DIM);
   }

   private static String elapsed(long millis) {
      if (millis <= 0L) {
         return "-";
      }

      long seconds = millis / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      return hours + ":" + two(minutes % 60L) + ":" + two(seconds % 60L);
   }

   private static String two(long n) {
      return n < 10L ? "0" + n : String.valueOf(n);
   }

   /* renderSimpleMessageBox, ported. SkullOrca's was a fixed 350x35 with a red
      border; Skin's sizes itself to the message, which matters now that the
      longest one is a compiler error and not "STOPPED". */
   private void drawAlert(Graphics2D g) {
      if (System.currentTimeMillis() <= this.alertUntil) {
         Skin.alert(g, this.rs.windowWidth / 2, this.rs.windowHeight / 2, this.alertMessage);
      }
   }
}
