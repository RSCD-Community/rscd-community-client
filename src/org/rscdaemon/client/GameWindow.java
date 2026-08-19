package org.rscdaemon.client;

import java.awt.Color;
import java.awt.Event;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Panel;
import java.net.InetAddress;
import java.net.Socket;

/*
 * Applet mode is gone. This extended java.applet.Applet because the client was
 * once served from a web page, and it kept extending it long after browser play
 * was cancelled -- the inheritance cost nothing and nobody had a reason to
 * touch it.
 *
 * JDK 24 gave one: JEP 504 removed the Applet API outright, so a client that
 * extends it will not compile, let alone run, on a JDK anyone is likely to have
 * installed. Since the whole point of the desktop client is that a player can
 * run it without a fight, that settles it.
 *
 * Applet extends Panel, and Panel is all this ever used: createWindow() always
 * opened a real AWT frame even in the webclient, GameFrame drives every event
 * by hand through handleEvent(), and the only members touched are Component's
 * paint/update/getGraphics. So the base class changes and nothing else does.
 *
 * What went with it: init(), start() and stop() -- the browser lifecycle, never
 * called once createWindow() was the only way in. destroy() stays, because it
 * is not really an applet method here: GameFrame.handleEvent maps WINDOW_DESTROY
 * (event 201) onto it, so it is the window's close button.
 */
public class GameWindow extends Panel implements Runnable {
   /*
    * Sampled from the rune glow in the splash artwork rather than picked by
    * eye. The original was (75, 61, 43), a muddy brown that suited the old
    * near-black RSCDaemon splash and is invisible against this one.
    */
   public static final Color BAR_COLOUR = new Color(152, 196, 80);
   public static final Font LOADING_FONT = GameImage.helvetica(0, 12);

   /*
    * Where the progress bar sits, as a fraction of the splash image rather
    * than in pixels.
    *
    * The old code blitted the splash unscaled at a fixed (5, 0) and drew a
    * fixed 277x20 bar at a fixed offset, which is why the artwork had to be
    * authored at exactly 506x345. The splash is now scaled to whatever the
    * window is, so the bar has to be positioned proportionally or it drifts
    * off its recess as soon as the window is not that size.
    *
    * These are measured from the recess in the artwork -- interior
    * x 341..1169, y 923..998 of a 1513x1039 image. If the splash is redrawn,
    * re-measure and update these four numbers; nothing else needs to change.
    */
   private static final float BAR_X = 0.2254F;
   private static final float BAR_Y = 0.8884F;
   private static final float BAR_W = 0.5479F;
   private static final float BAR_H = 0.0731F;

   /** Point size LOADING_FONT was chosen against, for proportional scaling. */
   private static final int FONT_REFERENCE_HEIGHT = 345;

   private Image loadingLogo;
   private Image scaledLogo;
   private int scaledLogoWidth;
   private int scaledLogoHeight;
   /* The drawing surface, in pixels. Named for the applet it used to be sized
      to; it is the frame's client area now and the splash scales itself to it. */
   static int canvasWidth;
   static int canvasHeight;
   /* The size the user last dragged the frame to, interior pixels, written on
      the AWT thread and read by the game thread between ticks. Zero until the
      first resize. */
   volatile int resizedWidth;
   volatile int resizedHeight;
   private Thread gameWindowThread;
   private int threadSleepModifier;
   private int catchupTickLimit;
   private long[] currentTimeArray;
   public static GameFrame gameFrame = null;
   private int exitTimeout;
   private int overloadCount;
   public int yOffset;
   public int lastActionTimeout;
   public int loadingScreen;
   public String loadingString;
   private int loadingPercent;
   private String loadingBarText;
   private Graphics loadingGraphics;
   private String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";
   public boolean keyLeftBraceDown;
   public boolean keyRightBraceDown;
   public boolean keyLeftDown;
   public boolean keyRightDown;
   public boolean keyUpDown;
   public boolean keyDownDown;
   public boolean keySpaceDown;
   public boolean keyNMDown;
   public int threadSleepTime;
   public int mouseX;
   public int mouseY;
   public int mouseDownButton;
   public int lastMouseDownButton;
   public int keyDown;
   public int keyDown2;
   public boolean keyF1Toggle;
   public String inputText;
   public String enteredText;
   public String inputMessage;
   public String enteredMessage;

