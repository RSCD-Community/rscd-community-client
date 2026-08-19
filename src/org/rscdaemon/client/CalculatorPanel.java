package org.rscdaemon.client;

import java.awt.Graphics2D;
import java.awt.Rectangle;

/*
 * The calculator screen -- reached from the F2 menu's Calculators button.
 *
 * Two pages. The first is the picker, and it is ScriptPanel's list page with a
 * different directory behind it: same pane, same rows, same scroll triangles,
 * because a player who has used one picker has used both. The second is the
 * calculator itself, and its layout is the shape every RSC calculator page has
 * had since tip.it: controls down the left, answers on the right. Here the
 * controls take the left button strip (SkullOrca's 115px column) and the
 * answers take the pane the script list used.
 *
 * The panel owns the whole keyboard while it is open, the way the world map
 * does -- that is what lets a level be typed into a field without the same
 * keystrokes walking into the chat line, which is the reason ScriptPanel
 * could never have a text box (see the comment on run() there). mudclient
 * routes every key here first and this consumes all of them.
 *
 * Everything a calculator author controls is in Calculator.java; everything
 * here is rendering and input. That split is the design: the calculator does
 * all the calculation logic, the client is just the renderer.
 */
final class CalculatorPanel {
   /* ScriptPanel's rectangles, kept to the pixel: same pane, same list box,
      same rhythm. See the note there about where the numbers came from. */
   private static final int PANE_X = 125;
   private static final int PANE_Y = 5;
   private static final int PANE_W = 382;
   private static final int PANE_H = 324;
   private static final int LIST_X = 130;
   private static final int LIST_Y = 10;
   private static final int LIST_W = 372;
   private static final int LIST_H = 264;
   private static final int ROWS = 16;
   private static final int LIST_ROW = 14;
   private static final int LINE = 14;

   /* The left strip on the run page: SkullOrca's button column, one input per
      34px -- a small label, then a 115x17 widget box under it. Eight fit
      between the top and the Back button; seven when the scroll arrows are
      out. */
   private static final int IN_X = 5;
   private static final int IN_W = 115;
   private static final int IN_TOP = 15;
   private static final int IN_ROW = 34;
   private static final int IN_ROWS = 8;

   /* The dropdown's option list, opened over the output pane. */
   private static final int POP_X = 160;
   private static final int POP_Y = 34;
   private static final int POP_W = 300;
   private static final int POP_ROWS = 16;
   private static final int POP_H = 24 + POP_ROWS * LIST_ROW + 8;

   private static final int PAGE_LIST = 0;
   private static final int PAGE_RUN = 1;

   private final mudclient rs;

   private boolean open;
   private int page = PAGE_LIST;

   /* The picker's state -- refreshed when the panel opens, as ScriptPanel's
      list is, not per frame. */
   private String[] names = new String[0];
   private int listOffset;

   /* The run page's state. */
   private Calculator calc;
   private String calcName = "";
   private java.util.List<Calculator.Input> inputs;
   private int inputOffset;
   /* Which NUMBER input owns the caret; -1 for none. */
   private int focused = -1;
   /* Which CHOICE input has its option list open; -1 for none. */
   private int popupFor = -1;
   private int popupOffset;

   /* What the last compute() said, redrawn every frame, recomputed only when
      an input changes. computeError is the calculator's own exception when it
      threw instead -- shown where the answers would have been, because a
      broken calculator that silently shows stale numbers is worse than one
      that says it is broken. */
   private Calculator.Output output;
   private String computeError;
   private int outputScroll;
   /* Measured during draw; what the scroll arrows clamp against. */
   private int outputHeight;

   private String alertMessage = "";
   private long alertUntil;

   private final Rectangle backToGame = new Rectangle(5, 295, 115, 35);
   private final Rectangle scriptsButton = new Rectangle(5, 15, 115, 35);

   CalculatorPanel(mudclient rs) {
      this.rs = rs;
   }

   boolean isOpen() {
      return this.open;
   }

   /* Reopening lands where you left -- mid-calculator if a calculator was up,
      because F2 in and out to check something should not throw your inputs
      away. Picking it again from the list does reset it: load() builds a
      fresh instance. */
   void open() {
      this.open = true;
      this.refresh();
   }

