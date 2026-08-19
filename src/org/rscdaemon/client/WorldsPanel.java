package org.rscdaemon.client;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import org.rscdaemon.client.util.Config;
import org.rscdaemon.client.util.WorldList;

/*
 * The Worlds screen: where the player chooses which server they are playing on.
 *
 * This was built out of Jagex's Menu widgets, which was the right way to get it
 * working -- the list, the text boxes and the buttons all existed and all
 * behaved. It is the wrong way to keep it. Menu draws Jagex's flat grey panel,
 * and it can only put a line of text in a row, so a server with four worlds was
 * four sibling lines distinguished by leading spaces, and "how full is it" was a
 * number the player had to read and compare. The two things this screen exists
 * to show -- who is out there, and how alive they are -- were the two things it
 * could not draw.
 *
 * So it draws itself now, through Skin, which is the same theme the F2 script
 * menu uses. Nothing about Jagex's own panels changed: the login boxes, the
 * options menu and the bank are still Menu, still pixel-for-pixel theirs. This
 * screen is not theirs -- it did not exist in 2001, because there was one
 * server and nothing to choose.
 *
 * Behaviour:
 *   - clicking a row SELECTS it; only Join Now joins. A list where the click
 *     that reads a row also commits to it has no safe place to put the pointer,
 *     and this is the first screen the client shows, so a misclick is a player's
 *     first impression of somebody else's server.
 *   - selecting fills the address box with the row's host:port, so Connect is a
 *     second route to the same world, the box reads back what is selected, and
 *     anyone who wants to edit an address starts from a real one
 *   - Favourite acts on the SELECTION, and is faded out when there is none.
 *     It used to act on whatever the pointer happened to be over, which is what
 *     made the misfire described in open() possible at all.
 *   - the search filters what has already been fetched, so it works offline
 *   - the address box connects to anything you can name, which is how you reach
 *     localhost, a server that has not registered, and one that was delisted
 *
 * Input does its own press-edge detection off the held button rather than
 * borrowing Menu's latch, so nothing here depends on who clears
 * lastMouseDownButton or in what order.
 */
final class WorldsPanel {

   /* The login screen's canvas: windowWidth x windowHeight + 12. Package
      visible because the boot-time chooser has to size its own surface to
      them -- before startGame() there is no game framebuffer to ask. */
   static final int SCREEN_W = 512;
   static final int SCREEN_H = 346;

   private static final int PANEL_X = 11;
   private static final int PANEL_Y = 6;
   private static final int PANEL_W = 490;
   private static final int PANEL_H = 334;

   private static final int TITLE_BASE = 34;
   private static final int STATUS_BASE = 54;

   private static final int SEARCH_X = 90;
   private static final int SEARCH_Y = 64;
   private static final int SEARCH_W = 250;
   private static final int SEARCH_H = 22;

   private static final int LIST_X = 22;
   private static final int LIST_Y = 94;
   private static final int LIST_W = 468;
   private static final int LIST_H = 192;
   private static final int ROW_H = 24;
   private static final int VISIBLE = LIST_H / ROW_H;

   /* Join Now, at the right-hand end of every row. It is the only part of a
      row that joins, so it has to be a target and not just a caption. */
   private static final int JOIN_X = LIST_X + LIST_W - 100;
   private static final int JOIN_W = 80;

   private static final int BAR_Y = 300;
   private static final int BAR_H = 22;
   /* Wide enough for "rscd-community.org:43594" in full -- 167px of text plus
      field()'s own padding. Selecting a row writes its host:port here, so a
      box that truncated would be hiding the port from the player it exists to
      show it to. */
   private static final int ADDR_X = 88;
   private static final int ADDR_W = 184;

   private static final int BACK_X = 444;
   private static final int BACK_Y = 14;
   private static final int BACK_W = 46;
   private static final int BACK_H = 20;

   /* Buttons on the bottom bar, as {x, width}. Favourite is wide enough for
      "Unfavourite", which is the longer of the two labels it carries. */
   private static final int CONNECT_X = 278;
   private static final int CONNECT_W = 62;
   private static final int FAVOURITE_X = 346;
   private static final int FAVOURITE_W = 90;
   private static final int REFRESH_X = 442;
   private static final int REFRESH_W = 52;