   protected void startGame() {
   }

   protected synchronized void updateGame() {
   }

   protected void logoutAndStop() {
   }

   protected synchronized void renderFrame() {
   }

   protected final void createWindow(int width, int height, String title, boolean resizable) {
      canvasWidth = width;
      canvasHeight = height;
      gameFrame = new GameFrame(this, width, height, title, resizable, false);
      this.loadingScreen = 1;
      this.gameWindowThread = new Thread(this);
      this.gameWindowThread.start();
      this.gameWindowThread.setPriority(1);
   }

   public void setLogo(Image logo) {
      this.loadingLogo = logo;
      this.scaledLogo = null;
      /*
       * Toolkit.getImage() is asynchronous and returns before anything has
       * been read, so the image reports a width of -1 until it loads. Block
       * until it is really there -- otherwise the first scale request is made
       * against unknown dimensions and silently produces nothing.
       */
      if (logo != null) {
         try {
            MediaTracker tracker = new MediaTracker(this);
            tracker.addImage(logo, 0);
            tracker.waitForID(0);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
         }
      }

      /*
       * run() paints the loading screen once, before startGame(), and from
       * then on only drawLoadingBarText() runs -- and that touches nothing but
       * the bar interior. So a splash that arrives after that first paint
       * would never be drawn at all. Paint it here instead of waiting for an
       * expose event that never comes.
       */
      if (this.loadingScreen == 2 && this.loadingGraphics != null) {
         this.drawLoadingScreen(this.loadingPercent, this.loadingBarText);
      }
   }

   /*
    * The splash scaled to the current window, rebuilt only when the window
    * size actually changes. Scaling every frame would be visibly slow with a
    * full-resolution source image.
    */
   private Image getScaledLogo() {
      if (this.loadingLogo == null) {
         return null;
      } else if (this.scaledLogo != null && this.scaledLogoWidth == canvasWidth && this.scaledLogoHeight == canvasHeight) {
         return this.scaledLogo;
      } else {
         Image scaled = this.loadingLogo.getScaledInstance(canvasWidth, canvasHeight, Image.SCALE_SMOOTH);

         try {
            MediaTracker tracker = new MediaTracker(this);
            tracker.addImage(scaled, 0);
            tracker.waitForID(0);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
         }

         this.scaledLogo = scaled;
         this.scaledLogoWidth = canvasWidth;
         this.scaledLogoHeight = canvasHeight;
         return this.scaledLogo;
      }
   }

   /** Font scaled so the bar label stays proportionate on a larger window. */
   private Font getLoadingFont() {
      int size = Math.max(10, Math.round(12.0F * canvasHeight / FONT_REFERENCE_HEIGHT));
      return size == LOADING_FONT.getSize() ? LOADING_FONT : LOADING_FONT.deriveFont((float)size);
   }

   /* The canvas, for subclasses that draw straight onto the window rather than
      through the game's own framebuffer -- which, before startGame() has run,
      is everything, because the framebuffer does not exist yet. */
   protected static int canvasWidth() {
      return canvasWidth;
   }

   protected static int canvasHeight() {
      return canvasHeight;
   }

   /*
    * The window's Graphics, cached the way the loading screen caches it.
    * getGraphics() on an AWT frame allocates every call, and this is used once
    * a frame.
    */
   protected final Graphics screenGraphics() {
      if (this.loadingGraphics == null) {
         this.loadingGraphics = this.getGraphics();
      }

      return this.loadingGraphics;
   }

   /*
    * The same Graphics, but re-fetched from the window.
    *
    * The cached one carries the clip and the geometry the window had when it
    * was taken, so anything that draws straight onto the window across a
    * resize -- the boot-time worlds chooser is the one that does -- has to ask
    * for it again afterwards or it keeps painting into the old shape.
    */
   protected final Graphics refreshScreenGraphics() {
      this.loadingGraphics = this.getGraphics();
      return this.loadingGraphics;
   }