   void close() {
      this.open = false;
      this.popupFor = -1;
      this.focused = -1;
   }

   private void refresh() {
      this.names = this.rs.calculators().list();
      /* Clamp to the last full page rather than jumping to the top -- deleting
         one calculator while scrolled should not lose the reader's place. */
      if (this.listOffset > this.maxListOffset()) {
         this.listOffset = this.maxListOffset();
      }
   }

   private void showAlertMessage(String message, int seconds) {
      this.alertMessage = message;
      this.alertUntil = System.currentTimeMillis() + seconds * 1000L;
   }

   /*
    * ---- loading ----
    *
    * Straight through ScriptRunner -- same compiler, same staged preamble,
    * same prebuilt-.class fallback for a player on a plain JRE -- just against
    * calculators/ instead of scripts/. The one extra check is the cast: the
    * runner proves the class extends Methods, and a calculator has to be the
    * subclass of Methods that carries inputs and compute().
    */
   private void pick(String name) {
      Methods loaded;

      try {
         loaded = this.rs.calculators().load(name);
      } catch (ScriptRunner.ScriptException var3) {
         this.showAlertMessage("COULD NOT COMPILE " + name.toUpperCase(), 3);
         this.rs.displayMessage("@gry@ " + var3.getMessage(), 3, 0);
         return;
      }

      if (!(loaded instanceof Calculator)) {
         this.showAlertMessage(name.toUpperCase() + " IS NOT A CALCULATOR", 3);
         this.rs.displayMessage("@gry@ " + name + " extends Methods but not Calculator, so it is a script -- "
            + "a calculator starts with: public class " + name + " extends Calculator", 3, 0);
         return;
      }

      this.calc = (Calculator)loaded;
      this.calcName = name;
      this.inputs = this.calc.inputList();
      this.inputOffset = 0;
      this.focused = -1;
      this.popupFor = -1;
      this.outputScroll = 0;
      this.page = PAGE_RUN;
      this.recompute();
   }

   private void recompute() {
      Calculator.Output out = new Calculator.Output();
      this.computeError = null;

      try {
         this.calc.compute(out);
      } catch (Throwable var3) {
         // The calculator's bug, not ours, and the author is likely the person
         // looking at the screen. Name the exception where the answer goes.
         this.computeError = var3.toString();
      }

      this.output = out;
   }

   /*
    * ---- input ----
    */

   void handleClick(int mouseX, int mouseY, int button) {
      if (button == 0) {
         return;
      }

      // The panel is drawn centred; bring the click into its coordinates.
      mouseX -= this.rs.loginOffsetX();
      mouseY -= this.rs.loginOffsetY();

      if (this.page == PAGE_LIST) {
         this.clickList(mouseX, mouseY);
      } else {
         this.clickRun(mouseX, mouseY);
      }
   }

   private void clickList(int mouseX, int mouseY) {
      if (this.scriptsButton.contains(mouseX, mouseY)) {
         /* Back the way you came: the Calculators button on the script menu
            is what opened this. */
         this.close();
         this.rs.scriptPanel().open();
         return;
      }

      if (this.backToGame.contains(mouseX, mouseY)) {
         this.close();
         return;
      }

      // The scroll triangles, at the right edge of the list box.
      if (mouseX >= LIST_X + LIST_W - 14 && mouseX <= LIST_X + LIST_W - 4) {
         if (mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15) {
            if (this.listOffset > 0) {
               this.listOffset--;
            }

            return;
         }

         if (mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5) {
            if (this.listOffset < this.maxListOffset()) {
               this.listOffset++;
            }

            return;
         }
      }

      int row = this.rowAt(mouseX, mouseY);
      if (row != -1) {
         int index = this.listOffset + row;
         if (index < this.names.length) {
            this.pick(this.names[index]);
         }
      }
   }

