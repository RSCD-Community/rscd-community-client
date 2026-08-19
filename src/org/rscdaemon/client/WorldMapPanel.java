package org.rscdaemon.client;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.rscdaemon.client.util.Config;

/*
 * The world map.
 *
 * SkullOrca had a World Map button with an empty clicked() body, and so did
 * this client until now -- it was drawn dim and said so. This is that button.
 *
 * Three things make it worth writing down.
 *
 * The map is not in the jar. worldmap.png, mapkey.gif and worldmap.cal are
 * fetched from Config.CACHE_URL, the same place every other asset comes from,
 * so a server that has redrawn its landscape redraws its map and every client
 * that connects to it is looking at the right world. It is deliberately NOT in
 * the gamefiles array: that array is downloaded in full before the login
 * screen appears, and a megabyte of map that most sessions never open has no
 * business being in the way of logging in. It is fetched on first open, on its
 * own thread, and the screen says what it is doing while that happens.
 *
 * The projection is data, not constants. worldmap.cal carries scale, origin_x
 * and origin_y; the defaults here are the fallback for a server that has not
 * shipped one. They were measured rather than guessed -- see the comments in
 * that file -- and the awkward-looking minus sign on x is real: game x grows
 * westward and pixels grow eastward, so the map is a mirror of the coordinate
 * space.
 *
 * The marker only appears if the player is actually on the map. Every storey
 * of the world is stacked 944 tiles apart in the same coordinate space, so a
 * player in a dungeon, upstairs, or in a quest cave has a perfectly valid
 * y that projects onto the overworld surface -- which would put a "you are
 * here" dot in the middle of a forest they are nowhere near. Below ground the
 * map says where they are in words instead of lying with a dot.
 */
final class WorldMapPanel {
   /* Fallback projection, used when the server ships no worldmap.cal. */
   private static final double DEFAULT_SCALE = 4.5651;
   private static final double DEFAULT_ORIGIN_X = 3500.0;
   private static final double DEFAULT_ORIGIN_Y = -453.2;

   /* Each storey of the world sits this far north of the one below it. */
   private static final int STOREY = 944;

   private static final String MAP_FILE = "worldmap.png";
   private static final String KEY_FILE = "mapkey.gif";
   private static final String CAL_FILE = "worldmap.cal";

   private static final int IDLE = 0;
   private static final int LOADING = 1;
   private static final int READY = 2;
   private static final int FAILED = 3;

   /* Screen pixels per map pixel. Fully out is computed from the image so the
      whole map fits the view; fully in is twice life size, which is as far as
      a 4.5-pixel tile is worth magnifying. */
   private static final double ZOOM_MAX = 2.0;
   private static final double ZOOM_STEP = 1.25;

   /* The control strip across the bottom. */
   private static final int BAR_H = 26;
   private static final int BTN_W = 30;
   private static final int BTN_H = 18;

   private final mudclient rs;

   /* Read by the loader thread to find out whether the screen it is loading for
      is still on screen. */
   private volatile boolean open;

   /* Written by the loader thread, read by the client thread every frame. */
   private volatile int state = IDLE;
   private volatile String status = "";
   private volatile BufferedImage map;
   private volatile BufferedImage legend;
   private volatile Thread loader;

   /* Kept after the first fetch so re-opening is a decode rather than another
      download. The decoded map is ~55MB and is dropped on close; the bytes it
      came from are one megabyte and are not. */
   private volatile byte[] mapBytes;
   private volatile byte[] keyBytes;

   private double scale = DEFAULT_SCALE;
   private double originX = DEFAULT_ORIGIN_X;
   private double originY = DEFAULT_ORIGIN_Y;

   /* Rectangles of the world the artist drew somewhere other than where the
      projection puts them, each with the pixel nudge that catches the drawing
      up. This map pulls the Kharazi jungle out as an inset with a sea between
      it and Shilo that does not exist in the game; without the nudge the
      marker for anyone in the jungle floats in that invented water. From
      worldmap.cal: region=x1,y1,x2,y2,dx,dy in game tiles and map pixels.
      First matching region wins. */
   private double[][] regions = new double[0][];

   /* The map pixel currently under the centre of the view, and how magnified
      it is. Everything on screen is derived from these three numbers. */
   private double centreX;
   private double centreY;
   private double zoom = 1.0;

   private boolean showLegend;
   private int legendOffset;

   /* Set when the map should jump to the player as soon as it has finished
      loading -- open() usually runs before there is an image to centre on. */
   private boolean centreOnPlayer = true;

   private boolean pressed;
   private boolean consumed;
   private int dragX;
   private int dragY;
   private int tick;