   /*
    * Put the loading screen back up after the game has already started.
    *
    * There is a second time this is needed now: choosing a different server
    * means a different set of assets, so the client goes back through the
    * loader rather than running one world's content against another's. The
    * splash is cleared with it -- it belongs to the server being left, and
    * leaving it up through the next download would say the wrong thing about
    * where the client is going.
    */
   protected final void enterLoadingScreen(String text) {
      this.loadingScreen = 2;
      this.loadingGraphics = this.getGraphics();
      this.setLogo(null);
      this.drawLoadingScreen(0, text);
   }

   protected final void leaveLoadingScreen() {
      this.loadingScreen = 0;
   }

   private int barX() {
      return Math.round(canvasWidth * BAR_X);
   }

   private int barY() {
      return Math.round(canvasHeight * BAR_Y);
   }

   private int barWidth() {
      return Math.round(canvasWidth * BAR_W);
   }

   private int barHeight() {
      return Math.round(canvasHeight * BAR_H);
   }

   protected final void changeThreadSleepModifier(int i) {
      this.threadSleepModifier = 1000 / i;
   }

   protected final void resetCurrentTimeArray() {
      for (int i = 0; i < 10; i++) {
         this.currentTimeArray[i] = 0L;
      }
   }

   @Override
   public final synchronized boolean keyDown(Event event, int key) {
      this.handleMenuKeyDown(key);
      this.keyDown = key;
      this.keyDown2 = key;
      this.lastActionTimeout = 0;
      if (key == 1006) {
         this.keyLeftDown = true;
      }

      if (key == 1007) {
         this.keyRightDown = true;
      }

      if (key == 1004) {
         this.keyUpDown = true;
      }

      if (key == 1005) {
         this.keyDownDown = true;
      }

      if ((char)key == ' ') {
         this.keySpaceDown = true;
      }

      if ((char)key == 'n' || (char)key == 'm') {
         this.keyNMDown = true;
      }

      if ((char)key == 'N' || (char)key == 'M') {
         this.keyNMDown = true;
      }

      if ((char)key == '{') {
         this.keyLeftBraceDown = true;
      }

      if ((char)key == '}') {
         this.keyRightBraceDown = true;
      }

      if ((char)key == 1008) {
         this.keyF1Toggle = !this.keyF1Toggle;
      }

      boolean validKeyDown = false;

      for (int j = 0; j < this.charSet.length(); j++) {
         if (key == this.charSet.charAt(j)) {
            validKeyDown = true;
            break;
         }
      }

      if (validKeyDown && this.inputText.length() < 20) {
         this.inputText = this.inputText + (char)key;
      }

      if (validKeyDown && this.inputMessage.length() < 80) {
         this.inputMessage = this.inputMessage + (char)key;
      }

      if (key == 8 && this.inputText.length() > 0) {
         this.inputText = this.inputText.substring(0, this.inputText.length() - 1);
      }

      if (key == 8 && this.inputMessage.length() > 0) {
         this.inputMessage = this.inputMessage.substring(0, this.inputMessage.length() - 1);
      }

      if (key == 10 || key == 13) {
         this.enteredText = this.inputText;
         this.enteredMessage = this.inputMessage;
      }

      this.handleKeyPressed(key);
      return true;
   }

   protected void handleMenuKeyDown(int key) {
   }

   /*
    * Every key, after the client has finished with it. keyDown() is final and
    * has been since the original, so a subclass that wants keys -- the script
    * runner does -- needs somewhere to hook that isn't an override.
    */
   protected void handleKeyPressed(int key) {
   }

