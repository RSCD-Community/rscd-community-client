import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/*
 * Bakes the client's eight fonts into .jf files per FONT-ASSETS.md.
 *
 * This is GameImage.loadFont/drawLetter transliterated onto BufferedImage so
 * it runs headless (Component.createImage needs a displayable window; the
 * pixels are the same). Text antialiasing is explicitly OFF -- that is what
 * the original Windows client got from AWT, and what the glyph crop/threshold
 * logic downstream assumes.
 *
 *    javac FontBaker.java
 *    java FontBaker . "Liberation Sans"          # an installed family
 *    java FontBaker . /path/to/fonts/Arial       # a pair of files on disk
 *
 * The second form takes a PREFIX and reads <prefix>-Regular.ttf for the plain
 * slots and <prefix>-Bold.ttf for the bold ones. It exists because the font a
 * bake should come from is usually not the font the machine happens to have
 * installed, and asking AWT by name gives you whatever fontconfig feels like
 * -- silently. A path cannot be substituted, so a bake taken this way is
 * reproducible on any machine, which is the whole point of baking.
 */
public final class FontBaker {

   // Identical to GameImage.loadFont's charset, index 0..94.
   private static final String CHARSET =
         "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
               + "!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";

   // The eight slots GameWindow.loadFonts fills, in slot order 0..7.
   private static final String[] FONTS =
         {"h11p", "h12b", "h12p", "h13b", "h14b", "h16b", "h20b", "h24b"};

   private FontBaker() {
   }

   public static void main(String[] args) throws Exception {
      File outDir = new File(args.length > 0 ? args[0] : "fonts");
      String source = args.length > 1 ? args[1] : "Helvetica";
      if (!outDir.isDirectory() && !outDir.mkdirs()) {
         throw new java.io.IOException("cannot create " + outDir);
      }

      if (isPath(source)) {
         // Fail loudly and early rather than half-writing a set of slots.
         for (boolean bold : new boolean[] {false, true}) {
            File f = faceFile(source, bold);
            if (!f.isFile()) {
               throw new java.io.FileNotFoundException(f.toString());
            }
         }
         System.out.println("baking from files: " + faceFile(source, false)
               + " / " + faceFile(source, true));
      } else {
         String resolved = new Font(source, Font.PLAIN, 12).getFamily();
         if (!source.equals(resolved)) {
            System.out.println("WARNING: '" + source + "' is not installed; AWT substituted '"
                  + resolved + "'. These bakes carry the substitute's metrics.");
         }
      }

      for (String spec : FONTS) {
         bake(spec, source, new File(outDir, spec + ".jf"));
      }
   }

   /* A source with a separator in it is a file prefix, not a family name. */
   private static boolean isPath(String source) {
      return source.indexOf(File.separatorChar) >= 0 || source.indexOf('/') >= 0;
   }

   private static File faceFile(String prefix, boolean bold) {
      return new File(prefix + (bold ? "-Bold.ttf" : "-Regular.ttf"));
   }

   /*
    * The font for one slot. Asking AWT by family name lets it pick the bold
    * face itself; a file already IS one face, so the style is PLAIN and the
    * boldness comes from having opened the bold file. Both routes were
    * measured against each other over all 95 glyphs at all 8 slots and agree
    * exactly, so this is a choice of where the face comes from, nothing more.
    */
   private static Font fontFor(String source, int style, int size) throws Exception {
      if (!isPath(source)) {
         return new Font(source, style, size);
      }
      Font face = Font.createFont(Font.TRUETYPE_FONT, faceFile(source, style == Font.BOLD));
      return face.deriveFont(Font.PLAIN, (float) size);
   }