   private static final int FOCUS_SEARCH = 0;
   private static final int FOCUS_ADDRESS = 1;

   private final mudclient rs;
   private final WorldList worldList;

   /* The rows as last drawn, so a click lands on what the player saw. */
   private List<WorldList.Row> shown = new ArrayList<WorldList.Row>();
   private String search = "";
   private String address = "";
   /* Set when the screen itself has something to say, which outranks the
      fetch's own status until the next refresh. */
   private String message = "";

   /*
    * The selected row, held by identity rather than by reference. Every
    * refresh replaces the whole row list with freshly parsed objects, so a
    * held Row would go on pointing at a dead copy -- one that still has last
    * minute's player count and is not the object the list is drawing. A key
    * that is looked up each frame simply stops matching when the server it
    * names leaves the listing, which is the right thing to happen.
    */
   private String selectedId = "";

   private int focus = FOCUS_SEARCH;
   private int offset;
   private int caretTick;
   private boolean pressed;

   /* Hold-to-repeat for the list's up/down arrows; see ScrollRepeat's own
      comment for why Menu's own lists never needed one of these. */
   private final ScrollRepeat scrollUpRepeat = new ScrollRepeat();
   private final ScrollRepeat scrollDownRepeat = new ScrollRepeat();

   WorldsPanel(mudclient rs, WorldList worldList) {
      this.rs = rs;
      this.worldList = worldList;
   }

   void open() {
      this.focus = FOCUS_SEARCH;
      this.message = "";
      /*
       * Open with the button considered already down, so the first click this
       * panel acts on has to start after it opened.
       *
       * Without this, the click that opened the screen was still held when the
       * first frame ran, the press edge fired immediately, and it fired at the
       * pointer -- which was on the login panel's Worlds button at (375, 314).
       * In this panel's geometry that is inside Favourite (346..436 x
       * 300..322), so merely opening the screen favourited whichever server the
       * list happened to be scrolled to and wrote it to settings.ini. Every
       * click-through screen built this way has this bug once.
       *
       * Favourite now acts on the selection instead of the pointer, so the
       * same misfire would do nothing on a screen that has just opened. This
       * stays anyway: it is what stops the opening click reaching a row.
       */
      this.pressed = true;
      if (this.worldList.getState() == WorldList.IDLE) {
         this.worldList.refresh();
      }
   }

   void setMessage(String message) {
      this.message = message;
   }

   /*
    * ---- input ----
    */

   void update(int mouseX, int mouseY, int buttonHeld) {
      this.caretTick++;

      boolean down = buttonHeld != 0;

      /*
       * Repeat while held, ahead of the click-edge handling below and run on
       * every tick regardless of it, so the arrows keep scrolling for as long
       * as the button stays down instead of moving the list one row per
       * press. The click-edge branch further down still owns the single-row
       * move on the press itself; see ScrollRepeat for why the two do not
       * double up on that first row.
       */
      if (this.scrollUpRepeat.fire(down && this.scrollHit(mouseX, mouseY, true)) && this.offset > 0) {
         this.offset--;
      }

      if (this.scrollDownRepeat.fire(down && this.scrollHit(mouseX, mouseY, false)) && this.offset < this.maxOffset()) {
         this.offset++;
      }

      boolean click = down && !this.pressed;
      this.pressed = down;

      if (!click) {
         return;
      }

      if (hit(mouseX, mouseY, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H)) {
         this.focus = FOCUS_SEARCH;
         return;
      }

      if (hit(mouseX, mouseY, ADDR_X, BAR_Y, ADDR_W, BAR_H)) {
         this.focus = FOCUS_ADDRESS;
         return;
      }

      if (this.rs.canLeaveWorldsScreen() && hit(mouseX, mouseY, BACK_X, BACK_Y, BACK_W, BACK_H)) {
         this.rs.leaveWorldsScreen();
         return;
      }

      if (hit(mouseX, mouseY, REFRESH_X, BAR_Y, REFRESH_W, BAR_H)) {
         /* Drawn disabled while a fetch is in flight; the click has to agree
            with the drawing or the button lies about what it just did. */
         if (this.worldList.getState() != WorldList.LOADING) {
            this.message = "";
            this.worldList.refresh();
         }

         return;
      }

      if (hit(mouseX, mouseY, CONNECT_X, BAR_Y, CONNECT_W, BAR_H)) {
         /* Drawn faded with nothing typed, so it does nothing when clicked
            there: a button that answers a click with an error message it
            caused itself is worse than one that is visibly not ready. */
         if (this.address.trim().length() > 0) {
            this.connectToTyped();
         }

         return;
      }

      if (hit(mouseX, mouseY, FAVOURITE_X, BAR_Y, FAVOURITE_W, BAR_H)) {
         WorldList.Row row = this.selectedRow();
         if (row != null) {
            this.message = (WorldList.toggleFavourite(row.serverId) ? "Favourited " : "Unfavourited ")
               + row.serverName + ".";
         }

         return;
      }

      if (this.scrollHit(mouseX, mouseY, true)) {
         if (this.offset > 0) {
            this.offset--;
         }

         return;
      }

      if (this.scrollHit(mouseX, mouseY, false)) {
         if (this.offset < this.maxOffset()) {
            this.offset++;
         }

         return;
      }

      WorldList.Row row = this.rowAt(mouseX, mouseY);
      if (row == null) {
         return;
      }

      /* The right-hand end of the row is Join Now and commits; the rest of it
         selects. Full and out-of-date worlds keep their Join Now target: the
         count came from a heartbeat that may be a minute old, so the server
         gets to be the one that refuses, with a reason that is current. */
      if (mouseX >= JOIN_X && mouseX < JOIN_X + JOIN_W) {
         this.rs.joinWorld(row);
      } else {
         this.select(row);
      }
   }