   @Override
   public final synchronized boolean keyUp(Event event, int i) {
      this.keyDown = 0;
      if (i == 1006) {
         this.keyLeftDown = false;
      }

      if (i == 1007) {
         this.keyRightDown = false;
      }

      if (i == 1004) {
         this.keyUpDown = false;
      }

      if (i == 1005) {
         this.keyDownDown = false;
      }

      if ((char)i == ' ') {
         this.keySpaceDown = false;
      }

      if ((char)i == 'n' || (char)i == 'm') {
         this.keyNMDown = false;
      }

      if ((char)i == 'N' || (char)i == 'M') {
         this.keyNMDown = false;
      }

      if ((char)i == '{') {
         this.keyLeftBraceDown = false;
      }

      if ((char)i == '}') {
         this.keyRightBraceDown = false;
      }

      return true;
   }

   @Override
   public final synchronized boolean mouseMove(Event event, int i, int j) {
      this.mouseX = i;
      this.mouseY = j + this.yOffset;
      this.mouseDownButton = 0;
      this.lastActionTimeout = 0;
      return true;
   }

   @Override
   public final synchronized boolean mouseUp(Event event, int i, int j) {
      this.mouseX = i;
      this.mouseY = j + this.yOffset;
      this.mouseDownButton = 0;
      return true;
   }

   @Override
   public final synchronized boolean mouseDown(Event event, int i, int j) {
      this.mouseX = i;
      this.mouseY = j + this.yOffset;
      this.mouseDownButton = event.metaDown() ? 2 : 1;
      this.lastMouseDownButton = this.mouseDownButton;
      this.lastActionTimeout = 0;
      this.handleMouseDown(this.mouseDownButton, i, j);
      return true;
   }

   protected void handleMouseDown(int button, int x, int y) {
   }

   /*
    * The mouse wheel.
    *
    * This one does not arrive as an Event and does not go through handleEvent,
    * because java.awt.Event predates the wheel entirely -- there is no id for
    * it. GameFrame catches it in the newer event model and calls this directly.
    *
    * mouseX/mouseY are deliberately left alone. A wheel event carries the
    * pointer position, but writing it here would be a second source of truth
    * for a value the move and drag handlers already own, and the wheel can turn
    * mid-drag. The handler gets the coordinates as arguments instead.
    */
   public final synchronized void mouseWheel(int rotation, int x, int y) {
      this.lastActionTimeout = 0;
      this.handleMouseWheel(rotation, x, y + this.yOffset);
   }

   /*
    * GameFrame reports every drag here. The canvas statics keep the loading
    * screen's splash tracking the window immediately; the game itself applies
    * the new size from its own thread (mudclient.updateGame), never from this one.
    */
   final void frameResized(int width, int height) {
      canvasWidth = width;
      canvasHeight = height;
      this.resizedWidth = width;
      this.resizedHeight = height;
   }

   protected void handleMouseWheel(int rotation, int x, int y) {
   }

   /*
    * Whether x,y is the chat line -- the place a player taps to say "I want to
    * type a message". Takes canvas coordinates and applies the same yOffset the
    * mouse handlers do, so callers pass what the pointer reported and nothing
    * else has to know about the inset.
    *
    * Only the web client has any use for this: it has a keyboard to summon and
    * needs a reason to summon it. Desktop players type without clicking
    * anything, so it goes unasked there. See mudclient's override and
    * rscweb.web.MobileKeyboard.
    */
   public final synchronized boolean chatEntryTapped(int x, int y) {
      return this.isChatEntryArea(x, y + this.yOffset);
   }

   protected boolean isChatEntryArea(int x, int y) {
      return false;
   }

   /*
    * Whether a drag right now is panning something, and so must not also be
    * read as a swipe to scroll.
    *
    * Only the touch path asks. A mouse has a wheel and a button and they are
    * separate hardware, so dragging and scrolling can never be the same
    * gesture; one finger has to stand in for both, and over anything that pans
    * it is the drag that wins. See mudclient's override and DomEvents.
    */
   public final synchronized boolean dragPans() {
      return this.isDragPanning();
   }

   protected boolean isDragPanning() {
      return false;
   }

   /*
    * Whether x,y is open world -- somewhere a drag should turn the camera
    * rather than mean anything to the interface. False over the panels, the
    * message strip and every window that takes the screen.
    */
   public final synchronized boolean cameraDragArea(int x, int y) {
      return this.isCameraDragArea(x, y + this.yOffset);
   }