   private void clickRun(int mouseX, int mouseY) {
      /* An open dropdown owns the click: a row picks, anywhere else closes.
         Nothing under it can be hit through it. */
      if (this.popupFor != -1) {
         this.clickPopup(mouseX, mouseY);
         return;
      }

      if (this.backToGame.contains(mouseX, mouseY)) {
         this.focused = -1;
         this.page = PAGE_LIST;
         this.refresh();
         return;
      }

      // The input strip's own arrows, when there are more inputs than rows.
      if (this.inputScrolls() && mouseX >= 52 && mouseX <= 72) {
         if (mouseY >= 2 && mouseY <= 12) {
            if (this.inputOffset > 0) {
               this.inputOffset--;
            }

            return;
         }

         if (mouseY >= 278 && mouseY <= 288) {
            if (this.inputOffset + this.inputRows() < this.inputs.size()) {
               this.inputOffset++;
            }

            return;
         }
      }

      int hit = this.inputAt(mouseX, mouseY);
      if (hit != -1) {
         Calculator.Input in = this.inputs.get(hit);
         if (in.kind == Calculator.Input.CHOICE) {
            this.focused = -1;
            this.popupFor = hit;
            this.popupOffset = Math.max(0, Math.min(in.index() - POP_ROWS / 2, in.optionCount() - POP_ROWS));
         } else if (in.kind == Calculator.Input.TOGGLE) {
            this.focused = -1;
            in.checked = !in.checked;
            this.recompute();
         } else {
            this.focused = hit;
         }

         return;
      }

      // The output pane's scroll arrows, same corners the script list uses.
      if (mouseX >= LIST_X + LIST_W - 14 && mouseX <= LIST_X + LIST_W - 4) {
         int shown = LIST_H - 28;
         int max = Math.max(0, this.outputHeight - shown);
         if (mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15) {
            this.outputScroll = Math.max(0, this.outputScroll - LIST_ROW * 2);
            return;
         }

         if (mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5) {
            this.outputScroll = Math.min(max, this.outputScroll + LIST_ROW * 2);
            return;
         }
      }

      // A click on nothing puts the caret away.
      this.focused = -1;
   }

   private void clickPopup(int mouseX, int mouseY) {
      Calculator.Input in = this.inputs.get(this.popupFor);

      if (mouseX >= POP_X + POP_W - 14 && mouseX <= POP_X + POP_W - 4) {
         if (mouseY >= POP_Y + 5 && mouseY <= POP_Y + 15) {
            if (this.popupOffset > 0) {
               this.popupOffset--;
            }

            return;
         }

         if (mouseY >= POP_Y + POP_H - 15 && mouseY <= POP_Y + POP_H - 5) {
            if (this.popupOffset + POP_ROWS < in.optionCount()) {
               this.popupOffset++;
            }

            return;
         }
      }

      int row = this.popupRowAt(mouseX, mouseY);
      if (row != -1) {
         int index = this.popupOffset + row;
         if (index < in.optionCount()) {
            in.selected = index;
            this.popupFor = -1;
            this.recompute();
         }

         return;
      }

      this.popupFor = -1;
   }

   /*
    * The mouse wheel, arriving from GameFrame by way of mudclient's
    * handleMouseWheel -- the same road ScriptPanel's list takes. Spent on
    * whatever the pointer is over: the picker list, an open dropdown (which
    * owns the screen the way it owns clicks), the input strip when it has
    * grown arrows, or the output pane -- the place a long answer table
    * actually needs it. Each clamps exactly as its own arrows do.
    */
   void wheel(int notches, int mouseX, int mouseY) {
      if (!this.open || notches == 0) {
         return;
      }

      mouseX -= this.rs.loginOffsetX();
      mouseY -= this.rs.loginOffsetY();

      if (this.page == PAGE_LIST) {
         if (mouseX >= LIST_X && mouseX <= LIST_X + LIST_W
               && mouseY >= LIST_Y && mouseY <= LIST_Y + LIST_H) {
            int scrolled = this.listOffset + notches * 3;
            this.listOffset = Math.max(0, Math.min(scrolled, this.maxListOffset()));
         }

         return;
      }

      if (this.popupFor != -1) {
         if (mouseX >= POP_X && mouseX <= POP_X + POP_W
               && mouseY >= POP_Y && mouseY <= POP_Y + POP_H) {
            Calculator.Input in = this.inputs.get(this.popupFor);
            int maxPop = Math.max(0, in.optionCount() - POP_ROWS);
            this.popupOffset = Math.max(0, Math.min(this.popupOffset + notches * 3, maxPop));
         }

         return;
      }

      if (this.inputScrolls() && mouseX >= IN_X && mouseX <= IN_X + IN_W) {
         int maxIn = this.inputs.size() - this.inputRows();
         this.inputOffset = Math.max(0, Math.min(this.inputOffset + notches, maxIn));
         return;
      }

      if (mouseX >= LIST_X && mouseX <= LIST_X + LIST_W
            && mouseY >= LIST_Y && mouseY <= LIST_Y + LIST_H) {
         int shown = LIST_H - 28;
         int max = Math.max(0, this.outputHeight - shown);
         this.outputScroll = Math.max(0, Math.min(this.outputScroll + notches * LIST_ROW * 2, max));
      }
   }