   private static void bake(String spec, String source, File out) throws Exception {
      // "h11p" -> plain 11, "h12b" -> bold 12. The f/d variants never bake:
      // they exist to compensate live-AWT antialiasing, which is off here.
      String s = spec.substring(1);
      int style = Font.PLAIN;
      if (s.endsWith("b")) {
         style = Font.BOLD;
         s = s.substring(0, s.length() - 1);
      } else if (s.endsWith("p")) {
         s = s.substring(0, s.length() - 1);
      }
      int size = Integer.parseInt(s);

      Font font = fontFor(source, style, size);
      BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
      Graphics2D pg = probe.createGraphics();
      pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
      FontMetrics metrics = pg.getFontMetrics(font);

      byte[] buffer = new byte[100000];
      int offset = 855;
      boolean antialiased = false;

      for (int i = 0; i < 95; i++) {
         char letter = CHARSET.charAt(i);

         // drawLetter, headless. addCharWidth is always false for h-fonts.
         int charWidth = metrics.charWidth(letter);
         int ascent = metrics.getMaxAscent();
         int boxHeight = metrics.getMaxAscent() + metrics.getMaxDescent();
         int lineHeight = metrics.getHeight();

         BufferedImage image = new BufferedImage(Math.max(1, charWidth), boxHeight,
               BufferedImage.TYPE_INT_RGB);
         Graphics2D g = image.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
               RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
         g.setColor(Color.black);
         g.fillRect(0, 0, charWidth, boxHeight);
         g.setColor(Color.white);
         g.setFont(font);
         g.drawString(String.valueOf(letter), 0, ascent);
         g.dispose();

         int[] pixels = new int[charWidth * boxHeight];
         if (charWidth > 0) {
            image.getRGB(0, 0, charWidth, boxHeight, pixels, 0, charWidth);
         }

         // Crop to the nonzero bounding box, defaults matching the original
         // (an empty glyph crops to its full advance box of zeros).
         int cropLeft = 0, cropTop = 0, cropRight = charWidth, cropBottom = boxHeight;
         outerTop:
         for (int y = 0; y < boxHeight; y++) {
            for (int x = 0; x < charWidth; x++) {
               if ((pixels[x + y * charWidth] & 0xFFFFFF) != 0) {
                  cropTop = y;
                  break outerTop;
               }
            }
         }
         outerLeft:
         for (int x = 0; x < charWidth; x++) {
            for (int y = 0; y < boxHeight; y++) {
               if ((pixels[x + y * charWidth] & 0xFFFFFF) != 0) {
                  cropLeft = x;
                  break outerLeft;
               }
            }
         }
         outerBottom:
         for (int y = boxHeight - 1; y >= 0; y--) {
            for (int x = 0; x < charWidth; x++) {
               if ((pixels[x + y * charWidth] & 0xFFFFFF) != 0) {
                  cropBottom = y + 1;
                  break outerBottom;
               }
            }
         }
         outerRight:
         for (int x = charWidth - 1; x >= 0; x--) {
            for (int y = 0; y < boxHeight; y++) {
               if ((pixels[x + y * charWidth] & 0xFFFFFF) != 0) {
                  cropRight = x + 1;
                  break outerRight;
               }
            }
         }

         buffer[i * 9] = (byte) (offset / 16384);
         buffer[i * 9 + 1] = (byte) (offset / 128 & 127);
         buffer[i * 9 + 2] = (byte) (offset & 127);
         buffer[i * 9 + 3] = (byte) (cropRight - cropLeft);
         buffer[i * 9 + 4] = (byte) (cropBottom - cropTop);
         buffer[i * 9 + 5] = (byte) cropLeft;
         buffer[i * 9 + 6] = (byte) (ascent - cropTop);
         buffer[i * 9 + 7] = (byte) charWidth;
         buffer[i * 9 + 8] = (byte) lineHeight;

         for (int y = cropTop; y < cropBottom; y++) {
            for (int x = cropLeft; x < cropRight; x++) {
               int coverage = pixels[x + y * charWidth] & 0xFF;
               if (coverage > 30 && coverage < 230) {
                  antialiased = true;
               }
               buffer[offset++] = (byte) coverage;
            }
         }
      }

      byte[] name = spec.getBytes(StandardCharsets.US_ASCII);
      try (DataOutputStream file = new DataOutputStream(new FileOutputStream(out))) {
         file.writeBytes("RSCF");
         file.writeByte(1);
         file.writeByte(name.length);
         file.write(name);
         file.writeByte(antialiased ? 1 : 0);
         file.writeByte(0);
         file.writeInt(offset);
         file.write(buffer, 0, offset);
      }
      System.out.println(out + ": " + offset + " payload bytes"
            + (antialiased ? " (antialiased)" : ""));
   }
}