   protected boolean isCameraDragArea(int x, int y) {
      return false;
   }

   /*
    * One step of camera turn (dir +1 or -1) and one step of zoom (dir +1 to
    * come closer). Touch only: a keyboard already has the arrow keys, and this
    * is those keys' effect without the held-key state they work through --
    * which a synthetic press cannot reproduce, since it would be released
    * again before the game loop next looked at it.
    */
   public final synchronized void nudgeCamera(int dir) {
      this.turnCameraStep(dir);
   }

   public final synchronized void nudgeCameraZoom(int dir) {
      this.zoomCameraStep(dir);
   }

   protected void turnCameraStep(int dir) {
   }

   protected void zoomCameraStep(int dir) {
   }

   /*
    * True while something on screen wants text typed into it right now --
    * a script's ask(), a bank/shop "how many?", the login fields. Desktop
    * never calls this (a physical keyboard needs no summoning); the web
    * client polls it every frame to decide when to focus its hidden proxy
    * <input> and bring up a mobile on-screen keyboard. See mudclient's
    * override and rscweb.web.MobileKeyboard.
    */
   public boolean awaitingTextInput() {
      return false;
   }

   @Override
   public final synchronized boolean mouseDrag(Event event, int i, int j) {
      this.mouseX = i;
      this.mouseY = j + this.yOffset;
      this.mouseDownButton = event.metaDown() ? 2 : 1;
      return true;
   }

   /*
    * The window's close button, by way of GameFrame.handleEvent's WINDOW_DESTROY
    * (event 201). It keeps the applet-era name because that is what the frame
    * calls, and because it still means the same thing.
    *
    * Ask the game loop to wind up, give it two seconds, then leave. It used to
    * finish with gameWindowThread.stop(), which has thrown
    * UnsupportedOperationException unconditionally since JDK 20 -- so the
    * "forcing kill" branch did not force anything, it threw out of the close
    * handler. The thread is a daemon and close() has already logged the player
    * out, so exiting is both sufficient and what the code was reaching for.
    */
   public final void destroy() {
      this.exitTimeout = -1;

      try {
         Thread.sleep(2000L);
      } catch (Exception var2) {
      }

      if (this.exitTimeout == -1) {
         System.out.println("2 seconds expired, exiting");
         this.close();
         this.gameWindowThread = null;
         System.exit(0);
      }
   }

   private final void close() {
      this.exitTimeout = -2;
      System.out.println("Closing program");
      this.logoutAndStop();

      try {
         Thread.sleep(1000L);
      } catch (Exception var2) {
      }

      if (gameFrame != null) {
         gameFrame.dispose();
      }

      System.exit(0);
   }

   /*
    * The eight fonts the whole interface is drawn with, in slot order.
    *
    * Every one of them is Helvetica, and that is a problem outside Windows.
    * GameImage.loadFont does not load a font file -- it asks AWT to render 95
    * characters and keeps the pixels, so the panel layout is decided by
    * whatever metrics the local JDK hands back. Helvetica is not installed on
    * a normal Linux box; fontconfig substitutes something close but not equal
    * (see GameImage.helveticaFamily), every glyph comes out a pixel or two
    * wide or narrow, and the accumulated drift pushes labels past the edges of
    * panels that were laid out to the pixel in 2001.
    *
    * So each slot first looks for a pre-rendered bake, media/fonts/<name>.jf,
    * produced once on a machine that really has Helvetica. That is the same
    * pixel data the AWT path would have produced there, so the layout is right
    * everywhere. A checkout with no bakes present falls through to AWT and
    * behaves exactly as it always did -- the bakes are an improvement, not a
    * requirement.
    */
   private void loadFonts() {
      this.loadFont("h11p", 0);
      this.loadFont("h12b", 1);
      this.loadFont("h12p", 2);
      this.loadFont("h13b", 3);
      this.loadFont("h14b", 4);
      this.loadFont("h16b", 5);
      this.loadFont("h20b", 6);
      this.loadFont("h24b", 7);
   }

