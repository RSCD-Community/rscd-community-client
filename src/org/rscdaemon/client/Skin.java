package org.rscdaemon.client;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;

/*
 * The client's own chrome -- one theme, shared by every surface this project
 * added on top of vanilla.
 *
 * There are two of those surfaces: the F2 script menu and the Worlds screen.
 * They were built years and one rewrite apart, so they looked like it -- F2
 * was SkullOrca's 0,170,0 green on black, Worlds was Jagex's grey Menu
 * widgets. Neither belonged to this client. Everything a player sees that
 * Jagex did not draw now comes through here, so there is exactly one place to
 * change how it looks.
 *
 * Vanilla's own panels are deliberately NOT routed through this. The login
 * boxes, the options menu, the bank -- those are Jagex's layout to the pixel
 * (see GameImage.helvetica: the options menu's longest line is 193px against
 * a 193px panel) and restyling them would be a fork of the thing this project
 * exists to preserve.
 *
 * ---- the palette ----
 *
 * Sampled from assets/splash-rscd.png rather than picked by eye, the same way
 * GameWindow's loading BAR_COLOUR is. That artwork is the only place this
 * project's look was ever actually decided, so it is the source of truth: wet
 * green-black stone, carved granite, and warm gold lettering. Numbers came
 * out of percentile samples of the tagline band (gold), the frame (stone),
 * the rune ring (green) and the corner gems (blue).
 *
 * The green survives as an accent -- rune glow, moss, the "online" state --
 * and not as the interface colour it was in the F2 overlay. On the splash it
 * is light coming out of carved stone, never the stone itself.
 *
 * ---- how it draws ----
 *
 * Through a Graphics2D wrapped directly around the client's own pixel array,
 * so antialiased text and gradients land in the same int[] the software
 * renderer writes to. That matters for more than looks: the frame the player
 * sees is composed from that array, and so are F12 screenshots and every
 * frame the movie recorder writes. Drawing onto the component's Graphics
 * instead -- the obvious way -- would be invisible to both.
 *
 * The wrapper is zero-copy (a DataBufferInt over the existing array) and is
 * rebuilt only when the array itself changes, which is a window resize.
 */
final class Skin {

   /* ---- stone ---- */
   /** Behind everything; the splash's cave interior. */
   static final int BG_DEEP = 0x0B0C07;
   /** Panel fill. */
   static final int BG = 0x14150E;
   /** The shadowed face of the carved frame. */
   static final int STONE_DARK = 0x231E17;
   static final int STONE_SHADOW = 0x332C21;
   /** The frame itself. */
   static final int STONE = 0x4F4536;
   /** Granite catching the light -- top-left bevels only. */
   static final int STONE_LIT = 0xC6BBA7;

   /* ---- gold: the lettering ramp off the tagline ---- */
   static final int GOLD_DIM = 0x87723A;
   static final int GOLD = 0xBA9A5F;
   static final int GOLD_HI = 0xDFC893;
   static final int GOLD_MAX = 0xEFE2B4;

   /* ---- accents ---- */
   /** Rune glow. Live/online/running states, sparingly. */
   static final int RUNE = 0xBBE969;
   static final int MOSS = 0x6F7A3A;
   /** The frame's corner gems. Selection. */
   static final int GEM = 0x7FBFD0;
   static final int GEM_HI = 0xD3EBEF;
   /** Torch amber, and the ember red it sits next to: warnings and Stop. */
   static final int AMBER = 0xFBD658;
   static final int EMBER = 0x8C3A1E;
   static final int EMBER_HI = 0xD4552A;
   /** Lettering on an ember face: Stop's glyph, the danger button, the alert box. */
   static final int EMBER_TEXT = 0xF0C4A6;

   /* ---- text ---- */
   static final int TEXT = 0xC9C2AE;
   static final int TEXT_DIM = 0x7C7565;
   static final int TEXT_OFF = 0x4A4539;

   /* ---- type ----
    *
    * Through GameImage.helvetica so these resolve the same way vanilla's
    * rasterised fonts do -- Helvetica, then the metric clones. A box that
    * fits on Windows has to fit on a Linux box with no Helvetica installed.
    */
   static final Font FONT_TITLE = GameImage.helvetica(Font.BOLD, 17);
   static final Font FONT_HEAD = GameImage.helvetica(Font.BOLD, 12);
   static final Font FONT_BODY = GameImage.helvetica(Font.PLAIN, 12);
   static final Font FONT_SMALL = GameImage.helvetica(Font.PLAIN, 11);