   private void select(WorldList.Row row) {
      this.selectedId = key(row);
      /*
       * The address box doubles as the read-out of what is selected and as a
       * starting point for anyone who wants to edit an address by hand -- a
       * server that runs a second world on a port the registry does not list,
       * say. Connect and Join Now therefore reach the same place, which is
       * the point: nothing here is a route the client keeps to itself.
       */
      this.address = row.target();
      this.message = "";
   }

   /* Identity, not equality: two rows are the same row when they are the same
      world of the same server, whatever object the last fetch built for it. */
   private static String key(WorldList.Row row) {
      return row.serverId + "/" + row.world;
   }

   private WorldList.Row selectedRow() {
      if (this.selectedId.length() == 0) {
         return null;
      }

      for (WorldList.Row row : this.shown) {
         if (key(row).equals(this.selectedId)) {
            return row;
         }
      }

      return null;
   }

   /* Jagex's key protocol, as Menu.keyDown reads it: a character code, with
      8 backspace, 10/13 enter and 9 tab. */
   void keyDown(int key) {
      if (key == 0) {
         return;
      }

      /* Escape is Back, when Back exists -- the screen a keyboard opened
         should close from the keyboard too. Before any world is chosen there
         is nowhere to go back to, and the key does nothing, like the button
         it mirrors. */
      if (key == 27) {
         if (this.rs.canLeaveWorldsScreen()) {
            this.rs.leaveWorldsScreen();
         }

         return;
      }

      if (key == 9) {
         this.focus = this.focus == FOCUS_SEARCH ? FOCUS_ADDRESS : FOCUS_SEARCH;
         return;
      }

      if (key == 10 || key == 13) {
         if (this.focus == FOCUS_ADDRESS) {
            this.connectToTyped();
         }

         return;
      }

      boolean searching = this.focus == FOCUS_SEARCH;
      String text = searching ? this.search : this.address;

      if (key == 8) {
         if (text.length() > 0) {
            text = text.substring(0, text.length() - 1);
         }
      } else if (VALID.indexOf(key) >= 0 && text.length() < (searching ? 24 : 48)) {
         text = text + (char)key;
      } else {
         return;
      }

      if (searching) {
         this.search = text;
         // A filtered list is a different list; showing it scrolled to where
         // the old one was is how a search appears to return nothing.
         this.offset = 0;
      } else {
         this.address = text;
      }
   }

   private static final String VALID =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";

   private void connectToTyped() {
      WorldList.Row typed = WorldList.direct(this.address);
      if (typed != null) {
         this.rs.joinWorld(typed);
      } else {
         this.message = "That is not an address the client can use.";
      }
   }