   private void loadFont(String name, int slot) {
      if (!loadBakedFont(name, slot)) {
         GameImage.loadFont(name, slot, this);
      }
   }

   /*
    * Reads media/fonts/<name>.jf into a font slot. Returns false -- quietly,
    * so a bare checkout does not nag -- if the file is absent, and loudly if
    * it is present but unusable, because a corrupt bake silently falling back
    * to substituted metrics is exactly the bug this is here to prevent.
    *
    * Header, all big-endian: magic "RSCF", version byte (1), name length byte,
    * that many ASCII name bytes, antialiased flag byte, one reserved zero
    * byte, then a four-byte payload length. The payload after it is
    * byte-for-byte what GameImage.fontData[slot] holds after an AWT load: 95
    * nine-byte glyph headers followed by the cropped 8-bit coverage bitmaps.
    * The name is checked against the slot being filled so that a mis-copied
    * file cannot quietly install h24b's metrics where h11p belongs.
    *
    * Nothing here runs loadFont's f/d retry chain. Those retries re-render at
    * a different weight to compensate for how live AWT rasterised the first
    * attempt; against data that was already rasterised correctly they would
    * only replace a good bake with a bad live one.
    */
   private static boolean loadBakedFont(String name, int slot) {
      File file = new File(mudclient.fontDirectory(), name + ".jf");
      if (!file.isFile()) {
         return false;
      }

      DataInputStream in = null;

      try {
         in = new DataInputStream(new FileInputStream(file));
         byte[] magic = new byte[4];
         in.readFully(magic);
         if (magic[0] != 'R' || magic[1] != 'S' || magic[2] != 'C' || magic[3] != 'F') {
            System.out.println("Ignoring " + file + ": not a baked font");
            return false;
         }

         int version = in.readUnsignedByte();
         if (version != 1) {
            System.out.println("Ignoring " + file + ": font format version " + version + ", expected 1");
            return false;
         }

         byte[] nameBytes = new byte[in.readUnsignedByte()];
         in.readFully(nameBytes);
         String baked = new String(nameBytes, "US-ASCII");
         if (!name.equals(baked)) {
            System.out.println("Ignoring " + file + ": holds font " + baked);
            return false;
         }

         boolean antialiased = in.readUnsignedByte() != 0;
         in.readUnsignedByte(); // reserved
         long length = (long)in.readInt() & 4294967295L;
         // 855 is the glyph header block alone; a payload that small has no
         // bitmaps at all and cannot be a real font.
         if (length <= 855L || length > (long)MAX_BAKED_FONT_BYTES) {
            System.out.println("Ignoring " + file + ": payload length " + length + " is not plausible");
            return false;
         }

         byte[] payload = new byte[(int)length];
         in.readFully(payload); // short file throws, and is reported below
         GameImage.setBakedFont(slot, payload, antialiased);
         return true;
      } catch (IOException var13) {
         System.out.println("Ignoring " + file + ": " + var13);
         return false;
      } finally {
         if (in != null) {
            try {
               in.close();
            } catch (IOException var12) {
            }
         }
      }
   }

   /* The largest bake seen is h24b at about 21KB; this is a sanity bound on a
      length field read from disk, not a real limit on font size. */
   private static final int MAX_BAKED_FONT_BYTES = 1048576;