   /*
    * Wheel notches that have arrived but not yet been spent, and where the
    * pointer was when the last of them did.
    *
    * The wheel is the one input here that does not come in through update():
    * GameFrame hands it over on the AWT event thread, while everything else on
    * this panel runs on the client thread. Zooming from there would be moving
    * centreX/centreY/zoom out from under draw() mid-frame. So the notches are
    * parked and update() spends them, which is the same shape as the fix that
    * moved centreNow() off the loader thread.
    */
   private final AtomicInteger wheelNotches = new AtomicInteger();
   private volatile int wheelX;
   private volatile int wheelY;

   private Rectangle btnOut = new Rectangle();
   private Rectangle btnIn = new Rectangle();
   private Rectangle btnFit = new Rectangle();
   private Rectangle btnMe = new Rectangle();
   private Rectangle btnKey = new Rectangle();
   private Rectangle btnClose = new Rectangle();

   WorldMapPanel(mudclient rs) {
      this.rs = rs;
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
      this.showLegend = false;
      this.legendOffset = 0;
      this.centreOnPlayer = true;
      this.zoom = 1.0;
      /*
       * Open with the button considered already held, so the click that opened
       * this screen cannot also be read as a click on it. The Worlds screen
       * learned this the hard way -- the press that opened it was still down on
       * the first frame and fired at whatever widget sat under the pointer.
       */
      this.pressed = true;
      this.consumed = true;

      if (this.state != READY) {
         this.beginLoad();
      } else {
         this.centreNow();
      }
   }

   void close() {
      this.open = false;

      /*
       * Let the raster go. It is 3506x3928 in 32-bit colour -- 55MB, four times
       * everything else the client holds -- and there is no reason for it to
       * sit in the heap while somebody plays. Loading.xml.data is dropped after
       * the splash for the same reason. The compressed bytes stay, so opening
       * it again costs a decode and not a download.
       */
      this.map = null;
      this.legend = null;
      if (this.state == READY || this.state == LOADING) {
         this.state = IDLE;
      }
   }

   /*
    * ---- loading ----
    */

   private void beginLoad() {
      if (this.loader != null && this.loader.isAlive()) {
         return;
      }

      this.state = LOADING;
      this.status = this.mapBytes == null ? "Fetching the map.." : "Drawing the map..";

      Thread t = new Thread(new Runnable() {
         public void run() {
            WorldMapPanel.this.load();
         }
      }, "world-map");
      t.setDaemon(true);
      this.loader = t;
      t.start();
   }

   private void load() {
      try {
         if (this.mapBytes == null) {
            this.mapBytes = get(MAP_FILE);
            if (this.mapBytes == null) {
               this.status = "This world has no map. (" + MAP_FILE + " was not found on " + Config.CACHE_URL + ")";
               this.state = FAILED;
               return;
            }

            /* Neither of these is worth failing over. A world may well ship a
               map and no legend, and one that has not shipped a .cal is saying
               it uses the vanilla geometry. */
            this.keyBytes = get(KEY_FILE);
            this.readCalibration(get(CAL_FILE));
         }

         this.status = "Drawing the map..";
         BufferedImage img = decode(this.mapBytes);
         if (img == null) {
            this.status = MAP_FILE + " could not be read as an image.";
            this.state = FAILED;
            return;
         }

         BufferedImage key = null;
         if (this.keyBytes != null) {
            key = decode(this.keyBytes);
         }

         this.legend = key;
         this.map = img;
         this.state = READY;

         /*
          * Checked on both sides of the handover, which is what makes it
          * airtight: if close() ran before this line we drop what we just
          * published, and if it runs after, its own null wins. Without it a
          * player who opened the map and pressed Escape while it decoded was
          * left holding 55MB for a screen that is not on screen.
          *
          * Nothing here touches the view -- centring is the client thread's
          * job, from draw(), because centreX/centreY/zoom are read there every
          * frame and are not worth making volatile for one event.
          */
         if (!this.open) {
            this.close();
         }
      } catch (OutOfMemoryError e) {
         /* Explicitly, because this is the one thing here big enough to cause
            it and dying silently would look like the map simply never opened.
            Everything the attempt was holding is already unreachable. */
         this.map = null;
         this.legend = null;
         this.status = "Not enough memory to open the map.";
         this.state = FAILED;
      } catch (Exception e) {
         this.status = "The map could not be loaded: " + e;
         this.state = FAILED;
      }
   }

   /*
    * One asset, or null if it is not there.
    *
    * loadcache() in mudclient is fatal on failure by design -- the game cannot
    * start without its data. This one cannot be, because a world with no map is
    * a world with no map and not a world that is broken.
    */
   private static byte[] get(String name) {
      String url = Config.CACHE_URL + "/" + name;

      try {
         HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
         c.setConnectTimeout(8000);
         c.setReadTimeout(30000);
         if (c.getResponseCode() != 200) {
            System.err.println(url + " -> HTTP " + c.getResponseCode());
            return null;
         }

         int length = c.getContentLength();
         ByteArrayOutputStream out = new ByteArrayOutputStream(length > 0 ? length : 65536);
         InputStream in = c.getInputStream();

         try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
               out.write(buf, 0, n);
            }
         } finally {
            in.close();
         }