   /*
    * Every key while the panel is open comes here and stays here -- mudclient
    * gives this panel the keyboard the way it gives the world map the
    * keyboard. Escape backs out one layer at a time: dropdown, then caret,
    * then the calculator, then the picker, then back to the script menu it
    * came from. F2 is the whole-menu toggle and drops straight out to the
    * game from anywhere.
    */
   boolean handleKey(int key) {
      if (!this.open) {
         return false;
      }

      if (key == 1009) {
         this.close();
         return true;
      }

      if (key == 27) {
         if (this.popupFor != -1) {
            this.popupFor = -1;
         } else if (this.focused != -1) {
            this.focused = -1;
         } else if (this.page == PAGE_RUN) {
            this.page = PAGE_LIST;
            this.refresh();
         } else {
            this.close();
            this.rs.scriptPanel().open();
         }

         return true;
      }

      if (this.focused != -1 && this.page == PAGE_RUN) {
         Calculator.Input in = this.inputs.get(this.focused);

         if (key == 8) {
            if (in.text.length() > 0) {
               in.text = in.text.substring(0, in.text.length() - 1);
               this.recompute();
            }
         } else if (key == 22) {
            /* Ctrl+V. The old event model hands a held Ctrl to us as the
               letter's control code -- 22 is SYN, V's -- which is also why
               no plain keystroke can collide with it. Whatever is on the
               clipboard goes through exactly the filter typing does, so
               pasting "level 60" deposits the 60 and nothing else. */
            this.paste(in);
         } else if (key == 10 || key == 13) {
            this.focused = -1;
         } else if ((key >= '0' && key <= '9' || key == '.' || key == '-') && in.text.length() < 10) {
            in.text = in.text + (char)key;
            this.recompute();
         }
      }

      return true;
   }

   /** The system clipboard into the focused field, character by character
    *  through the same charset and length cap the keyboard path enforces.
    *  A clipboard that cannot be read (headless, permissions, non-text
    *  content) pastes nothing rather than throwing. */
   private void paste(Calculator.Input in) {
      String clip;

      try {
         clip = (String)java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
            .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
      } catch (Exception var5) {
         return;
      }

      boolean changed = false;
      for (int i = 0; i < clip.length() && in.text.length() < 10; i++) {
         char c = clip.charAt(i);
         if (c >= '0' && c <= '9' || c == '.' || c == '-') {
            in.text = in.text + c;
            changed = true;
         }
      }

      if (changed) {
         this.recompute();
      }
   }

   /*
    * ---- geometry ----
    */