   @Override
   public final void run() {
      if (this.loadingScreen == 1) {
         this.loadingScreen = 2;
         this.loadingGraphics = this.getGraphics();
         this.drawLoadingLogo();
         this.drawLoadingScreen(0, "Loading...");
         this.startGame();
         this.loadingScreen = 0;
      }

      int i = 0;
      int j = 256;
      int sleepTime = 1;
      int i1 = 0;

      for (int j1 = 0; j1 < 10; j1++) {
         this.currentTimeArray[j1] = System.currentTimeMillis();
      }

      while (this.exitTimeout >= 0) {
         if (this.exitTimeout > 0) {
            this.exitTimeout--;
            if (this.exitTimeout == 0) {
               this.close();
               this.gameWindowThread = null;
               return;
            }
         }

         int k1 = j;
         int i2 = sleepTime;
         j = 300;
         sleepTime = 1;
         long l1 = System.currentTimeMillis();
         if (this.currentTimeArray[i] == 0L) {
            j = k1;
            sleepTime = i2;
         } else if (l1 > this.currentTimeArray[i]) {
            j = (int)((long)(2560 * this.threadSleepModifier) / (l1 - this.currentTimeArray[i]));
         }

         if (j < 25) {
            j = 25;
         }

         if (j > 256) {
            j = 256;
            sleepTime = (int)((long)this.threadSleepModifier - (l1 - this.currentTimeArray[i]) / 10L);
            if (sleepTime < this.threadSleepTime) {
               sleepTime = this.threadSleepTime;
            }
         }

         try {
            Thread.sleep((long)sleepTime);
         } catch (InterruptedException var10) {
         }

         this.currentTimeArray[i] = l1;
         i = (i + 1) % 10;
         if (sleepTime > 1) {
            for (int j2 = 0; j2 < 10; j2++) {
               if (this.currentTimeArray[j2] != 0L) {
                  this.currentTimeArray[j2] = this.currentTimeArray[j2] + (long)sleepTime;
               }
            }
         }

         int k2 = 0;

         while (i1 < 256) {
            this.updateGame();
            i1 += j;
            if (++k2 > this.catchupTickLimit) {
               i1 = 0;
               this.overloadCount += 6;
               if (this.overloadCount > 25) {
                  this.overloadCount = 0;
                  this.keyF1Toggle = true;
               }
               break;
            }
         }

         this.overloadCount--;
         i1 &= 255;
         this.renderFrame();
      }

      if (this.exitTimeout == -1) {
         this.close();
      }

      this.gameWindowThread = null;
   }

   @Override
   public final void update(Graphics g) {
      this.paint(g);
   }

   @Override
   public final void paint(Graphics g) {
      if (this.loadingScreen == 2 && this.loadingLogo != null) {
         this.drawLoadingScreen(this.loadingPercent, this.loadingBarText);
      }
   }

   private final void drawLoadingLogo() {
      this.loadingGraphics.setColor(Color.black);
      Image logo = this.getScaledLogo();
      if (logo != null) {
         this.loadingGraphics.drawImage(logo, 0, 0, this);
      }

      this.loadFonts();
   }

   private final void drawLoadingScreen(int i, String s) {
      try {
         this.loadingGraphics.setColor(Color.black);
         this.loadingGraphics.fillRect(0, 0, canvasWidth, canvasHeight);
         Image logo = this.getScaledLogo();
         if (logo != null) {
            this.loadingGraphics.drawImage(logo, 0, 0, this);
         }

         this.loadingPercent = i;
         this.loadingBarText = s;
         int barX = this.barX();
         int barY = this.barY();
         int barW = this.barWidth();
         int barH = this.barHeight();
         Font font = this.getLoadingFont();
         this.loadingGraphics.setColor(BAR_COLOUR);
         this.loadingGraphics.fillRect(barX, barY, barW * i / 100, barH);
         this.loadingGraphics.setColor(Color.WHITE);
         this.drawString(this.loadingGraphics, s, font, barX + barW / 2, barY + barH / 2);
         if (this.loadingString != null) {
            this.drawString(this.loadingGraphics, this.loadingString, font, barX + barW / 2, barY - barH);
            return;
         }
      } catch (Exception var10) {
      }
   }

   public final void drawLoadingBarText(int i, String s) {
      try {
         this.loadingPercent = i;
         this.loadingBarText = s;
         int barX = this.barX();
         int barY = this.barY();
         int barW = this.barWidth();
         int barH = this.barHeight();
         int filled = barW * i / 100;
         /*
          * Repaint only the bar interior. The unfilled remainder is cleared to
          * black, which is what the recess in the artwork already is, so the
          * frame is never touched and there is no need to re-blit and re-scale
          * the whole splash on every tick.
          */
         this.loadingGraphics.setColor(BAR_COLOUR);
         this.loadingGraphics.fillRect(barX, barY, filled, barH);
         this.loadingGraphics.setColor(Color.black);
         this.loadingGraphics.fillRect(barX + filled, barY, barW - filled, barH);
         this.loadingGraphics.setColor(Color.WHITE);
         this.drawString(this.loadingGraphics, s, this.getLoadingFont(), barX + barW / 2, barY + barH / 2);
      } catch (Exception var9) {
      }
   }