   /* Button states. */
   static final int NORMAL = 0;
   static final int HOVER = 1;
   static final int DISABLED = 2;
   static final int DANGER = 3;
   static final int ACTIVE = 4;

   private Skin() {
   }

   /*
    * ---- the Graphics2D bridge ----
    *
    * One cached wrapper per pixel array. Identity-compared, not equals: a
    * resize replaces the array, and that is exactly when the BufferedImage
    * has to be rebuilt.
    */
   private static int[] wrappedArray;
   private static BufferedImage wrappedImage;

   private static synchronized BufferedImage imageOver(int[] pixels, int width, int height) {
      if (wrappedArray != pixels || wrappedImage == null
            || wrappedImage.getWidth() != width || wrappedImage.getHeight() != height) {
         DataBufferInt buffer = new DataBufferInt(pixels, pixels.length);
         WritableRaster raster = Raster.createWritableRaster(
            new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height,
               new int[] { 0xFF0000, 0xFF00, 0xFF }),
            buffer, null);
         wrappedImage = new BufferedImage(new DirectColorModel(24, 0xFF0000, 0xFF00, 0xFF),
            raster, false, null);
         wrappedArray = pixels;
      }

      return wrappedImage;
   }

   /*
    * A Graphics2D over the client's framebuffer. The caller disposes it --
    * every draw() here is one open/dispose pair around a whole surface, not
    * one per widget, because Graphics2D creation is the expensive part and
    * the drawing is not.
    */
   static Graphics2D open(GameImageMiddleMan gg, int width, int height) {
      Graphics2D g = imageOver(gg.imagePixelArray, gg.menuDefaultWidth, gg.menuDefaultHeight)
         .createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
         RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      /* Clip to the game view, not the buffer: the buffer is wider than the
         512x334 view when the side panels are laid out beside it. */
      g.clipRect(0, 0, width, height);
      return g;
   }

   static Color colour(int rgb) {
      return new Color(rgb, false);
   }

   static Color colour(int rgb, int alpha) {
      return new Color((alpha & 0xFF) << 24 | rgb & 0xFFFFFF, true);
   }

   /*
    * ---- surfaces ----
    */

   /**
    * Dims whatever is already on screen. Not a solid fill: the F2 menu has
    * always left the world faintly visible behind it, which is how you watch
    * a script run while reading about it, and the Worlds screen sits over the
    * login backdrop the same way.
    */
   static void scrim(Graphics2D g, int width, int height, int alpha) {
      g.setColor(colour(BG_DEEP, alpha));
      g.fillRect(0, 0, width, height);
   }

   /**
    * A carved stone panel: dark fill, granite frame, a lit top-left bevel and
    * a gold hairline on the inside edge. The frame is the splash's, reduced
    * to what survives at 3 pixels.
    */
   static void panel(Graphics2D g, int x, int y, int w, int h) {
      g.setPaint(new GradientPaint(x, y, colour(BG), x, y + h, colour(BG_DEEP)));
      g.fillRect(x, y, w, h);

      /* Granite band. Drawn as three nested rectangles rather than a stroke
         so the corners stay square -- a mitred stroke rounds them at these
         widths and the frame stops reading as cut stone. */
      g.setColor(colour(STONE_DARK));
      g.drawRect(x, y, w - 1, h - 1);
      g.setColor(colour(STONE));
      g.drawRect(x + 1, y + 1, w - 3, h - 3);
      g.setColor(colour(STONE_SHADOW));
      g.drawRect(x + 2, y + 2, w - 5, h - 5);

      /* Light falls from the top left, as it does on the splash frame. */
      g.setColor(colour(STONE_LIT, 60));
      g.drawLine(x + 1, y + 1, x + w - 3, y + 1);
      g.drawLine(x + 1, y + 1, x + 1, y + h - 3);

      g.setColor(colour(GOLD_DIM, 110));
      g.drawRect(x + 3, y + 3, w - 7, h - 7);
   }

   /** A sunken area inside a panel -- lists, text fields, the log. */
   static void well(Graphics2D g, int x, int y, int w, int h) {
      g.setColor(colour(BG_DEEP, 200));
      g.fillRect(x, y, w, h);
      g.setColor(colour(STONE_SHADOW));
      g.drawRect(x, y, w - 1, h - 1);
      g.setColor(colour(STONE_LIT, 28));
      g.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
   }

   /**
    * The title treatment off the splash tagline: gold letters, widely spaced,
    * between two small flourishes.
    */
   static void title(Graphics2D g, int centreX, int baseline, String text) {
      Font spaced = FONT_TITLE.deriveFont(
         java.util.Collections.singletonMap(java.awt.font.TextAttribute.TRACKING, 0.14));
      g.setFont(spaced);
      int width = g.getFontMetrics().stringWidth(text);
      int x = centreX - width / 2;

      g.setColor(colour(BG_DEEP, 190));
      g.drawString(text, x + 1, baseline + 1);
      g.setColor(colour(GOLD_MAX));
      g.drawString(text, x, baseline);

      flourish(g, x - 18, baseline - 5, true);
      flourish(g, x + width + 18, baseline - 5, false);
   }

   /** The little arrow-and-diamond the tagline is bracketed with. */
   private static void flourish(Graphics2D g, int x, int y, boolean pointRight) {
      int d = pointRight ? 1 : -1;
      g.setColor(colour(GOLD, 190));
      g.fillPolygon(new int[] { x, x - d * 5, x, x + d * 5 }, new int[] { y - 4, y, y + 4, y }, 4);
      g.setColor(colour(GOLD_DIM, 150));
      g.drawLine(x + d * 6, y, x + d * 12, y);
   }

   /** A section heading inside a panel: small, gold, with a rule under it. */
   static void heading(Graphics2D g, int x, int baseline, int width, String text) {
      g.setFont(FONT_HEAD);
      g.setColor(colour(GOLD_HI));
      g.drawString(text, x, baseline);
      g.setColor(colour(GOLD_DIM, 90));
      g.drawLine(x, baseline + 4, x + width, baseline + 4);
   }

   /**
    * A button. Stone face, gold rim and gold lettering; the rim and the face
    * brighten together on hover so the target is obvious without a colour
    * change, and DANGER swaps gold for ember so Stop reads as Stop the way
    * SkullOrca's red one did.
    */
   static void button(Graphics2D g, int x, int y, int w, int h, String label, int state) {
      int rim;
      int text;
      int faceTop;
      int faceBottom;

      switch (state) {
         case HOVER:
            rim = GOLD_HI;
            text = GOLD_MAX;
            faceTop = 0x3A3327;
            faceBottom = 0x211D15;
            break;
         case DISABLED:
            rim = STONE_SHADOW;
            text = TEXT_OFF;
            faceTop = 0x1A180F;
            faceBottom = 0x121009;
            break;
         case DANGER:
            rim = EMBER_HI;
            text = EMBER_TEXT;
            faceTop = 0x3A1B10;
            faceBottom = 0x21100A;
            break;
         case ACTIVE:
            rim = GOLD_HI;
            text = GOLD_MAX;
            faceTop = 0x453B26;
            faceBottom = 0x2B2417;
            break;
         default:
            rim = GOLD_DIM;
            text = GOLD_HI;
            faceTop = 0x2A251A;
            faceBottom = 0x181510;
      }

      g.setPaint(new GradientPaint(x, y, colour(faceTop), x, y + h, colour(faceBottom)));
      g.fillRect(x, y, w, h);
      g.setColor(colour(rim, state == DISABLED ? 255 : 210));
      g.drawRect(x, y, w - 1, h - 1);
      g.setColor(colour(STONE_LIT, state == DISABLED ? 12 : 34));
      g.drawLine(x + 1, y + 1, x + w - 3, y + 1);

      if (label != null && label.length() > 0) {
         g.setFont(FONT_HEAD);
         FontMetrics fm = g.getFontMetrics();
         int tx = x + (w - fm.stringWidth(label)) / 2;
         int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
         g.setColor(colour(BG_DEEP, 170));
         g.drawString(label, tx + 1, ty + 1);
         g.setColor(colour(text));
         g.drawString(label, tx, ty);
      }
   }

   /**
    * A list row's background. Hover is a wash; selection adds the gem-blue
    * edge the splash puts in the frame corners, which is the one colour here
    * that appears nowhere else and so cannot be misread.
    */
   static void row(Graphics2D g, int x, int y, int w, int h, boolean hover, boolean selected) {
      if (selected) {
         g.setPaint(new GradientPaint(x, y, colour(GOLD_DIM, 70), x + w, y, colour(GOLD_DIM, 16)));
         g.fillRect(x, y, w, h);
         g.setColor(colour(GEM, 200));
         g.fillRect(x, y, 2, h);
      } else if (hover) {
         g.setColor(colour(GOLD, 26));
         g.fillRect(x, y, w, h);
      }
   }

   /** A hairline divider. */
   static void rule(Graphics2D g, int x, int y, int w) {
      g.setColor(colour(STONE, 120));
      g.drawLine(x, y, x + w, y);
   }

   /*
    * ---- text ----
    *
    * All of it shadowed. The panels are translucent, so a line can land on
    * anything from black stone to a lit sky in the login backdrop, and one
    * dark pixel behind each glyph is what keeps it readable over both.
    */
   static void text(Graphics2D g, String s, int x, int baseline, Font font, int colour) {
      g.setFont(font);
      g.setColor(colour(BG_DEEP, 160));
      g.drawString(s, x + 1, baseline + 1);
      g.setColor(colour(colour));
      g.drawString(s, x, baseline);
   }

   static void textRight(Graphics2D g, String s, int right, int baseline, Font font, int colour) {
      g.setFont(font);
      text(g, s, right - g.getFontMetrics().stringWidth(s), baseline, font, colour);
   }

   static void textCentre(Graphics2D g, String s, int centreX, int baseline, Font font, int colour) {
      g.setFont(font);
      text(g, s, centreX - g.getFontMetrics().stringWidth(s) / 2, baseline, font, colour);
   }

   static int width(Graphics2D g, String s, Font font) {
      g.setFont(font);
      return g.getFontMetrics().stringWidth(s);
   }

   /**
    * Truncates to fit, with an ellipsis. Server names come off a registry
    * anyone can push to, so no width here is guaranteed by anything.
    */
   static String fit(Graphics2D g, String s, Font font, int maxWidth) {
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();
      if (fm.stringWidth(s) <= maxWidth) {
         return s;
      }

      int end = s.length();
      while (end > 1 && fm.stringWidth(s.substring(0, end) + "...") > maxWidth) {
         end--;
      }

      return s.substring(0, end) + "...";
   }

   /** A scroll arrow, in the gold ramp rather than SkullOrca's green. */
   static void arrow(Graphics2D g, int x, int y, int w, int h, boolean up, boolean hot) {
      int[] xs = { x, x + w / 2, x + w };
      int[] ys = up ? new int[] { y + h, y, y + h } : new int[] { y, y + h, y };
      g.setColor(colour(hot ? GOLD_HI : GOLD_DIM, hot ? 255 : 190));
      g.fillPolygon(xs, ys, 3);
   }

   /**
    * A small filled disc for state -- server online, script running. Rune
    * green when live, ember when not, with a soft halo so it reads as light
    * coming through stone rather than a UI dot.
    */
   static void lamp(Graphics2D g, int cx, int cy, boolean lit) {
      int core = lit ? RUNE : EMBER;
      g.setColor(colour(core, 45));
      g.fillOval(cx - 5, cy - 5, 10, 10);
      g.setColor(colour(core, lit ? 235 : 170));
      g.fillOval(cx - 2, cy - 2, 5, 5);
   }

   /**
    * A proportion bar -- a world's population against its capacity. Fills
    * green through amber to ember as it approaches full, so "nearly full" is
    * visible without reading the numbers.
    */
   static void meter(Graphics2D g, int x, int y, int w, int h, double fraction) {
      double f = fraction < 0.0 ? 0.0 : fraction > 1.0 ? 1.0 : fraction;

      g.setColor(colour(BG_DEEP, 210));
      g.fillRect(x, y, w, h);
      g.setColor(colour(STONE_SHADOW));
      g.drawRect(x, y, w - 1, h - 1);

      int fill = (int)Math.round((w - 2) * f);
      if (fill > 0) {
         g.setColor(colour(f >= 1.0 ? EMBER_HI : f > 0.85 ? AMBER : MOSS, 220));
         g.fillRect(x + 1, y + 1, fill, h - 2);
         g.setColor(colour(f >= 1.0 ? EMBER_HI : f > 0.85 ? AMBER : RUNE, 90));
         g.drawLine(x + 1, y + 1, x + fill, y + 1);
      }
   }

   /**
    * The centred alert box: SkullOrca's showAlertMessage, restyled. Kept
    * centre-screen and kept loud, because the things that raise it are a
    * script dying and a screenshot being written.
    */
   static void alert(Graphics2D g, int centreX, int centreY, String message) {
      g.setFont(FONT_HEAD);
      int w = g.getFontMetrics().stringWidth(message) + 40;
      int h = 34;
      int x = centreX - w / 2;
      int y = centreY - h / 2;

      g.setComposite(AlphaComposite.SrcOver);
      g.setPaint(new GradientPaint(x, y, colour(0x2A1710), x, y + h, colour(0x160B07)));
      g.fillRect(x, y, w, h);
      g.setColor(colour(EMBER_HI, 220));
      g.drawRect(x, y, w - 1, h - 1);
      textCentre(g, message, centreX, y + 22, FONT_HEAD, EMBER_TEXT);
   }
}