   /*
    * The mouse wheel, arriving from GameFrame by way of
    * mudclient.handleMouseWheel. mouseX/mouseY are screen coordinates, same
    * as everywhere else on this panel takes them from mudclient -- the offset
    * into panel space happens here rather than being asked of the caller.
    */
   void wheel(int notches, int mouseX, int mouseY) {
      if (notches == 0) {
         return;
      }

      mouseX -= this.rs.loginOffsetX();
      mouseY -= this.rs.loginOffsetY();

      if (!hit(mouseX, mouseY, LIST_X, LIST_Y, LIST_W, LIST_H)) {
         return;
      }

      int scrolled = this.offset + notches * 3;
      if (scrolled < 0) {
         scrolled = 0;
      } else if (scrolled > this.maxOffset()) {
         scrolled = this.maxOffset();
      }

      this.offset = scrolled;
   }

   private static boolean hit(int mouseX, int mouseY, int x, int y, int w, int h) {
      return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
   }

   private boolean scrollHit(int mouseX, int mouseY, boolean up) {
      if (this.maxOffset() == 0) {
         return false;
      }

      int x = LIST_X + LIST_W - 16;
      int y = up ? LIST_Y + 4 : LIST_Y + LIST_H - 14;
      return hit(mouseX, mouseY, x, y, 11, 10);
   }

   private int maxOffset() {
      return this.shown.size() > VISIBLE ? this.shown.size() - VISIBLE : 0;
   }

   private WorldList.Row rowAt(int mouseX, int mouseY) {
      if (mouseX < LIST_X || mouseX >= LIST_X + LIST_W - 18) {
         return null;
      }

      if (mouseY < LIST_Y || mouseY >= LIST_Y + VISIBLE * ROW_H) {
         return null;
      }

      int index = this.offset + (mouseY - LIST_Y) / ROW_H;
      return index >= 0 && index < this.shown.size() ? this.shown.get(index) : null;
   }

   /*
    * ---- drawing ----
    */

   void draw(GameImageMiddleMan gg, int mouseX, int mouseY) {
      /* The layout is fixed 512x346; in a bigger window the screen sits
         centred over a full-window scrim, and the mouse shifts with it.
         mudclient offsets the update() coordinates by the same amounts. The
         boot-time chooser goes through paint() directly and centres itself
         at blit time instead. */
      Graphics2D g = Skin.open(gg, this.rs.windowWidth, this.rs.surfaceHeight());
      int ox = this.rs.loginOffsetX();
      int oy = this.rs.loginOffsetY();

      try {
         Skin.scrim(g, this.rs.windowWidth, this.rs.surfaceHeight(), 232);
         g.translate(ox, oy);
         this.paint(g, mouseX - ox, mouseY - oy);
      } finally {
         g.dispose();
      }
   }