   private int maxListOffset() {
      return this.names.length > ROWS ? this.names.length - ROWS : 0;
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

   private int popupRowAt(int mouseX, int mouseY) {
      if (mouseX < POP_X + 4 || mouseX > POP_X + POP_W - 16) {
         return -1;
      }

      int top = POP_Y + 20;
      if (mouseY < top || mouseY >= top + POP_ROWS * LIST_ROW) {
         return -1;
      }

      return (mouseY - top) / LIST_ROW;
   }

   private boolean inputScrolls() {
      return this.inputs != null && this.inputs.size() > IN_ROWS;
   }

   /** Seven rows when the arrows need the top and bottom of the strip. */
   private int inputRows() {
      return this.inputScrolls() ? IN_ROWS - 1 : IN_ROWS;
   }

   private int inputTop() {
      return this.inputScrolls() ? IN_TOP + LIST_ROW : IN_TOP;
   }

   /** Which input's widget box is under the mouse; -1 for none. */
   private int inputAt(int mouseX, int mouseY) {
      if (this.inputs == null || mouseX < IN_X || mouseX > IN_X + IN_W) {
         return -1;
      }

      int rows = Math.min(this.inputRows(), this.inputs.size() - this.inputOffset);
      for (int row = 0; row < rows; row++) {
         int boxY = this.inputTop() + row * IN_ROW + 12;
         if (mouseY >= boxY && mouseY <= boxY + 17) {
            return this.inputOffset + row;
         }
      }

      return -1;
   }

   /*
    * ---- drawing ----
    *
    * Same slot in the frame as ScriptPanel's draw: after the game, before the
    * world map, one Skin.open/dispose pair around the whole surface.
    */
   void draw(GameImageMiddleMan gg, int mouseX, int mouseY) {
      Graphics2D g = Skin.open(gg, this.rs.windowWidth, this.rs.surfaceHeight());
      /* Fixed 512x334 layout, centred in a bigger window; the scrim stays
         full-window and the mouse shifts with the content. Same treatment as
         ScriptPanel. */
      int ox = this.rs.loginOffsetX();
      int oy = this.rs.loginOffsetY();

      try {
         if (this.open) {
            Skin.scrim(g, this.rs.windowWidth, this.rs.surfaceHeight(), 226);
            g.translate(ox, oy);
            mouseX -= ox;
            mouseY -= oy;

            if (this.page == PAGE_LIST) {
               this.drawList(g, mouseX, mouseY);
            } else {
               this.drawRun(g, mouseX, mouseY);
            }

            g.translate(-ox, -oy);
         }

         this.drawAlert(g);
      } finally {
         g.dispose();
      }
   }

   private void drawList(Graphics2D g, int mouseX, int mouseY) {
      this.drawStripButton(g, this.scriptsButton, "Scripts", mouseX, mouseY);
      this.drawStripButton(g, this.backToGame, "Back to Game", mouseX, mouseY);

      Skin.panel(g, PANE_X, PANE_Y, PANE_W, PANE_H);
      Skin.well(g, LIST_X, LIST_Y, LIST_W, LIST_H);
      Skin.heading(g, LIST_X + 8, LIST_Y + 17, LIST_W - 24, "CALCULATORS");

      if (this.names.length == 0) {
         Skin.text(g, "Nothing in " + mudclient.calculatorDirectory().getPath(),
            LIST_X + 8, LIST_Y + 42, Skin.FONT_BODY, Skin.TEXT_DIM);
      } else {
         int hovered = this.rowAt(mouseX, mouseY);
         String loaded = this.calc != null ? this.calcName : null;

         for (int row = 0; row < ROWS; row++) {
            int index = this.listOffset + row;
            if (index >= this.names.length) {
               break;
            }

            int y = LIST_Y + 24 + row * LIST_ROW;
            boolean isLoaded = this.names[index].equals(loaded);
            Skin.row(g, LIST_X + 4, y, LIST_W - 20, LIST_ROW, row == hovered, isLoaded);
            Skin.text(g, this.names[index], LIST_X + 10, y + 11, Skin.FONT_BODY,
               isLoaded ? Skin.GOLD_MAX : Skin.TEXT);
         }

         if (this.maxListOffset() != 0) {
            int x = LIST_X + LIST_W - 14;
            boolean upHot = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15;
            boolean downHot = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5;
            Skin.arrow(g, x, LIST_Y + 5, 10, 10, true, upHot);
            Skin.arrow(g, x, LIST_Y + LIST_H - 15, 10, 10, false, downHot);
         }
      }

      int y = LIST_Y + LIST_H + 20;
      Skin.text(g, "Drop a .java that extends Calculator into", PANE_X + 10, y, Skin.FONT_SMALL, Skin.TEXT_DIM);
      Skin.text(g, mudclient.calculatorDirectory().getPath() + " and it appears here.",
         PANE_X + 10, y + LINE, Skin.FONT_SMALL, Skin.TEXT_DIM);
      Skin.textRight(g, "F2 closes this menu", PANE_X + PANE_W - 10, y + LINE * 2,
         Skin.FONT_SMALL, Skin.TEXT_DIM);
   }

   private void drawRun(Graphics2D g, int mouseX, int mouseY) {
      this.drawInputs(g, mouseX, mouseY);
      this.drawStripButton(g, this.backToGame, "Back", mouseX, mouseY);

      Skin.panel(g, PANE_X, PANE_Y, PANE_W, PANE_H);
      Skin.well(g, LIST_X, LIST_Y, LIST_W, LIST_H);
      Skin.heading(g, LIST_X + 8, LIST_Y + 17, LIST_W - 24, this.calcName.toUpperCase());

      this.drawOutput(g);

      int shown = LIST_H - 28;
      /* drawOutput has just measured the real height; if the answer got
         shorter (an input change shrank the table), pull the scroll back so
         the pane cannot sit past the end of it. */
      int maxScroll = Math.max(0, this.outputHeight - shown);
      if (this.outputScroll > maxScroll) {
         this.outputScroll = maxScroll;
      }
      if (this.outputHeight > shown) {
         int x = LIST_X + LIST_W - 14;
         boolean upHot = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + 5 && mouseY <= LIST_Y + 15;
         boolean downHot = mouseX >= x && mouseX <= x + 10 && mouseY >= LIST_Y + LIST_H - 15 && mouseY <= LIST_Y + LIST_H - 5;
         Skin.arrow(g, x, LIST_Y + 5, 10, 10, true, upHot);
         Skin.arrow(g, x, LIST_Y + LIST_H - 15, 10, 10, false, downHot);
      }

      int y = LIST_Y + LIST_H + 20;
      Skin.text(g, "calculator: " + this.calcName, PANE_X + 10, y, Skin.FONT_BODY, Skin.GOLD_HI);
      String about = this.calc.about();
      if (about != null && about.length() > 0) {
         Skin.text(g, Skin.fit(g, about, Skin.FONT_SMALL, PANE_W - 20), PANE_X + 10, y + LINE,
            Skin.FONT_SMALL, Skin.TEXT_DIM);
      }

      Skin.textRight(g, "F2 closes this menu", PANE_X + PANE_W - 10, y + LINE * 2,
         Skin.FONT_SMALL, Skin.TEXT_DIM);

      if (this.popupFor != -1) {
         this.drawPopup(g, mouseX, mouseY);
      }
   }

   private void drawStripButton(Graphics2D g, Rectangle r, String label, int mouseX, int mouseY) {
      boolean hover = r.contains(mouseX, mouseY);
      Skin.button(g, r.x, r.y, r.width, r.height, label, hover ? Skin.HOVER : Skin.NORMAL);
   }

   /*
    * The left strip: label over widget, one input per row, in the order the
    * calculator declared them.
    */
   private void drawInputs(Graphics2D g, int mouseX, int mouseY) {
      if (this.inputScrolls()) {
         boolean upHot = mouseX >= 52 && mouseX <= 72 && mouseY >= 2 && mouseY <= 12;
         boolean downHot = mouseX >= 52 && mouseX <= 72 && mouseY >= 278 && mouseY <= 288;
         Skin.arrow(g, 57, 3, 10, 8, true, upHot && this.inputOffset > 0);
         Skin.arrow(g, 57, 279, 10, 8, false,
            downHot && this.inputOffset + this.inputRows() < this.inputs.size());
      }

      int rows = Math.min(this.inputRows(), this.inputs.size() - this.inputOffset);
      int hovered = this.inputAt(mouseX, mouseY);

      for (int row = 0; row < rows; row++) {
         int index = this.inputOffset + row;
         Calculator.Input in = this.inputs.get(index);
         int top = this.inputTop() + row * IN_ROW;
         int boxY = top + 12;

         Skin.text(g, Skin.fit(g, in.label, Skin.FONT_SMALL, IN_W - 2),
            IN_X + 1, top + 9, Skin.FONT_SMALL, Skin.TEXT_DIM);
         Skin.well(g, IN_X, boxY, IN_W, 17);

         boolean hot = index == hovered;
         boolean caret = index == this.focused;
         if (hot || caret) {
            g.setColor(Skin.colour(caret ? Skin.GOLD_HI : Skin.GOLD_DIM, caret ? 220 : 160));
            g.drawRect(IN_X, boxY, IN_W - 1, 16);
         }

         if (in.kind == Calculator.Input.CHOICE) {
            Skin.text(g, Skin.fit(g, in.display(), Skin.FONT_SMALL, IN_W - 22),
               IN_X + 5, boxY + 12, Skin.FONT_SMALL, Skin.TEXT);
            Skin.arrow(g, IN_X + IN_W - 13, boxY + 6, 8, 6, false, hot);
         } else if (in.kind == Calculator.Input.TOGGLE) {
            boolean on = in.checked;
            Skin.lamp(g, IN_X + 10, boxY + 8, on);
            Skin.text(g, on ? "ON" : "OFF", IN_X + 20, boxY + 12, Skin.FONT_SMALL,
               on ? Skin.RUNE : Skin.TEXT_OFF);
         } else {
            String text = in.display();
            Skin.text(g, text, IN_X + 5, boxY + 12, Skin.FONT_SMALL, Skin.TEXT);
            /* The caret, blinking on the half second, after the last typed
               character. */
            if (caret && (System.currentTimeMillis() / 500 & 1L) == 0L) {
               int cx = IN_X + 6 + Skin.width(g, text, Skin.FONT_SMALL);
               g.setColor(Skin.colour(Skin.GOLD_HI));
               g.drawLine(cx, boxY + 3, cx, boxY + 13);
            }
         }
      }
   }

   /*
    * The output pane. Items land top to bottom in the order compute() wrote
    * them, clipped to the well, scrolled in whole rows. The height measured
    * on the way down is what the arrows clamp against next click.
    */
   private void drawOutput(Graphics2D g) {
      Graphics2D clipped = (Graphics2D)g.create();

      try {
         clipped.clipRect(LIST_X + 2, LIST_Y + 22, LIST_W - 4, LIST_H - 26);

         int x = LIST_X + 10;
         int y = LIST_Y + 24 - this.outputScroll;

         if (this.computeError != null) {
            Skin.text(clipped, "This calculator threw:", x, y + 11, Skin.FONT_BODY, Skin.EMBER_HI);
            Skin.text(clipped, Skin.fit(clipped, this.computeError, Skin.FONT_SMALL, LIST_W - 30),
               x, y + 11 + LINE, Skin.FONT_SMALL, Skin.TEXT_DIM);
            this.outputHeight = LINE * 2;
            return;
         }

         int bottom = y;
         for (Calculator.Output.Item item : this.output.items) {
            if (item.kind == Calculator.Output.GAP) {
               bottom += 8;
            } else if (item.kind == Calculator.Output.HEADING) {
               Skin.heading(clipped, x, bottom + 13, LIST_W - 40, item.text);
               bottom += 20;
            } else if (item.kind == Calculator.Output.TEXT) {
               Skin.text(clipped, item.text, x, bottom + 11, Skin.FONT_BODY, item.colour);
               bottom += 15;
            } else {
               bottom = this.drawTable(clipped, item.table, x, bottom);
            }
         }

         this.outputHeight = bottom - y;
      } finally {
         clipped.dispose();
      }
   }

   /*
    * The tip.it table: headers, a rule, then rows washed green for things the
    * level in hand can already do and ember for things it cannot yet. Column
    * widths come off the content so a calculator never has to guess pixels,
    * and number cells right-align because columns of counts are read down,
    * not across.
    */
   private int drawTable(Graphics2D g, Calculator.Table t, int x, int y) {
      int cols = t.headers.length;
      int[] width = new int[cols];

      for (int c = 0; c < cols; c++) {
         width[c] = Skin.width(g, t.headers[c], Skin.FONT_SMALL);
      }

      for (int r = 0; r < t.rows.size(); r++) {
         String[] row = t.rows.get(r);
         for (int c = 0; c < cols; c++) {
            if (row[c] != null) {
               int w = Skin.width(g, row[c], Skin.FONT_SMALL);
               if (w > width[c]) {
                  width[c] = w;
               }
            }
         }
      }

      int tableW = 0;
      for (int c = 0; c < cols; c++) {
         width[c] += 14;
         tableW += width[c];
      }

      int max = LIST_W - 40;
      if (tableW > max) {
         /* Too wide: the widest column absorbs the loss and its text is cut
            to fit when drawn. Rare, and better than the table walking out of
            the box. */
         int widest = 0;
         for (int c = 1; c < cols; c++) {
            if (width[c] > width[widest]) {
               widest = c;
            }
         }

         width[widest] -= tableW - max;
      }

      int cx = x;
      for (int c = 0; c < cols; c++) {
         Skin.text(g, t.headers[c], cx, y + 11, Skin.FONT_SMALL, Skin.GOLD);
         cx += width[c];
      }

      Skin.rule(g, x, y + 15, Math.min(tableW, max) - 8);
      y += 18;

      for (int r = 0; r < t.rows.size(); r++) {
         String[] row = t.rows.get(r);
         boolean judged = t.judged.get(r).booleanValue();
         boolean ok = t.ok.get(r).booleanValue();

         if (judged) {
            g.setColor(Skin.colour(ok ? Skin.MOSS : Skin.EMBER, 45));
            g.fillRect(x - 4, y, Math.min(tableW, max), LIST_ROW);
         }

         int ink = !judged ? Skin.TEXT : ok ? Skin.RUNE : Skin.EMBER_HI;
         cx = x;
         for (int c = 0; c < cols; c++) {
            if (row[c] != null) {
               String cell = Skin.fit(g, row[c], Skin.FONT_SMALL, width[c] - 8);
               if (t.numeric.get(r)[c]) {
                  Skin.textRight(g, cell, cx + width[c] - 12, y + 11, Skin.FONT_SMALL, ink);
               } else {
                  Skin.text(g, cell, cx, y + 11, Skin.FONT_SMALL, ink);
               }
            }

            cx += width[c];
         }

         y += LIST_ROW;
      }

      return y + 4;
   }

   private void drawPopup(Graphics2D g, int mouseX, int mouseY) {
      Calculator.Input in = this.inputs.get(this.popupFor);

      Skin.panel(g, POP_X, POP_Y, POP_W, POP_H);
      Skin.heading(g, POP_X + 8, POP_Y + 15, POP_W - 24, in.label.toUpperCase());

      int hovered = this.popupRowAt(mouseX, mouseY);

      for (int row = 0; row < POP_ROWS; row++) {
         int index = this.popupOffset + row;
         if (index >= in.optionCount()) {
            break;
         }

         int y = POP_Y + 20 + row * LIST_ROW;
         boolean selected = index == in.index();
         Skin.row(g, POP_X + 4, y, POP_W - 20, LIST_ROW, row == hovered, selected);
         Skin.text(g, Skin.fit(g, in.optionName(index), Skin.FONT_BODY, POP_W - 40),
            POP_X + 10, y + 11, Skin.FONT_BODY, selected ? Skin.GOLD_MAX : Skin.TEXT);
      }

      if (in.optionCount() > POP_ROWS) {
         int x = POP_X + POP_W - 14;
         boolean upHot = mouseX >= x && mouseX <= x + 10 && mouseY >= POP_Y + 5 && mouseY <= POP_Y + 15;
         boolean downHot = mouseX >= x && mouseX <= x + 10 && mouseY >= POP_Y + POP_H - 15 && mouseY <= POP_Y + POP_H - 5;
         Skin.arrow(g, x, POP_Y + 5, 10, 10, true, upHot);
         Skin.arrow(g, x, POP_Y + POP_H - 15, 10, 10, false, downHot);
      }
   }

   private void drawAlert(Graphics2D g) {
      if (System.currentTimeMillis() <= this.alertUntil) {
         Skin.alert(g, this.rs.windowWidth / 2, this.rs.windowHeight / 2, this.alertMessage);
      }
   }
}