         return out.toByteArray();
      } catch (Exception e) {
         System.err.println(url + " -> " + e);
         return null;
      }
   }

   /*
    * Decode straight into the format the screen is in.
    *
    * ImageIO would hand back whatever the file happens to be -- 3BYTE_BGR for
    * an RGB png, BYTE_INDEXED for a gif -- and Java2D would then convert every
    * pixel on every frame while it scaled it. Giving the reader a destination
    * image of TYPE_INT_RGB moves that conversion to the one place it belongs,
    * and costs nothing extra in memory: the alternative is decoding into one
    * 41MB raster and copying it into a second 55MB one.
    */
   private static BufferedImage decode(byte[] bytes) throws Exception {
      /*
       * The low-level reader API below is a desktop-only path: the web
       * build's ImageIO stubs createImageInputStream/getImageReaders to
       * null/empty unconditionally (it only backs the simple read(), via the
       * browser's own decoder) -- see rscweb.imageio.ImageIO. Without this
       * fallback the map failed to load on every single attempt in a
       * browser, network and assets notwithstanding: iis was always null,
       * so this returned null before a byte of the fetch was ever in
       * question. convert() already exists for the gif key's ClassCastException
       * below; it does the same job for a reader that was never offered at all.
       */
      ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
      if (iis == null) {
         return convert(bytes);
      }

      try {
         Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
         if (!readers.hasNext()) {
            return convert(bytes);
         }

         ImageReader reader = readers.next();

         try {
            reader.setInput(iis);
            BufferedImage dst = new BufferedImage(reader.getWidth(0), reader.getHeight(0), BufferedImage.TYPE_INT_RGB);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setDestination(dst);
            return reader.read(0, param);
         } catch (Exception e) {
            /*
             * Not every reader will write into any destination you hand it: the
             * gif one produces byte samples and throws a ClassCastException
             * partway through an int raster, which is what mapkey.gif does.
             * Read it whichever way it wants and convert afterwards. The files
             * this happens to are the small ones -- the trick above exists for
             * the 55MB map, and the map is a png.
             */
            return convert(bytes);
         } finally {
            reader.dispose();
         }
      } finally {
         iis.close();
      }
   }

   /* Read it however it comes and copy it into the screen's format. Starts
      from the bytes again because the failed attempt left the stream part-read
      and the destination part-filled. */
   private static BufferedImage convert(byte[] bytes) throws Exception {
      BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
      if (raw == null) {
         return null;
      }

      if (raw.getType() == BufferedImage.TYPE_INT_RGB) {
         return raw;
      }

      BufferedImage dst = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = dst.createGraphics();

      try {
         g.drawImage(raw, 0, 0, null);
      } finally {
         g.dispose();
      }

      return dst;
   }

   /*
    * scale / origin_x / origin_y, one per line. Anything unparseable leaves the
    * measured default in place rather than throwing the screen away over it.
    */
   private void readCalibration(byte[] bytes) {
      this.scale = DEFAULT_SCALE;
      this.originX = DEFAULT_ORIGIN_X;
      this.originY = DEFAULT_ORIGIN_Y;
      this.regions = new double[0][];

      if (bytes == null) {
         return;
      }

      String[] lines = new String(bytes).split("\n");
      java.util.List<double[]> found = new java.util.ArrayList<double[]>();

      for (int i = 0; i < lines.length; i++) {
         String line = lines[i].trim();
         int eq = line.indexOf('=');
         if (line.length() == 0 || line.charAt(0) == '#' || eq <= 0) {
            continue;
         }

         String name = line.substring(0, eq).trim();
         String value = line.substring(eq + 1).trim();

         try {
            if ("region".equals(name)) {
               String[] parts = value.split(",");
               if (parts.length == 6) {
                  double[] r = new double[6];
                  for (int p = 0; p < 6; p++) {
                     r[p] = Double.parseDouble(parts[p].trim());
                  }
                  found.add(r);
               } else {
                  System.err.println(CAL_FILE + ": ignoring " + line);
               }
               continue;
            }

            double d = Double.parseDouble(value);
            if ("scale".equals(name) && d > 0.0) {
               this.scale = d;
            } else if ("origin_x".equals(name)) {
               this.originX = d;
            } else if ("origin_y".equals(name)) {
               this.originY = d;
            }
         } catch (NumberFormatException e) {
            System.err.println(CAL_FILE + ": ignoring " + line);
         }
      }

      this.regions = found.toArray(new double[0][]);
   }

   /*
    * ---- the projection ----
    */

   /* A game tile's pixel on the map: the linear projection, plus the nudge of
      whichever moved region claims the tile first. Every placement goes
      through here -- projecting one axis on its own would miss the nudge. */
   private double[] project(int[] tile) {
      double px = this.originX - this.scale * tile[0];
      double py = this.originY + this.scale * tile[1];

      for (int i = 0; i < this.regions.length; i++) {
         double[] r = this.regions[i];
         if (tile[0] >= r[0] && tile[0] <= r[2] && tile[1] >= r[1] && tile[1] <= r[3]) {
            px += r[4];
            py += r[5];
            break;
         }
      }

      return new double[] { px, py };
   }

   /* The player's coordinates, or null if there is nothing to place. */
   private int[] playerTile() {
      if (this.rs.loggedIn != 1 || this.rs.ourPlayer == null) {
         return null;
      }

      return new int[] { this.rs.sectionX + this.rs.areaX, this.rs.sectionY + this.rs.areaY };
   }

   /* True when the player is standing on the surface rather than on one of the
      storeys stacked above and below it. */
   private boolean overworld(int[] tile) {
      return tile != null && tile[1] >= 0 && tile[1] < STOREY;
   }

   /* ..and additionally inside the part of the world the map actually draws.
      A map that covers half a world should not claim the other half. */
   private boolean onMap(int[] tile) {
      BufferedImage img = this.map;
      if (img == null || !this.overworld(tile)) {
         return false;
      }

      double[] p = this.project(tile);
      return p[0] >= 0.0 && p[0] < img.getWidth() && p[1] >= 0.0 && p[1] < img.getHeight();
   }

   /*
    * ---- view ----
    */

   private int viewW() {
      return this.rs.windowWidth;
   }

   private int viewH() {
      return this.rs.surfaceHeight() - BAR_H;
   }

   /* Zoomed out far enough that the whole map is on screen. Nothing may go
      further out than this: past it the map is a stamp in a field of black. */
   private double minZoom() {
      BufferedImage img = this.map;
      if (img == null) {
         return 0.1;
      }

      double z = Math.min((double)this.viewW() / img.getWidth(), (double)this.viewH() / img.getHeight());
      return Math.min(z, ZOOM_MAX);
   }

   private void centreNow() {
      BufferedImage img = this.map;
      if (img == null) {
         return;
      }

      int[] tile = this.playerTile();

      if (this.onMap(tile)) {
         double[] p = this.project(tile);
         this.centreX = p[0];
         this.centreY = p[1];
      } else {
         this.centreX = img.getWidth() / 2.0;
         this.centreY = img.getHeight() / 2.0;
      }

      this.zoom = Math.max(this.minZoom(), Math.min(ZOOM_MAX, this.zoom));
      this.clamp();
      this.centreOnPlayer = false;
   }

   /*
    * Keep the view over the map.
    *
    * Along an axis where the map is wider than the view the centre is bounded
    * so no edge comes inside the frame; along one where it is narrower -- which
    * happens on this map's x as soon as you zoom out, it being far taller than
    * it is wide -- the only sensible place for it is the middle.
    */
   private void clamp() {
      BufferedImage img = this.map;
      if (img == null) {
         return;
      }

      double halfW = this.viewW() / this.zoom / 2.0;
      double halfH = this.viewH() / this.zoom / 2.0;

      if (img.getWidth() <= halfW * 2.0) {
         this.centreX = img.getWidth() / 2.0;
      } else {
         this.centreX = Math.max(halfW, Math.min(img.getWidth() - halfW, this.centreX));
      }

      if (img.getHeight() <= halfH * 2.0) {
         this.centreY = img.getHeight() / 2.0;
      } else {
         this.centreY = Math.max(halfH, Math.min(img.getHeight() - halfH, this.centreY));
      }
   }

   /* Zoom about the centre of the view. This is what the buttons and the
      keyboard do: neither of them is pointing at anything in particular. */
   private void zoomBy(double factor) {
      double z = this.zoom * factor;
      this.zoom = Math.max(this.minZoom(), Math.min(ZOOM_MAX, z));
      this.clamp();
   }

   /*
    * Zoom about a point on screen, keeping whatever is under the pointer under
    * the pointer. This is what the wheel does, and it is the difference between
    * zooming in on Varrock and zooming in on wherever the middle happened to be
    * and then having to drag back to Varrock.
    *
    * Both cases of drawMap's transform invert to the same thing. Un-letterboxed
    * the source rectangle starts at centre - view/zoom/2, so a screen pixel is
    * centre + (screen - view/2)/zoom; letterboxed, the axis is drawn at an
    * offset from zero, but clamp() has already put the centre at the middle of
    * the image by then, which works out identical.
    *
    * Clamping afterwards can still slide the view at the edges. Holding the
    * point truly fixed there would mean showing what is not on the map.
    */
   private void zoomAt(double factor, int screenX, int screenY) {
      int w = this.viewW();
      int h = this.viewH();

      if (this.map == null || screenX < 0 || screenX >= w || screenY < 0 || screenY >= h) {
         this.zoomBy(factor);
         return;
      }

      double was = this.zoom;
      double z = Math.max(this.minZoom(), Math.min(ZOOM_MAX, was * factor));

      if (z == was) {
         return;
      }

      double offX = screenX - w / 2.0;
      double offY = screenY - h / 2.0;
      double atX = this.centreX + offX / was;
      double atY = this.centreY + offY / was;

      this.zoom = z;
      this.centreX = atX - offX / z;
      this.centreY = atY - offY / z;
      this.clamp();
   }

   /*
    * ---- input ----
    */

   /*
    * Called every frame with the button as it is held, not as it is clicked:
    * panning is a drag, and a drag is only visible in the held state.
    */
   void update(int mouseX, int mouseY, int buttonHeld) {
      if (!this.open) {
         return;
      }

      this.tick++;
      this.layout();
      this.spendWheel();

      /*
       * The arrow keys rotate the camera and raise it, from flags that
       * GameWindow sets before anything downstream sees the key. Nothing on
       * screen is the game right now, so clearing them each frame is how those
       * keys come to belong to this panel -- handleKey below then pans with
       * them, and the world underneath stays where it was left.
       */
      this.rs.keyLeftDown = false;
      this.rs.keyRightDown = false;
      this.rs.keyUpDown = false;
      this.rs.keyDownDown = false;

      boolean down = buttonHeld != 0;

      if (down && !this.pressed) {
         this.consumed = this.press(mouseX, mouseY);
         this.dragX = mouseX;
         this.dragY = mouseY;
      } else if (down && !this.consumed) {
         this.drag(mouseX, mouseY);
      }

      this.pressed = down;
      if (!down) {
         this.consumed = false;
      }
   }

   /*
    * The mouse wheel, arriving from GameFrame on the AWT event thread. Nothing
    * is done with it here beyond noting it down -- see spendWheel().
    *
    * A notch is negative away from the hand, which everything else on a desktop
    * treats as zoom in, so the sign is flipped when it is spent.
    */
   void wheel(int notches, int x, int y) {
      if (!this.open || notches == 0) {
         return;
      }

      this.wheelX = x;
      this.wheelY = y;
      this.wheelNotches.addAndGet(notches);
   }

   /* Spend every notch banked since the last frame in one step, so a fast flick
      is one zoom rather than a queue of them arriving over the next second. */
   private void spendWheel() {
      int notches = this.wheelNotches.getAndSet(0);

      if (notches == 0) {
         return;
      }

      int x = this.wheelX;
      int y = this.wheelY;

      /* Over the legend the wheel scrolls it, the same as dragging it does.
         A line of the key is about what a notch should move. */
      if (this.showLegend && this.legend != null && this.legendRect().contains(x, y)) {
         this.scrollLegend(notches * 24);
         return;
      }

      if (this.state != READY) {
         return;
      }

      this.zoomAt(Math.pow(ZOOM_STEP, -notches), x, y);
   }

   /* Returns true if the press landed on something, meaning it is not a drag. */
   private boolean press(int mouseX, int mouseY) {
      if (this.btnClose.contains(mouseX, mouseY)) {
         this.close();
         return true;
      }

      if (this.btnKey.contains(mouseX, mouseY)) {
         this.showLegend = !this.showLegend && this.legend != null;
         this.legendOffset = 0;
         return true;
      }

      if (this.state != READY) {
         return true;
      }

      if (this.btnOut.contains(mouseX, mouseY)) {
         this.zoomBy(1.0 / ZOOM_STEP);
         return true;
      }

      if (this.btnIn.contains(mouseX, mouseY)) {
         this.zoomBy(ZOOM_STEP);
         return true;
      }

      if (this.btnFit.contains(mouseX, mouseY)) {
         this.zoom = this.minZoom();
         this.clamp();
         return true;
      }

      if (this.btnMe.contains(mouseX, mouseY)) {
         if (this.onMap(this.playerTile())) {
            this.centreNow();
         }

         return true;
      }

      /* Anything else is a grab, on the map or on the legend depending on
         which one is under the pointer. */
      return false;
   }

   private void drag(int mouseX, int mouseY) {
      int dx = mouseX - this.dragX;
      int dy = mouseY - this.dragY;
      this.dragX = mouseX;
      this.dragY = mouseY;

      if (dx == 0 && dy == 0) {
         return;
      }

      if (this.showLegend && this.legend != null && this.legendRect().contains(mouseX, mouseY)) {
         this.scrollLegend(-dy);
         return;
      }

      if (this.state != READY) {
         return;
      }

      /* The map moves with the pointer, so the view centre moves against it. */
      this.centreX -= dx / this.zoom;
      this.centreY -= dy / this.zoom;
      this.clamp();
   }

   private void scrollLegend(int by) {
      BufferedImage key = this.legend;
      if (key == null) {
         return;
      }

      int over = key.getHeight() - this.legendRect().height;
      if (over <= 0) {
         this.legendOffset = 0;
         return;
      }

      this.legendOffset = Math.max(0, Math.min(over, this.legendOffset + by));
   }

   /*
    * Every key while this is up -- mudclient hands the whole keyboard over
    * rather than letting the camera and the chat box see them too.
    */
   boolean handleKey(int key) {
      if (!this.open) {
         return false;
      }

      if (key == 27) {
         /* Escape backs out one layer at a time: the legend first, then the
            map. Closing both at once is how you lose the map because you
            wanted the legend gone. */
         if (this.showLegend) {
            this.showLegend = false;
         } else {
            this.close();
         }

         return true;
      }

      char c = (char)key;

      if (c == 'k' || c == 'K') {
         this.showLegend = !this.showLegend && this.legend != null;
         this.legendOffset = 0;
         return true;
      }

      if (this.showLegend) {
         if (key == 1004) {
            this.scrollLegend(-20);
            return true;
         }

         if (key == 1005) {
            this.scrollLegend(20);
            return true;
         }
      }

      if (this.state != READY) {
         return true;
      }

      /* '=' as well as '+' because '+' is a shifted key on every keyboard this
         is likely to meet and '=' is the same button unshifted. */
      if (c == '+' || c == '=') {
         this.zoomBy(ZOOM_STEP);
         return true;
      }

      if (c == '-' || c == '_') {
         this.zoomBy(1.0 / ZOOM_STEP);
         return true;
      }

      if (c == 'f' || c == 'F') {
         this.zoom = this.minZoom();
         this.clamp();
         return true;
      }

      if (c == 'c' || c == 'C') {
         if (this.onMap(this.playerTile())) {
            this.centreNow();
         }

         return true;
      }

      int step = (int)(48.0 / this.zoom);
      if (step < 1) {
         step = 1;
      }

      switch (key) {
         case 1004:
            this.centreY -= step;
            this.clamp();
            return true;
         case 1005:
            this.centreY += step;
            this.clamp();
            return true;
         case 1006:
            this.centreX -= step;
            this.clamp();
            return true;
         case 1007:
            this.centreX += step;
            this.clamp();
            return true;
      }

      return true;
   }

   /*
    * ---- drawing ----
    */

   void draw(GameImageMiddleMan gg, int mouseX, int mouseY) {
      if (!this.open) {
         return;
      }

      /* The map usually arrives several frames after open() asked for it, so
         this is where it gets pointed at the player -- on the client thread,
         and before anything is drawn from it. */
      if (this.centreOnPlayer && this.state == READY) {
         this.centreNow();
      }

      Graphics2D g = Skin.open(gg, this.rs.windowWidth, this.rs.surfaceHeight());

      try {
         int w = this.viewW();
         int h = this.viewH();

         g.setColor(Skin.colour(Skin.BG_DEEP));
         g.fillRect(0, 0, this.rs.windowWidth, this.rs.surfaceHeight());

         BufferedImage img = this.map;
         if (this.state == READY && img != null) {
            this.drawMap(g, img, w, h);
         } else {
            this.drawStatus(g, w, h);
         }

         if (this.showLegend && this.legend != null) {
            this.drawLegend(g);
         }

         this.drawBar(g, mouseX, mouseY);
      } finally {
         g.dispose();
      }
   }

   private void drawMap(Graphics2D g, BufferedImage img, int w, int h) {
      /* The source rectangle, in map pixels, that fills the view. */
      double halfW = w / this.zoom / 2.0;
      double halfH = h / this.zoom / 2.0;
      double sx1 = this.centreX - halfW;
      double sy1 = this.centreY - halfH;
      double sx2 = this.centreX + halfW;
      double sy2 = this.centreY + halfH;

      /* Zoomed out past the point where the map is narrower than the view it
         has to be letterboxed, or drawImage would stretch it out of shape. */
      int dx1 = 0;
      int dy1 = 0;
      int dx2 = w;
      int dy2 = h;

      if (sx1 < 0.0 || sx2 > img.getWidth()) {
         double vw = img.getWidth() * this.zoom;
         dx1 = (int)((w - vw) / 2.0);
         dx2 = dx1 + (int)vw;
         sx1 = 0.0;
         sx2 = img.getWidth();
      }

      if (sy1 < 0.0 || sy2 > img.getHeight()) {
         double vh = img.getHeight() * this.zoom;
         dy1 = (int)((h - vh) / 2.0);
         dy2 = dy1 + (int)vh;
         sy1 = 0.0;
         sy2 = img.getHeight();
      }

      /* Bilinear when shrinking, nearest when magnifying: a map zoomed out to
         a seventh of its size aliases into confetti without it, and one zoomed
         past 1:1 only goes soft if it is smoothed. */
      g.setRenderingHint(
         RenderingHints.KEY_INTERPOLATION,
         this.zoom < 1.0 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

      /* Intersect and restore rather than set and clear: Skin.open() has
         already clipped to the game view, and the buffer behind it is wider
         than the view whenever the side panels are laid out beside it. */
      Shape outer = g.getClip();
      g.clipRect(0, 0, w, h);
      g.drawImage(img, dx1, dy1, dx2, dy2, (int)sx1, (int)sy1, (int)sx2, (int)sy2, null);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

      int[] tile = this.playerTile();
      if (this.onMap(tile)) {
         double[] p = this.project(tile);
         double px = dx1 + (p[0] - sx1) * this.zoom;
         double py = dy1 + (p[1] - sy1) * this.zoom;
         if (px >= 0.0 && px < w && py >= 0.0 && py < h) {
            this.drawMarker(g, (int)px, (int)py, w, h);
         }
      }

      g.setClip(outer);
   }

   /*
    * You are here.
    *
    * Two rings and a cross rather than a filled dot, because the thing under it
    * is the part of the map the player most wants to read.
    */
   private void drawMarker(Graphics2D g, int x, int y, int w, int h) {
      g.setColor(Skin.colour(0x000000, 150));
      g.drawOval(x - 7, y - 7, 14, 14);
      g.drawOval(x - 5, y - 5, 10, 10);

      g.setColor(Skin.colour(Skin.AMBER));
      g.drawOval(x - 6, y - 6, 12, 12);
      g.drawLine(x, y - 9, x, y - 3);
      g.drawLine(x, y + 3, x, y + 9);
      g.drawLine(x - 9, y, x - 3, y);
      g.drawLine(x + 3, y, x + 9, y);

      g.setColor(Skin.colour(Skin.GOLD_MAX));
      g.fillRect(x - 1, y - 1, 2, 2);

      /* The label goes above the marker unless that is off the top, and left
         of it unless that is off the right edge. */
      String label = "You are here";
      int tw = Skin.width(g, label, Skin.FONT_SMALL);
      int tx = Math.max(2, Math.min(w - tw - 2, x - tw / 2));
      int ty = y - 11 < 12 ? y + 21 : y - 11;

      g.setColor(Skin.colour(Skin.BG_DEEP, 190));
      g.fillRect(tx - 3, ty - 10, tw + 6, 13);
      Skin.text(g, label, tx, ty, Skin.FONT_SMALL, Skin.GOLD_HI);
   }

   /*
    * What the screen says when there is no map to show: loading, or why not,
    * and where the player is if they are somewhere the map cannot draw.
    */
   private void drawStatus(Graphics2D g, int w, int h) {
      Skin.title(g, w / 2, h / 2 - 24, "World Map");

      String message = this.status;
      if (this.state == IDLE) {
         message = "Re-open the map to load it.";
      }

      int y = h / 2;
      String[] lines = wrap(g, message, w - 60);

      for (int i = 0; i < lines.length; i++) {
         Skin.textCentre(g, lines[i], w / 2, y, Skin.FONT_BODY, this.state == FAILED ? Skin.EMBER_HI : Skin.TEXT);
         y += 15;
      }

      if (this.state == LOADING) {
         /* A sweep rather than a progress bar. The fetch could report bytes
            against Content-Length, but the decode that follows it cannot report
            anything at all, and a bar that fills and then sits at 100% for a
            second looks more broken than one that never claimed to know. */
         int barX = w / 2 - 80;
         int barY = y + 8;
         Skin.meter(g, barX, barY, 160, 8, 0.0);
         int sweep = (this.tick * 3) % 200 - 40;
         Shape outer = g.getClip();
         g.clipRect(barX + 1, barY + 1, 158, 6);
         g.setColor(Skin.colour(Skin.GOLD, 200));
         g.fillRect(barX + sweep, barY + 1, 40, 6);
         g.setClip(outer);
      }
   }

   /* Word wrap, because a failure message carries a URL and a URL is long. */
   private static String[] wrap(Graphics2D g, String s, int maxWidth) {
      String[] words = s.split(" ");
      StringBuilder out = new StringBuilder();
      StringBuilder line = new StringBuilder();

      for (int i = 0; i < words.length; i++) {
         String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
         if (line.length() > 0 && Skin.width(g, candidate, Skin.FONT_BODY) > maxWidth) {
            out.append(line).append('\n');
            line.setLength(0);
            line.append(words[i]);
         } else {
            line.setLength(0);
            line.append(candidate);
         }
      }

      out.append(line);
      return out.toString().split("\n");
   }

   private Rectangle legendRect() {
      BufferedImage key = this.legend;
      int w = key == null ? 200 : key.getWidth();
      int h = key == null ? 100 : key.getHeight();

      int available = this.viewH() - 30;
      if (h > available) {
         h = available;
      }

      int x = this.viewW() - w - 12;
      if (x < 6) {
         x = 6;
      }

      return new Rectangle(x, 18, w, h);
   }

   private void drawLegend(Graphics2D g) {
      BufferedImage key = this.legend;
      Rectangle r = this.legendRect();

      Skin.panel(g, r.x - 6, r.y - 16, r.width + 12, r.height + 22);
      Skin.heading(g, r.x, r.y - 4, r.width, "KEY");

      Shape outer = g.getClip();
      g.clipRect(r.x, r.y, r.width, r.height);
      g.drawImage(key, r.x, r.y - this.legendOffset, null);
      g.setClip(outer);

      /* On the heading's own line: below the panel is the control strip, and a
         hint printed over the buttons reads as part of them. */
      if (key.getHeight() > r.height) {
         Skin.textRight(g, "drag to scroll", r.x + r.width, r.y - 4, Skin.FONT_SMALL, Skin.TEXT_DIM);
      }
   }

   /*
    * Where the six buttons are.
    *
    * Called from both passes rather than laid out while drawing: a rectangle
    * that only exists once a frame has been rendered is a rectangle the input
    * pass cannot hit-test on the frame it matters, and the bug that produces
    * is a button that ignores its first click.
    */
   private void layout() {
      int w = this.viewW();
      int y = this.viewH() + (BAR_H - BTN_H) / 2;
      int x = 5;

      this.btnOut.setBounds(x, y, BTN_W, BTN_H);
      x += BTN_W + 4;
      this.btnIn.setBounds(x, y, BTN_W, BTN_H);
      x += BTN_W + 4;
      this.btnFit.setBounds(x, y, 34, BTN_H);
      x += 38;
      this.btnMe.setBounds(x, y, 34, BTN_H);
      x += 38;
      this.btnKey.setBounds(x, y, 38, BTN_H);
      this.btnClose.setBounds(w - 48, y, 43, BTN_H);
   }

   private void drawBar(Graphics2D g, int mouseX, int mouseY) {
      int w = this.viewW();
      int top = this.viewH();

      g.setColor(Skin.colour(Skin.STONE_DARK));
      g.fillRect(0, top, w, BAR_H);
      g.setColor(Skin.colour(Skin.STONE));
      g.drawLine(0, top, w, top);

      this.layout();

      boolean ready = this.state == READY;
      this.bar(g, this.btnOut, "-", mouseX, mouseY, ready && this.zoom > this.minZoom() + 1.0E-6);
      this.bar(g, this.btnIn, "+", mouseX, mouseY, ready && this.zoom < ZOOM_MAX - 1.0E-6);
      this.bar(g, this.btnFit, "Fit", mouseX, mouseY, ready);
      this.bar(g, this.btnMe, "Me", mouseX, mouseY, ready && this.onMap(this.playerTile()));
      this.bar(g, this.btnKey, "Key", mouseX, mouseY, this.legend != null);
      this.bar(g, this.btnClose, "Close", mouseX, mouseY, true);

      int x = this.btnKey.x + this.btnKey.width;

      /*
       * The readout. Underground it says so instead of leaving the player
       * wondering why there is no marker -- the map is not broken, they are
       * simply not on it.
       */
      int[] tile = this.playerTile();
      String note;
      if (tile == null || !ready) {
         /* Nothing to say about a map that is not up yet -- and in particular
            not "off the edge of this map", which onMap() reports for a player
            standing in Falador purely because there is no image to be on. */
         note = "";
      } else if (!this.overworld(tile)) {
         note = "Underground -- not on this map";
      } else if (!this.onMap(tile)) {
         note = "Off the edge of this map";
      } else {
         note = "(" + tile[0] + ", " + tile[1] + ")";
      }

      int noteRight = w - 52;
      int noteLeft = x + 6;
      if (note.length() > 0 && noteRight - noteLeft > 40) {
         Skin.textRight(
            g,
            Skin.fit(g, note, Skin.FONT_SMALL, noteRight - noteLeft),
            noteRight,
            this.btnClose.y + BTN_H - 5,
            Skin.FONT_SMALL,
            this.onMap(tile) ? Skin.TEXT : Skin.TEXT_DIM);
      }
   }

   private void bar(Graphics2D g, Rectangle r, String label, int mouseX, int mouseY, boolean enabled) {
      int stateNow;
      if (!enabled) {
         stateNow = Skin.DISABLED;
      } else if (r.contains(mouseX, mouseY)) {
         stateNow = Skin.HOVER;
      } else if ("Key".equals(label) && this.showLegend) {
         stateNow = Skin.ACTIVE;
      } else {
         stateNow = Skin.NORMAL;
      }

      Skin.button(g, r.x, r.y, r.width, r.height, label, stateNow);
   }
}
