package org.rscdaemon.client;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;

public class GameFrame extends Frame {
   int frameWidth;
   int frameHeight;
   int graphicsTranslate;
   int frameOffset = 28;
   GameWindow aGameWindow;
   Graphics aGraphics49;

   public GameFrame(GameWindow gameWindow, int width, int height, String title, boolean resizable, boolean flag1) {
      /*
       * Ask for the events by name. See processEvent below for why this frame
       * has to opt in to the newer event model to keep speaking the old one.
       */
      this.enableEvents(
         AWTEvent.KEY_EVENT_MASK
            | AWTEvent.MOUSE_EVENT_MASK
            | AWTEvent.MOUSE_MOTION_EVENT_MASK
            | AWTEvent.MOUSE_WHEEL_EVENT_MASK
            | AWTEvent.WINDOW_EVENT_MASK
            | AWTEvent.COMPONENT_EVENT_MASK
      );
      this.frameWidth = width;
      this.frameHeight = height;
      this.aGameWindow = gameWindow;
      if (flag1) {
         this.frameOffset = 48;
      } else {
         this.frameOffset = 28;
      }

      this.setTitle(title);
      this.setResizable(resizable);
      /*
       * Black, because the window system fills newly exposed area with this
       * colour before any Java code gets to paint it. Every screen in the
       * client draws onto black already, so the default light grey was only
       * ever visible as a flash during a resize -- and, on the boot-time
       * worlds chooser, as a permanent border until that loop was taught to
       * repaint on resize.
       */
      this.setBackground(Color.black);
      this.show();
      this.toFront();
      this.awaitDecorations();
      this.resize(this.frameWidth, this.frameHeight);
      if (resizable) {
         /*
          * The vanilla 512x345 interior is the floor -- every layout in the
          * game treats it as the minimum, so don't let the window manager
          * offer anything smaller. mudclient clamps to the same numbers.
          */
         Insets insets = this.decorations();
         this.setMinimumSize(new Dimension(512 + insets.left + insets.right, 345 + insets.top + insets.bottom));
      }

      this.aGraphics49 = this.getGraphics();
   }

   /*
    * The window decorations used to be assumed rather than measured: the frame
    * was sized to height + frameOffset (28, or 48 when flag1) and everything
    * was drawn at a fixed translate of (0, 24). Those are the title bar and
    * border of a Windows 98/2000 frame. Under any window manager with a taller
    * title bar the game slides up underneath it -- the top rows are hidden and
    * the decoration height we failed to account for shows up as a blank strip
    * along the bottom.
    *
    * The peer knows the real numbers, so ask it. frameOffset is kept only as
    * the fallback for the window between show() and the window manager
    * reparenting us, when the insets are still all zero.
    */
   private Insets decorations() {
      Insets insets = this.getInsets();
      return undecorated(insets) ? new Insets(this.frameOffset - 4, 0, 4, 0) : insets;
   }

   private static boolean undecorated(Insets insets) {
      return insets == null || insets.top == 0 && insets.bottom == 0 && insets.left == 0 && insets.right == 0;
   }

