package org.rscdaemon.client.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import org.rscdaemon.client.util.PersistenceManager;

public class Sprite {
   private static final int TRANSPARENT = Color.BLACK.getRGB();
   private int[] pixels;
   private int width;
   private int height;
   private String packageName = "unknown";
   private int id = -1;
   private boolean requiresShift;
   private int xShift = 0;
   private int yShift = 0;
   private int something1 = 0;
   private int something2 = 0;

   public Sprite() {
      this.pixels = new int[0];
      this.width = 0;
      this.height = 0;
   }

   public Sprite(int[] pixels, int width, int height) {
      this.pixels = pixels;
      this.width = width;
      this.height = height;
   }

   public void setSomething(int something1, int something2) {
      this.something1 = something1;
      this.something2 = something2;
   }

   public int getSomething1() {
      return this.something1;
   }

   public int getSomething2() {
      return this.something2;
   }

   public void setName(int id, String packageName) {
      this.id = id;
      this.packageName = packageName;
   }

   public int getID() {
      return this.id;
   }

   public String getPackageName() {
      return this.packageName;
   }

   public void setShift(int xShift, int yShift) {
      this.xShift = xShift;
      this.yShift = yShift;
   }

   public void setRequiresShift(boolean requiresShift) {
      this.requiresShift = requiresShift;
   }

   public boolean requiresShift() {
      return this.requiresShift;
   }

   public int getXShift() {
      return this.xShift;
   }

   public int getYShift() {
      return this.yShift;
   }

   public int[] getPixels() {
      return this.pixels;
   }

   public int getPixel(int i) {
      return this.pixels[i];
   }

   public void setPixel(int i, int val) {
      this.pixels[i] = val;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   @Override
   public String toString() {
      return "id = " + this.id + "; package = " + this.packageName;
   }

   /*
    * serializeTo(File) and deserializeFrom(File) were here: a pair of
    * sprite-editor helpers that round-tripped a Sprite through XStream. Neither
    * had a caller anywhere in the client, and serializeTo was the only user of
    * PersistenceManager.write() -- so keeping them would have meant writing an
    * XML serialiser to support a code path nothing reaches. The editor they
    * belonged to is not in this tree.
    */

   public BufferedImage toImage() {
      BufferedImage img = new BufferedImage(this.width, this.height, 1);

      for (int y = 0; y < this.height; y++) {
         for (int x = 0; x < this.width; x++) {
            img.setRGB(x, y, this.pixels[x + y * this.width]);
         }
      }

      return img;
   }

   public static Sprite fromImage(BufferedImage img) {
      int[] pixels = new int[img.getWidth() * img.getHeight()];

      for (int y = 0; y < img.getHeight(); y++) {
         for (int x = 0; x < img.getWidth(); x++) {
            int rgb = img.getRGB(x, y);
            if (rgb == TRANSPARENT) {
               rgb = 0;
            }

            pixels[x + y * img.getWidth()] = rgb;
         }
      }

      return new Sprite(pixels, img.getWidth(), img.getHeight());
   }

   public ByteBuffer pack() throws IOException {
      ByteBuffer out = ByteBuffer.allocate(25 + this.pixels.length * 4);
      out.putInt(this.width);
      out.putInt(this.height);
      out.put((byte)(this.requiresShift ? 1 : 0));
      out.putInt(this.xShift);
      out.putInt(this.yShift);
      out.putInt(this.something1);
      out.putInt(this.something2);

      for (int c = 0; c < this.pixels.length; c++) {
         out.putInt(this.pixels[c]);
      }

      ((Buffer)out).flip();
      return out;
   }

   public static Sprite unpack(ByteBuffer in) throws IOException {
      if (in.remaining() < 25) {
         throw new IOException("Provided buffer too short - Headers missing");
      } else {
         int width = in.getInt();
         int height = in.getInt();
         boolean requiresShift = in.get() == 1;
         int xShift = in.getInt();
         int yShift = in.getInt();
         int something1 = in.getInt();
         int something2 = in.getInt();
         int[] pixels = new int[width * height];
         if (in.remaining() < pixels.length * 4) {
            throw new IOException("Provided buffer too short - Pixels missing");
         } else {
            for (int c = 0; c < pixels.length; c++) {
               pixels[c] = in.getInt();
            }

            Sprite sprite = new Sprite(pixels, width, height);
            sprite.setRequiresShift(requiresShift);
            sprite.setShift(xShift, yShift);
            sprite.setSomething(something1, something2);
            return sprite;
         }
      }
   }
}