   /*
    * The drawing itself, onto a Graphics2D the caller owns.
    *
    * Split out from draw() for the boot-time chooser, which has to show this
    * screen before there is a game framebuffer to open one over -- see
    * mudclient.chooseWorldBeforeBoot(). It paints into an ordinary
    * BufferedImage and blits that to the window instead.
    */
   void paint(Graphics2D g, int mouseX, int mouseY) {
      /* Refilled every frame, which is what makes the search filter as it is
         typed and the counts move on a refresh without anything being wired
         together. It is a few hundred string compares. */
      this.shown = this.worldList.filter(this.search);
      if (this.offset > this.maxOffset()) {
         this.offset = this.maxOffset();
      }

      /* No scrim here: draw() lays a full-window one behind this, and the
         boot-time chooser paints onto a solid black fill where a scrim
         changed nothing anyway. */
      Skin.panel(g, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
      Skin.title(g, SCREEN_W / 2, TITLE_BASE, "WORLDS");

      this.drawStatus(g);
      this.drawSearch(g, mouseX, mouseY);
      this.drawList(g, mouseX, mouseY);
      this.drawBottomBar(g, mouseX, mouseY);

      /* No Back on the boot-time chooser: there is nothing behind it, and a
         button that cannot go anywhere is worse than no button. */
      if (this.rs.canLeaveWorldsScreen()) {
         Skin.button(g, BACK_X, BACK_Y, BACK_W, BACK_H, "Back",
            hit(mouseX, mouseY, BACK_X, BACK_Y, BACK_W, BACK_H) ? Skin.HOVER : Skin.NORMAL);
      }
   }

   private void drawStatus(Graphics2D g) {
      String status = this.message;
      int colour = Skin.EMBER_HI;

      if (status.length() == 0) {
         status = this.worldList.getMessage();
         colour = this.worldList.getState() == WorldList.FAILED ? Skin.EMBER_HI : Skin.TEXT_DIM;
      }

      if (status.length() == 0) {
         WorldList.Row selected = this.selectedRow();
         status = this.shown.isEmpty()
            ? (this.search.length() > 0 ? "Nothing matches that search." : "No servers are listed right now.")
            : selected == null
               ? "Click a world to select it."
               : selected.serverName + ", world " + selected.world + " -- Join Now to play there.";
         colour = Skin.TEXT_DIM;
      }

      Skin.textCentre(g, Skin.fit(g, status, Skin.FONT_SMALL, PANEL_W - 40),
         SCREEN_W / 2, STATUS_BASE, Skin.FONT_SMALL, colour);
   }

   private void drawSearch(Graphics2D g, int mouseX, int mouseY) {
      Skin.text(g, "Search", 26, SEARCH_Y + 15, Skin.FONT_HEAD, Skin.GOLD);
      this.field(g, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H, this.search,
         this.focus == FOCUS_SEARCH, "server name or host");

      /* How much of the list the filter is hiding. Without it a search that
         matches nothing looks identical to a registry that returned nothing,
         and those want very different reactions from the player. */
      int total = this.worldList.getRows().size();
      String count = this.shown.size() == total
         ? total + (total == 1 ? " world" : " worlds")
         : this.shown.size() + " of " + total;
      Skin.textRight(g, count, PANEL_X + PANEL_W - 14, SEARCH_Y + 15, Skin.FONT_SMALL, Skin.TEXT_DIM);
   }

   /** A text box: sunken well, the text, a blinking caret when focused. */
   private void field(Graphics2D g, int x, int y, int w, int h, String text, boolean focused, String hint) {
      Skin.well(g, x, y, w, h);
      if (focused) {
         g.setColor(Skin.colour(Skin.GOLD_DIM, 190));
         g.drawRect(x, y, w - 1, h - 1);
      }

      int baseline = y + h - 7;
      if (text.length() == 0 && !focused) {
         Skin.text(g, hint, x + 7, baseline, Skin.FONT_SMALL, Skin.TEXT_OFF);
         return;
      }

      String visible = Skin.fit(g, text, Skin.FONT_BODY, w - 16);
      Skin.text(g, visible, x + 7, baseline, Skin.FONT_BODY, Skin.TEXT);

      if (focused && this.caretTick % 40 < 22) {
         int caretX = x + 7 + Skin.width(g, visible, Skin.FONT_BODY) + 1;
         g.setColor(Skin.colour(Skin.GOLD_HI, 220));
         g.drawLine(caretX, y + 4, caretX, y + h - 5);
      }
   }

   private void drawList(Graphics2D g, int mouseX, int mouseY) {
      Skin.well(g, LIST_X, LIST_Y, LIST_W, LIST_H);

      if (this.shown.isEmpty()) {
         this.drawEmptyList(g);
         return;
      }

      WorldList.Row hovered = this.rowAt(mouseX, mouseY);
      WorldList.Row selected = this.selectedRow();
      boolean onJoin = mouseX >= JOIN_X && mouseX < JOIN_X + JOIN_W;

      for (int i = 0; i < VISIBLE; i++) {
         int index = this.offset + i;
         if (index >= this.shown.size()) {
            break;
         }

         WorldList.Row row = this.shown.get(index);
         this.drawRow(g, row, LIST_Y + i * ROW_H, row == hovered, row == selected,
            row == hovered && onJoin);
      }

      this.drawScrollbar(g, mouseX, mouseY);
   }

   /*
    * The empty list is the first thing a fresh install sees if the registry is
    * unreachable, so it says what to do next rather than just being blank.
    * The address box below is the answer, and it works with no network at all.
    */
   private void drawEmptyList(Graphics2D g) {
      int y = LIST_Y + LIST_H / 2 - 16;

      if (this.worldList.getState() == WorldList.LOADING) {
         Skin.textCentre(g, "Loading...", SCREEN_W / 2, y + 12, Skin.FONT_HEAD, Skin.GOLD);
         return;
      }

      if (this.search.length() > 0) {
         Skin.textCentre(g, "No world matches \"" + this.search + "\"",
            SCREEN_W / 2, y + 12, Skin.FONT_HEAD, Skin.TEXT_DIM);
         return;
      }

      Skin.textCentre(g, "No servers listed", SCREEN_W / 2, y, Skin.FONT_HEAD, Skin.TEXT_DIM);
      Skin.textCentre(g, "You can still type an address below to connect directly.",
         SCREEN_W / 2, y + 20, Skin.FONT_SMALL, Skin.TEXT_OFF);
      Skin.textCentre(g, Skin.fit(g, Config.API_URL, Skin.FONT_SMALL, LIST_W - 40),
         SCREEN_W / 2, y + 38, Skin.FONT_SMALL, Skin.TEXT_OFF);
   }

   /**
    * One world.
    *
    * A server's first world carries the server's name and its favourite star;
    * the rest are indented under it, because a server with four worlds is one
    * community and not four entries competing with each other.
    */
   private void drawRow(Graphics2D g, WorldList.Row row, int y, boolean hover, boolean selected, boolean joinHover) {
      boolean favourite = WorldList.isFavourite(row.serverId);
      /* The gem edge now means selected, which is what Skin.row calls it. It
         used to mean favourite -- but favourites already carry a star and
         already sort to the top, and there is only one edge to spend. */
      Skin.row(g, LIST_X + 2, y, LIST_W - 4, ROW_H, hover, selected);

      int textY = y + 16;

      if (row.grouped) {
         /* A bracket down from the name above, so the group still reads as a
            group when it is scrolled far enough that its header is off the
            top of the list. */
         g.setColor(Skin.colour(Skin.GOLD_DIM, 130));
         g.drawLine(LIST_X + 22, y, LIST_X + 22, y + ROW_H / 2);
         g.drawLine(LIST_X + 22, y + ROW_H / 2, LIST_X + 28, y + ROW_H / 2);
         Skin.text(g, "World " + row.world, LIST_X + 34, textY, Skin.FONT_BODY, Skin.TEXT_DIM);
      } else {
         if (favourite) {
            star(g, LIST_X + 14, y + ROW_H / 2, Skin.GOLD_HI);
         }

         Skin.text(g, Skin.fit(g, row.serverName, Skin.FONT_HEAD, 168),
            LIST_X + 24, textY, Skin.FONT_HEAD, Skin.GOLD_HI);
      }

      /* Population, as a bar and as the numbers. The bar is the thing that is
         read at a glance and the numbers are what gets compared, so both.
         The count is right-aligned against the join box rather than started at
         a fixed x: it is the only field here whose width depends on the
         numbers in it, and "2000 / 2000" is 14 pixels wider than "0 / 500". */
      boolean full = row.isFull();
      Skin.lamp(g, LIST_X + 210, y + ROW_H / 2, row.online > 0);

      if (row.capacity > 0) {
         Skin.meter(g, LIST_X + 222, y + ROW_H / 2 - 4, 66, 8, (double)row.online / row.capacity);
      }

      String count = row.capacity > 0 ? row.online + " / " + row.capacity : Integer.toString(row.online);
      Skin.textRight(g, count, JOIN_X - 10, textY, Skin.FONT_SMALL, Skin.TEXT);

      /*
       * Full is a hint and not a refusal: the count came from the server's own
       * heartbeat and may be a minute old, so the row is still joinable and
       * the server gets to be the one that says no, with a message that is
       * actually current. "Old client" is the same kind of hint, from the same
       * heartbeat, and takes the label because it is the harder of the two to
       * get past: a full world empties, an old client does not.
       *
       * Join Now is gold rather than green. It reads once per row, so a
       * saturated colour here is seven or eight shouts down the list -- which
       * is exactly what the green-on-black F2 overlay looked like before this
       * theme, and the reason for the theme. Green is left to the lamp and the
       * meter, where it is one small light per row and means something the
       * gold cannot say.
       *
       * It becomes an actual button on the row under the pointer and on the
       * selected row, and stays flat text everywhere else. Drawing eight of
       * them at once would put a button on every line of a list whose job is
       * to be read; drawing none would leave the one thing that commits
       * looking exactly like the caption it used to be.
       */
      boolean outdated = row.needsNewerClient(GameWindowMiddleMan.clientVersion);
      String label = outdated ? "Old client" : full ? "Full" : "Join Now";

      if (hover || selected) {
         int state = outdated || full ? Skin.DANGER : joinHover ? Skin.HOVER : Skin.NORMAL;
         Skin.button(g, JOIN_X, y + 3, JOIN_W, ROW_H - 6, label, state);
      } else {
         Skin.textRight(g, label, JOIN_X + JOIN_W - 4, textY,
            Skin.FONT_HEAD, outdated || full ? Skin.EMBER_HI : Skin.GOLD);
      }
   }

   /** Five-pointed enough at 9 pixels; it only has to read as "marked". */
   private static void star(Graphics2D g, int cx, int cy, int colour) {
      g.setColor(Skin.colour(colour, 230));
      g.fillPolygon(
         new int[] { cx, cx + 2, cx + 5, cx + 2, cx + 3, cx, cx - 3, cx - 2, cx - 5, cx - 2 },
         new int[] { cy - 5, cy - 2, cy - 1, cy + 1, cy + 5, cy + 3, cy + 5, cy + 1, cy - 1, cy - 2 },
         10);
   }

   private void drawScrollbar(Graphics2D g, int mouseX, int mouseY) {
      if (this.maxOffset() == 0) {
         return;
      }

      int x = LIST_X + LIST_W - 16;
      Skin.arrow(g, x, LIST_Y + 4, 11, 10, true, this.scrollHit(mouseX, mouseY, true));
      Skin.arrow(g, x, LIST_Y + LIST_H - 14, 11, 10, false, this.scrollHit(mouseX, mouseY, false));

      /* The track between the arrows, with a thumb sized to the fraction of
         the list on screen -- otherwise there is nothing to say how far down a
         long list you are. */
      int trackTop = LIST_Y + 18;
      int trackHeight = LIST_H - 36;
      g.setColor(Skin.colour(Skin.BG_DEEP, 160));
      g.fillRect(x + 3, trackTop, 5, trackHeight);

      int thumb = Math.max(12, trackHeight * VISIBLE / this.shown.size());
      int travel = trackHeight - thumb;
      int thumbY = trackTop + (this.maxOffset() == 0 ? 0 : travel * this.offset / this.maxOffset());
      g.setColor(Skin.colour(Skin.GOLD_DIM, 210));
      g.fillRect(x + 3, thumbY, 5, thumb);
   }

   private void drawBottomBar(Graphics2D g, int mouseX, int mouseY) {
      Skin.rule(g, LIST_X, BAR_Y - 12, LIST_W);

      Skin.text(g, "Address", 26, BAR_Y + 15, Skin.FONT_HEAD, Skin.GOLD);
      this.field(g, ADDR_X, BAR_Y, ADDR_W, BAR_H, this.address,
         this.focus == FOCUS_ADDRESS, "host or host:port");

      this.barButton(g, mouseX, mouseY, CONNECT_X, CONNECT_W, "Connect",
         this.address.trim().length() > 0);

      /*
       * Favourite needs a row to act on, so with nothing selected it is faded
       * and inert rather than quietly doing something to a row the player did
       * not name. The label carries which way it will go: one button doing two
       * opposite things has to say which one, and the star in the row is too
       * small to be the only answer.
       */
      WorldList.Row selected = this.selectedRow();
      this.barButton(g, mouseX, mouseY, FAVOURITE_X, FAVOURITE_W,
         selected != null && WorldList.isFavourite(selected.serverId) ? "Unfavourite" : "Favourite",
         selected != null);

      this.barButton(g, mouseX, mouseY, REFRESH_X, REFRESH_W, "Refresh",
         this.worldList.getState() != WorldList.LOADING);
   }

   private void barButton(Graphics2D g, int mouseX, int mouseY, int x, int w, String label, boolean enabled) {
      int state = !enabled ? Skin.DISABLED
         : hit(mouseX, mouseY, x, BAR_Y, w, BAR_H) ? Skin.HOVER : Skin.NORMAL;
      Skin.button(g, x, BAR_Y, w, BAR_H, label, state);
   }
}