   /*
    * X11 only reports the decoration size once the window manager has
    * reparented the frame, which is some milliseconds after show(). Wait for
    * it rather than sizing off the fallback, because mudclient grabs its
    * Graphics object once at startup and caches it for the life of the
    * process -- a correction that arrives later would never reach the game.
    */
   private void awaitDecorations() {
      for (int i = 0; i < 100 && undecorated(this.getInsets()); i++) {
         try {
            Thread.sleep(10L);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   @Override
   public Graphics getGraphics() {
      Graphics g = super.getGraphics();
      if (g == null) {
         return null;
      } else {
         if (this.graphicsTranslate == 0) {
            Insets insets = this.decorations();
            g.translate(insets.left, insets.top);
         } else {
            g.translate(-5, 0);
         }

         return g;
      }
   }

   @Override
   public void resize(int i, int j) {
      this.frameWidth = i;
      this.frameHeight = j;
      Insets insets = this.decorations();
      super.resize(i + insets.left + insets.right, j + insets.top + insets.bottom);
   }

   /*
    * The mouse wheel, and the price of having one.
    *
    * This client speaks the AWT 1.0 event model: handleEvent(Event) below, and
    * GameWindow's keyDown/mouseDown/mouseDrag. That model has no wheel --
    * java.awt.Event was written before wheels existed and there is no id for
    * one. The wheel only exists in the 1.1 model, as MouseWheelEvent.
    *
    * The two cannot simply coexist. Asking for any 1.1 event -- by adding a
    * listener or by calling enableEvents -- sets Component.newEventsOnly, and
    * from then on AWT stops converting events into old ones and delivering
    * them to handleEvent. Adding a MouseWheelListener and nothing else really
    * does silently kill every mouse click and every keystroke in the game;
    * that was measured, not assumed.
    *
    * So the frame opts in to the new model for everything and does the
    * conversion itself, which is what AWT used to do for us. handleEvent and
    * GameWindow are untouched: they still receive the same ids, the same key
    * codes and the same modifier bits they always did. The wheel is the one
    * event with no old equivalent, and it goes straight to GameWindow.
    *
    * The conversion follows java.awt.AWTEvent.convertToOld: mouse and key ids
    * are numerically identical in both models, an action key becomes KEY_ACTION
    * rather than KEY_PRESS, a key that is not one carries its character, and
    * the modifier keys themselves are suppressed because the old model had no
    * events for them.
    */
   @Override
   protected void processEvent(AWTEvent event) {
      if (event.getID() == ComponentEvent.COMPONENT_RESIZED) {
         /*
          * Report the interior size -- what the game can actually draw on --
          * not the frame's. Store-only on this thread: the game thread picks
          * the new size up between ticks, when nothing is mid-draw against
          * the old framebuffer.
          */
         Dimension size = this.getSize();
         Insets insets = this.decorations();
         this.aGameWindow.frameResized(size.width - insets.left - insets.right, size.height - insets.top - insets.bottom);
      }

      if (event.getID() == MouseEvent.MOUSE_WHEEL) {
         MouseWheelEvent wheel = (MouseWheelEvent)event;
         Insets insets = this.decorations();
         this.aGameWindow.mouseWheel(wheel.getWheelRotation(), wheel.getX() - insets.left, wheel.getY() - insets.top);
         return;
      }

      Event old = toOldEvent(event);
      if (old != null) {
         this.handleEvent(old);
      }

      super.processEvent(event);
   }

   /*
    * The action keys, paired with the codes the game knows them by. This is
    * java.awt.Event.getOldEventKey's table, which is package private; the
    * constants on both sides of it are not.
    */
   private static final int[][] ACTION_KEYS = {
      {Event.HOME, KeyEvent.VK_HOME},
      {Event.END, KeyEvent.VK_END},
      {Event.PGUP, KeyEvent.VK_PAGE_UP},
      {Event.PGDN, KeyEvent.VK_PAGE_DOWN},
      {Event.UP, KeyEvent.VK_UP},
      {Event.DOWN, KeyEvent.VK_DOWN},
      {Event.LEFT, KeyEvent.VK_LEFT},
      {Event.RIGHT, KeyEvent.VK_RIGHT},
      {Event.F1, KeyEvent.VK_F1},
      {Event.F2, KeyEvent.VK_F2},
      {Event.F3, KeyEvent.VK_F3},
      {Event.F4, KeyEvent.VK_F4},
      {Event.F5, KeyEvent.VK_F5},
      {Event.F6, KeyEvent.VK_F6},
      {Event.F7, KeyEvent.VK_F7},
      {Event.F8, KeyEvent.VK_F8},
      {Event.F9, KeyEvent.VK_F9},
      {Event.F10, KeyEvent.VK_F10},
      {Event.F11, KeyEvent.VK_F11},
      {Event.F12, KeyEvent.VK_F12},
      {Event.PRINT_SCREEN, KeyEvent.VK_PRINTSCREEN},
      {Event.SCROLL_LOCK, KeyEvent.VK_SCROLL_LOCK},
      {Event.CAPS_LOCK, KeyEvent.VK_CAPS_LOCK},
      {Event.NUM_LOCK, KeyEvent.VK_NUM_LOCK},
      {Event.PAUSE, KeyEvent.VK_PAUSE},
      {Event.INSERT, KeyEvent.VK_INSERT}
   };

   private static int oldKeyCode(KeyEvent key) {
      for (int[] pair : ACTION_KEYS) {
         if (pair[1] == key.getKeyCode()) {
            return pair[0];
         }
      }

      return key.getKeyChar();
   }

   private static Event toOldEvent(AWTEvent event) {
      int id = event.getID();

      if (event instanceof KeyEvent) {
         if (id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) {
            /* KEY_TYPED has no old equivalent; KEY_PRESSED already carries the
               character the old model would have reported. */
            return null;
         }

         KeyEvent key = (KeyEvent)event;
         int code = key.getKeyCode();
         if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT) {
            return null;
         }

         int oldId = id;
         if (key.isActionKey()) {
            oldId = id == KeyEvent.KEY_PRESSED ? Event.KEY_ACTION : Event.KEY_ACTION_RELEASE;
         }

         return new Event(
            event.getSource(),
            key.getWhen(),
            oldId,
            0,
            0,
            oldKeyCode(key),
            key.getModifiers() & ~InputEvent.BUTTON1_MASK
         );
      }

      if (event instanceof MouseEvent) {
         if (id < MouseEvent.MOUSE_PRESSED || id > MouseEvent.MOUSE_DRAGGED) {
            return null;
         }

         /* The right button reports itself as META, which is exactly how
            GameWindow.mouseDown tells the buttons apart. */
         MouseEvent mouse = (MouseEvent)event;
         return new Event(
            event.getSource(),
            mouse.getWhen(),
            id,
            mouse.getX(),
            mouse.getY(),
            0,
            mouse.getModifiers() & ~InputEvent.BUTTON1_MASK
         );
      }

      if (id == WindowEvent.WINDOW_CLOSING) {
         return new Event(event.getSource(), Event.WINDOW_DESTROY, null);
      }

      return null;
   }

   @Override
   public boolean handleEvent(Event event) {
      Insets insets = this.decorations();
      int x = event.x - insets.left;
      int y = event.y - insets.top;
      if (event.id == 401) {
         this.aGameWindow.keyDown(event, event.key);
      } else if (event.id == 402) {
         this.aGameWindow.keyUp(event, event.key);
      } else if (event.id == 501) {
         this.aGameWindow.mouseDown(event, x, y);
      } else if (event.id == 506) {
         this.aGameWindow.mouseDrag(event, x, y);
      } else if (event.id == 502) {
         this.aGameWindow.mouseUp(event, x, y);
      } else if (event.id == 503) {
         this.aGameWindow.mouseMove(event, x, y);
      } else if (event.id == 201) {
         this.aGameWindow.destroy();
      } else if (event.id == 1001) {
         this.aGameWindow.action(event, event.target);
      } else if (event.id == 403) {
         this.aGameWindow.keyDown(event, event.key);
      } else if (event.id == 404) {
         this.aGameWindow.keyUp(event, event.key);
      }

      return true;
   }

   @Override
   public final void paint(Graphics g) {
      this.aGameWindow.paint(g);
   }
}