   protected final void drawString(Graphics g, String s, Font font, int i, int j) {
      FontMetrics fontmetrics = (gameFrame == null ? this : gameFrame).getFontMetrics(font);
      fontmetrics.stringWidth(s);
      g.setFont(font);
      g.drawString(s, i - fontmetrics.stringWidth(s) / 2, j + fontmetrics.getHeight() / 4);
   }

   protected byte[] load(String filename) {
      int j = 0;
      int k = 0;
      byte[] abyte0 = null;

      try {
         InputStream inputstream = DataOperations.streamFromPath(filename);
         DataInputStream datainputstream = new DataInputStream(inputstream);
         byte[] abyte2 = new byte[6];
         datainputstream.readFully(abyte2, 0, 6);
         j = ((abyte2[0] & 255) << 16) + ((abyte2[1] & 255) << 8) + (abyte2[2] & 255);
         k = ((abyte2[3] & 255) << 16) + ((abyte2[4] & 255) << 8) + (abyte2[5] & 255);
         int l = 0;
         abyte0 = new byte[k];

         while (l < k) {
            int i1 = k - l;
            if (i1 > 1000) {
               i1 = 1000;
            }

            datainputstream.readFully(abyte0, l, i1);
            l += i1;
         }

         datainputstream.close();
      } catch (IOException var10) {
         var10.printStackTrace();
      }

      if (k != j) {
         byte[] abyte1 = new byte[j];
         DataFileDecrypter.unpackData(abyte1, j, abyte0, k, 0);
         return abyte1;
      } else {
         return abyte0;
      }
   }

   /*
    * The same JAG container, read from memory instead of a path -- the client
    * keeps no cache directory now, so this is what mudclient.load() calls.
    *
    * Header is u24 unpacked size then u24 packed size; equal sizes mean the
    * body was never compressed.
    */
   protected byte[] load(byte[] data) {
      int unpacked = ((data[0] & 255) << 16) + ((data[1] & 255) << 8) + (data[2] & 255);
      int packed = ((data[3] & 255) << 16) + ((data[4] & 255) << 8) + (data[5] & 255);
      byte[] body = new byte[packed];
      System.arraycopy(data, 6, body, 0, packed);
      if (packed == unpacked) {
         return body;
      } else {
         byte[] out = new byte[unpacked];
         DataFileDecrypter.unpackData(out, unpacked, body, packed, 0);
         return out;
      }
   }

   @Override
   public Graphics getGraphics() {
      return gameFrame != null ? gameFrame.getGraphics() : super.getGraphics();
   }

   @Override
   public Image createImage(int i, int j) {
      return gameFrame != null ? gameFrame.createImage(i, j) : super.createImage(i, j);
   }

   protected Socket makeSocket(String address, int port) throws IOException {
      Socket socket = new Socket(InetAddress.getByName(address), port);
      socket.setSoTimeout(30000);
      socket.setTcpNoDelay(true);
      return socket;
   }

   protected void startThread(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      thread.start();
   }

   public GameWindow() {
      canvasWidth = 512;
      canvasHeight = 384;
      this.threadSleepModifier = 20;
      this.catchupTickLimit = 1000;
      this.currentTimeArray = new long[10];
      this.loadingScreen = 1;
      this.loadingBarText = "Loading...";
      this.keyLeftBraceDown = false;
      this.keyRightBraceDown = false;
      this.keyLeftDown = false;
      this.keyRightDown = false;
      this.keyUpDown = false;
      this.keyDownDown = false;
      this.keySpaceDown = false;
      this.keyNMDown = false;
      this.threadSleepTime = 1;
      this.keyF1Toggle = false;
      this.inputText = "";
      this.enteredText = "";
      this.inputMessage = "";
      this.enteredMessage = "";
   }

}
