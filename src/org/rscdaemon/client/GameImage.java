package org.rscdaemon.client;
import org.rscdaemon.client.util.Config;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.PixelGrabber;
import java.io.BufferedInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import org.rscdaemon.client.util.Assets;
import org.rscdaemon.client.util.MemoryArchive;
import org.rscdaemon.client.model.Sprite;
import org.rscdaemon.client.util.DataConversions;

public class GameImage implements ImageProducer, ImageObserver {
   public Sprite[] sprites;
   private MemoryArchive spriteArchive;
   public int menuDefaultWidth;
   public int menuDefaultHeight;
   public int imageWidthUnused;
   public int imageHeightUnused;
   ColorModel colourModel;
   public int[] imagePixelArray;
   ImageConsumer imageConsumer;
   public Image image;
   private int imageY;
   private int imageHeight;
   private int imageX;
   private int imageWidth;
   public boolean f1Toggle = false;
   // One entry per font, built by loadFont: 95 glyphs of 9 header bytes each
   // (855 bytes), then the 8-bit glyph bitmaps. Header layout per glyph:
   // [0..2] bitmap offset packed base-128, [3] bitmap width, [4] bitmap
   // height, [5] x draw offset, [6] y offset up from the baseline,
   // [7] advance width, [8] font line height.
   static byte[][] fontData = new byte[50][];
   static int[] charIndexes = new int[256];
   public boolean drawStringShadows = false;
   // sin scaled by 32768 in [0..255], cos in [256..511]; 256 steps per turn.
   int[] sinCosCache;
   // Per-scanline edges for the rotated minimap sprite raster: screen x of
   // the left and right edge (8-bit fraction) and the sprite u,v at each.
   int[] scanlineStartX;
   int[] scanlineEndX;
   int[] scanlineStartU;
   int[] scanlineEndU;
   int[] scanlineStartV;
   int[] scanlineEndV;
   public static int spriteRotationMatchCount;
   public static int spriteRotationMismatchCount;
   public static int lastSpriteRotation;
   // Set while a font loads if any glyph pixel came back grey, i.e. the AWT
   // font rendered antialiased. Those fonts are drawn alpha-blended and get
   // no drop shadow.
   private static boolean[] fontAntialiased = new boolean[12];
   private static int fontDataOffset;
   private static byte[] fontDataBuffer = new byte[100000];
   public static int anInt352; // never read or written anywhere

   public GameImage(int width, int height, int k, Component component) {
      this.imageHeight = height;
      this.imageWidth = width;
      this.imageWidthUnused = this.menuDefaultWidth = width;
      this.imageHeightUnused = this.menuDefaultHeight = height;
      this.imagePixelArray = new int[width * height];

      for (int i1 = 0; i1 < this.imagePixelArray.length; i1++) {
         this.imagePixelArray[i1] = 0;
      }

      this.sprites = new Sprite[k];
      if (width > 1 && height > 1 && component != null) {
         this.colourModel = new DirectColorModel(32, 16711680, 65280, 255);
         this.image = component.createImage(this);
         this.completePixels();
         component.prepareImage(this.image, component);
         this.completePixels();
         component.prepareImage(this.image, component);
         this.completePixels();
         component.prepareImage(this.image, component);
      }

      try {
         // Was a ZipFile over Sprites.xml.data on disk. The archive
         // now lives in memory and stays packed -- 1 MB here against the 15 MB
         // its 1701 entries take unpacked.
         this.spriteArchive = new MemoryArchive(Assets.get("Sprites.xml.data"));
      } catch (Exception var6) {
         var6.printStackTrace();
         System.exit(1);
      }
   }

   /*
    * Size the framebuffer again, in place. Everything the constructor sizes
    * gets sized again -- the pixel array, both width/height pairs, and the AWT
    * Image, which cannot be kept: its consumer captured the old dimensions
    * when createImage() attached it, so a new Image is made over this producer
    * and the constructor's prepare dance is repeated to attach a fresh one.
    * Sprites, fonts and the archive never depended on the screen size and are
    * untouched. Camera keeps its own pointer to the pixel array; setCameraSize
    * re-reads it, so callers resize the camera after this.
    */
   public void resize(int width, int height, Component component) {
      this.imageWidth = width;
      this.imageHeight = height;
      this.imageX = 0;
      this.imageY = 0;
      this.imageWidthUnused = this.menuDefaultWidth = width;
      this.imageHeightUnused = this.menuDefaultHeight = height;
      this.imagePixelArray = new int[width * height];
      if (width > 1 && height > 1 && component != null) {
         if (this.image != null) {
            this.image.flush();
         }

         this.imageConsumer = null;
         this.image = component.createImage(this);
         this.completePixels();
         component.prepareImage(this.image, component);
         this.completePixels();
         component.prepareImage(this.image, component);
         this.completePixels();
         component.prepareImage(this.image, component);
      }
   }

   public boolean loadSprite(int id, String packageName) {
      try {
         byte[] entry = this.spriteArchive.get(String.valueOf(id));
         if (entry == null) {
            System.err.println("Missing sprite: " + id);
            return false;
         } else {
            this.sprites[id] = Sprite.unpack(ByteBuffer.wrap(entry));
            return true;
         }
      } catch (Exception var5) {
         var5.printStackTrace();
         return false;
      }
   }

   @Override
   public synchronized void addConsumer(ImageConsumer imageconsumer) {
      this.imageConsumer = imageconsumer;
      imageconsumer.setDimensions(this.menuDefaultWidth, this.menuDefaultHeight);
      imageconsumer.setProperties(null);
      imageconsumer.setColorModel(this.colourModel);
      imageconsumer.setHints(14);
   }

   @Override
   public synchronized boolean isConsumer(ImageConsumer imageconsumer) {
      return this.imageConsumer == imageconsumer;
   }

   @Override
   public synchronized void removeConsumer(ImageConsumer imageconsumer) {
      if (this.imageConsumer == imageconsumer) {
         this.imageConsumer = null;
      }
   }

   @Override
   public void startProduction(ImageConsumer imageconsumer) {
      this.addConsumer(imageconsumer);
   }

   @Override
   public void requestTopDownLeftRightResend(ImageConsumer imageconsumer) {
      System.out.println("TDLR");
   }

   public synchronized void completePixels() {
      if (this.imageConsumer != null) {
         this.imageConsumer.setPixels(0, 0, this.menuDefaultWidth, this.menuDefaultHeight, this.colourModel, this.imagePixelArray, 0, this.menuDefaultWidth);
         this.imageConsumer.imageComplete(2);
      }
   }

   public void setDimensions(int x, int y, int width, int height) {
      if (x < 0) {
         x = 0;
      }

      if (y < 0) {
         y = 0;
      }

      if (width > this.menuDefaultWidth) {
         width = this.menuDefaultWidth;
      }

      if (height > this.menuDefaultHeight) {
         height = this.menuDefaultHeight;
      }

      this.imageX = x;
      this.imageY = y;
      this.imageWidth = width;
      this.imageHeight = height;
   }

   public void resetDimensions() {
      this.imageX = 0;
      this.imageY = 0;
      this.imageWidth = this.menuDefaultWidth;
      this.imageHeight = this.menuDefaultHeight;
   }

   public void drawImage(Graphics g, int x, int y) {
      this.completePixels();
      g.drawImage(this.image, x, y, this);
   }

   public void clearScreen() {
      int i = this.menuDefaultWidth * this.menuDefaultHeight;
      if (!this.f1Toggle) {
         for (int j = 0; j < i; j++) {
            this.imagePixelArray[j] = 0;
         }
      } else {
         int k = 0;

         for (int l = -this.menuDefaultHeight; l < 0; l += 2) {
            for (int i1 = -this.menuDefaultWidth; i1 < 0; i1++) {
               this.imagePixelArray[k++] = 0;
            }

            k += this.menuDefaultWidth;
         }
      }
   }

   public void drawCircle(int i, int j, int k, int l, int i1) {
      int j1 = 256 - i1;
      int k1 = (l >> 16 & 0xFF) * i1;
      int l1 = (l >> 8 & 0xFF) * i1;
      int i2 = (l & 0xFF) * i1;
      int i3 = j - k;
      if (i3 < 0) {
         i3 = 0;
      }

      int j3 = j + k;
      if (j3 >= this.menuDefaultHeight) {
         j3 = this.menuDefaultHeight - 1;
      }

      byte byte0 = 1;
      if (this.f1Toggle) {
         byte0 = 2;
         if ((i3 & 1) != 0) {
            i3++;
         }
      }

      for (int k3 = i3; k3 <= j3; k3 += byte0) {
         int l3 = k3 - j;
         int i4 = (int)Math.sqrt((double)(k * k - l3 * l3));
         int j4 = i - i4;
         if (j4 < 0) {
            j4 = 0;
         }

         int k4 = i + i4;
         if (k4 >= this.menuDefaultWidth) {
            k4 = this.menuDefaultWidth - 1;
         }

         int l4 = j4 + k3 * this.menuDefaultWidth;

         for (int i5 = j4; i5 <= k4; i5++) {
            int j2 = (this.imagePixelArray[l4] >> 16 & 0xFF) * j1;
            int k2 = (this.imagePixelArray[l4] >> 8 & 0xFF) * j1;
            int l2 = (this.imagePixelArray[l4] & 0xFF) * j1;
            int j5 = (k1 + j2 >> 8 << 16) + (l1 + k2 >> 8 << 8) + (i2 + l2 >> 8);
            this.imagePixelArray[l4++] = j5;
         }
      }
   }

   public void drawBoxAlpha(int i, int j, int k, int l, int i1, int j1) {
      if (i < this.imageX) {
         k -= this.imageX - i;
         i = this.imageX;
      }

      if (j < this.imageY) {
         l -= this.imageY - j;
         j = this.imageY;
      }

      if (i + k > this.imageWidth) {
         k = this.imageWidth - i;
      }

      if (j + l > this.imageHeight) {
         l = this.imageHeight - j;
      }

      int k1 = 256 - j1;
      int l1 = (i1 >> 16 & 0xFF) * j1;
      int i2 = (i1 >> 8 & 0xFF) * j1;
      int j2 = (i1 & 0xFF) * j1;
      int j3 = this.menuDefaultWidth - k;
      byte byte0 = 1;
      if (this.f1Toggle) {
         byte0 = 2;
         j3 += this.menuDefaultWidth;
         if ((j & 1) != 0) {
            j++;
            l--;
         }
      }

      int k3 = i + j * this.menuDefaultWidth;

      for (int l3 = 0; l3 < l; l3 += byte0) {
         for (int i4 = -k; i4 < 0; i4++) {
            int k2 = (this.imagePixelArray[k3] >> 16 & 0xFF) * k1;
            int l2 = (this.imagePixelArray[k3] >> 8 & 0xFF) * k1;
            int i3 = (this.imagePixelArray[k3] & 0xFF) * k1;
            int j4 = (l1 + k2 >> 8 << 16) + (i2 + l2 >> 8 << 8) + (j2 + i3 >> 8);
            this.imagePixelArray[k3++] = j4;
         }

         k3 += j3;
      }
   }

   public void drawGradientBox(int i, int j, int k, int l, int i1, int j1) {
      if (i < this.imageX) {
         k -= this.imageX - i;
         i = this.imageX;
      }

      if (i + k > this.imageWidth) {
         k = this.imageWidth - i;
      }

      int k1 = j1 >> 16 & 0xFF;
      int l1 = j1 >> 8 & 0xFF;
      int i2 = j1 & 0xFF;
      int j2 = i1 >> 16 & 0xFF;
      int k2 = i1 >> 8 & 0xFF;
      int l2 = i1 & 0xFF;
      int i3 = this.menuDefaultWidth - k;
      byte byte0 = 1;
      if (this.f1Toggle) {
         byte0 = 2;
         i3 += this.menuDefaultWidth;
         if ((j & 1) != 0) {
            j++;
            l--;
         }
      }

      int j3 = i + j * this.menuDefaultWidth;

      for (int k3 = 0; k3 < l; k3 += byte0) {
         if (k3 + j >= this.imageY && k3 + j < this.imageHeight) {
            int l3 = ((k1 * k3 + j2 * (l - k3)) / l << 16) + ((l1 * k3 + k2 * (l - k3)) / l << 8) + (i2 * k3 + l2 * (l - k3)) / l;

            for (int i4 = -k; i4 < 0; i4++) {
               this.imagePixelArray[j3++] = l3;
            }

            j3 += i3;
         } else {
            j3 += this.menuDefaultWidth;
         }
      }
   }

   public void drawBox(int i, int j, int k, int l, int i1) {
      if (i < this.imageX) {
         k -= this.imageX - i;
         i = this.imageX;
      }

      if (j < this.imageY) {
         l -= this.imageY - j;
         j = this.imageY;
      }

      if (i + k > this.imageWidth) {
         k = this.imageWidth - i;
      }

      if (j + l > this.imageHeight) {
         l = this.imageHeight - j;
      }

      int j1 = this.menuDefaultWidth - k;
      byte byte0 = 1;
      if (this.f1Toggle) {
         byte0 = 2;
         j1 += this.menuDefaultWidth;
         if ((j & 1) != 0) {
            j++;
            l--;
         }
      }

      int k1 = i + j * this.menuDefaultWidth;

      for (int l1 = -l; l1 < 0; l1 += byte0) {
         for (int i2 = -k; i2 < 0; i2++) {
            this.imagePixelArray[k1++] = i1;
         }

         k1 += j1;
      }
   }

   public void drawBoxEdge(int x1, int y1, int x2, int y2, int colour) {
      this.drawLineX(x1, y1, x2, colour);
      this.drawLineX(x1, y1 + y2 - 1, x2, colour);
      this.drawLineY(x1, y1, y2, colour);
      this.drawLineY(x1 + x2 - 1, y1, y2, colour);
   }

   public void drawLineX(int x1, int y1, int x2, int colour) {
      if (y1 >= this.imageY && y1 < this.imageHeight) {
         if (x1 < this.imageX) {
            x2 -= this.imageX - x1;
            x1 = this.imageX;
         }

         if (x1 + x2 > this.imageWidth) {
            x2 = this.imageWidth - x1;
         }

         int xPixel = x1 + y1 * this.menuDefaultWidth;

         for (int yPixel = 0; yPixel < x2; yPixel++) {
            this.imagePixelArray[xPixel + yPixel] = colour;
         }
      }
   }

   public void drawLineY(int x1, int y1, int y2, int colour) {
      if (x1 >= this.imageX && x1 < this.imageWidth) {
         if (y1 < this.imageY) {
            y2 -= this.imageY - y1;
            y1 = this.imageY;
         }

         if (y1 + y2 > this.imageWidth) {
            y2 = this.imageHeight - y1;
         }

         int xPixel = x1 + y1 * this.menuDefaultWidth;

         for (int yPixel = 0; yPixel < y2; yPixel++) {
            this.imagePixelArray[xPixel + yPixel * this.menuDefaultWidth] = colour;
         }
      }
   }

   public void setPixelColour(int x, int y, int colour) {
      if (x >= this.imageX && y >= this.imageY && x < this.imageWidth && y < this.imageHeight) {
         this.imagePixelArray[x + y * this.menuDefaultWidth] = colour;
      }
   }

   public void fadePixels() {
      int k = this.menuDefaultWidth * this.menuDefaultHeight;

      for (int j = 0; j < k; j++) {
         int i = this.imagePixelArray[j] & 16777215;
         this.imagePixelArray[j] = (i >>> 1 & 8355711) + (i >>> 2 & 4144959) + (i >>> 3 & 2039583) + (i >>> 4 & 986895);
      }
   }

   public void blurRegion(int i, int j, int k, int l, int i1, int j1) {
      for (int k1 = k; k1 < k + i1; k1++) {
         for (int l1 = l; l1 < l + j1; l1++) {
            int i2 = 0;
            int j2 = 0;
            int k2 = 0;
            int l2 = 0;

            for (int i3 = k1 - i; i3 <= k1 + i; i3++) {
               if (i3 >= 0 && i3 < this.menuDefaultWidth) {
                  for (int j3 = l1 - j; j3 <= l1 + j; j3++) {
                     if (j3 >= 0 && j3 < this.menuDefaultHeight) {
                        int k3 = this.imagePixelArray[i3 + this.menuDefaultWidth * j3];
                        i2 += k3 >> 16 & 0xFF;
                        j2 += k3 >> 8 & 0xFF;
                        k2 += k3 & 0xFF;
                        l2++;
                     }
                  }
               }
            }

            this.imagePixelArray[k1 + this.menuDefaultWidth * l1] = (i2 / l2 << 16) + (j2 / l2 << 8) + k2 / l2;
         }
      }
   }

   public static int convertRGBToLong(int red, int green, int blue) {
      return (red << 16) + (green << 8) + blue;
   }

   public void cleanupSprites() {
      for (int i = 0; i < this.sprites.length; i++) {
         this.sprites[i] = null;
      }
   }

   public void storeSpriteHoriz(int index, int startX, int startY, int width, int height) {
      int[] pixels = new int[width * height];
      int pixel = 0;

      for (int x = startX; x < startX + width; x++) {
         for (int y = startY; y < startY + height; y++) {
            pixels[pixel++] = this.imagePixelArray[x + y * this.menuDefaultWidth];
         }
      }

      Sprite sprite = new Sprite(pixels, width, height);
      sprite.setShift(0, 0);
      sprite.setRequiresShift(false);
      sprite.setSomething(width, height);
      this.sprites[index] = sprite;
   }

   public void storeSpriteVert(int index, int startX, int startY, int width, int height) {
      int[] pixels = new int[width * height];
      int pixel = 0;

      for (int y = startY; y < startY + height; y++) {
         for (int x = startX; x < startX + width; x++) {
            pixels[pixel++] = this.imagePixelArray[x + y * this.menuDefaultWidth];
         }
      }

      Sprite sprite = new Sprite(pixels, width, height);
      sprite.setShift(0, 0);
      sprite.setRequiresShift(false);
      sprite.setSomething(width, height);
      this.sprites[index] = sprite;
   }

   public void drawPicture(int x, int y, int picture) {
      try {
         if (this.sprites[picture].requiresShift()) {
            x += this.sprites[picture].getXShift();
            y += this.sprites[picture].getYShift();
         }

         int l = x + y * this.menuDefaultWidth;
         int i1 = 0;
         int j1 = this.sprites[picture].getHeight();
         int k1 = this.sprites[picture].getWidth();
         int l1 = this.menuDefaultWidth - k1;
         int i2 = 0;
         if (y < this.imageY) {
            int j2 = this.imageY - y;
            j1 -= j2;
            y = this.imageY;
            i1 += j2 * k1;
            l += j2 * this.menuDefaultWidth;
         }

         if (y + j1 >= this.imageHeight) {
            j1 -= y + j1 - this.imageHeight + 1;
         }

         if (x < this.imageX) {
            int k2 = this.imageX - x;
            k1 -= k2;
            x = this.imageX;
            i1 += k2;
            l += k2;
            i2 += k2;
            l1 += k2;
         }

         if (x + k1 >= this.imageWidth) {
            int l2 = x + k1 - this.imageWidth + 1;
            k1 -= l2;
            i2 += l2;
            l1 += l2;
         }

         if (k1 <= 0 || j1 <= 0) {
            return;
         }

         byte byte0 = 1;
         if (this.f1Toggle) {
            byte0 = 2;
            l1 += this.menuDefaultWidth;
            i2 += this.sprites[picture].getWidth();
            if ((y & 1) != 0) {
               l += this.menuDefaultWidth;
               j1--;
            }
         }

         this.plotSprite(this.imagePixelArray, this.sprites[picture].getPixels(), 0, i1, l, k1, j1, l1, i2, byte0);
      } catch (Exception var11) {
         System.err.println("Error drawing: " + picture);
         var11.printStackTrace();
         System.exit(1);
      }
   }

   public void spriteClip1(int i, int j, int k, int l, int i1) {
      try {
         int j1 = this.sprites[i1].getWidth();
         int k1 = this.sprites[i1].getHeight();
         int l1 = 0;
         int i2 = 0;
         int j2 = (j1 << 16) / k;
         int k2 = (k1 << 16) / l;
         if (this.sprites[i1].requiresShift()) {
            int l2 = this.sprites[i1].getSomething1();
            int j3 = this.sprites[i1].getSomething2();
            j2 = (l2 << 16) / k;
            k2 = (j3 << 16) / l;
            i += (this.sprites[i1].getXShift() * k + l2 - 1) / l2;
            j += (this.sprites[i1].getYShift() * l + j3 - 1) / j3;
            if (this.sprites[i1].getXShift() * k % l2 != 0) {
               l1 = (l2 - this.sprites[i1].getXShift() * k % l2 << 16) / k;
            }

            if (this.sprites[i1].getYShift() * l % j3 != 0) {
               i2 = (j3 - this.sprites[i1].getYShift() * l % j3 << 16) / l;
            }

            k = k * (this.sprites[i1].getWidth() - (l1 >> 16)) / l2;
            l = l * (this.sprites[i1].getHeight() - (i2 >> 16)) / j3;
         }

         int i3 = i + j * this.menuDefaultWidth;
         int k3 = this.menuDefaultWidth - k;
         if (j < this.imageY) {
            int l3 = this.imageY - j;
            l -= l3;
            j = 0;
            i3 += l3 * this.menuDefaultWidth;
            i2 += k2 * l3;
         }

         if (j + l >= this.imageHeight) {
            l -= j + l - this.imageHeight + 1;
         }

         if (i < this.imageX) {
            int i4 = this.imageX - i;
            k -= i4;
            i = 0;
            i3 += i4;
            l1 += j2 * i4;
            k3 += i4;
         }

         if (i + k >= this.imageWidth) {
            int j4 = i + k - this.imageWidth + 1;
            k -= j4;
            k3 += j4;
         }

         byte byte0 = 1;
         if (this.f1Toggle) {
            byte0 = 2;
            k3 += this.menuDefaultWidth;
            k2 += k2;
            if ((j & 1) != 0) {
               i3 += this.menuDefaultWidth;
               l--;
            }
         }

         this.plotSale1(this.imagePixelArray, this.sprites[i1].getPixels(), 0, l1, i2, i3, k3, k, l, j2, k2, j1, byte0);
      } catch (Exception var15) {
         System.out.println("error in sprite clipping routine");
      }
   }

   public void drawSpriteAlpha(int i, int j, int k, int l) {
      if (this.sprites[k].requiresShift()) {
         i += this.sprites[k].getXShift();
         j += this.sprites[k].getYShift();
      }

      int i1 = i + j * this.menuDefaultWidth;
      int j1 = 0;
      int k1 = this.sprites[k].getHeight();
      int l1 = this.sprites[k].getWidth();
      int i2 = this.menuDefaultWidth - l1;
      int j2 = 0;
      if (j < this.imageY) {
         int k2 = this.imageY - j;
         k1 -= k2;
         j = this.imageY;
         j1 += k2 * l1;
         i1 += k2 * this.menuDefaultWidth;
      }

      if (j + k1 >= this.imageHeight) {
         k1 -= j + k1 - this.imageHeight + 1;
      }

      if (i < this.imageX) {
         int l2 = this.imageX - i;
         l1 -= l2;
         i = this.imageX;
         j1 += l2;
         i1 += l2;
         j2 += l2;
         i2 += l2;
      }

      if (i + l1 >= this.imageWidth) {
         int i3 = i + l1 - this.imageWidth + 1;
         l1 -= i3;
         j2 += i3;
         i2 += i3;
      }

      if (l1 > 0 && k1 > 0) {
         byte byte0 = 1;
         if (this.f1Toggle) {
            byte0 = 2;
            i2 += this.menuDefaultWidth;
            j2 += this.sprites[k].getWidth();
            if ((j & 1) != 0) {
               i1 += this.menuDefaultWidth;
               k1--;
            }
         }

         this.plotSpriteAlpha(this.imagePixelArray, this.sprites[k].getPixels(), 0, j1, i1, l1, k1, i2, j2, byte0, l);
      }
   }

   public void spriteClip2(int i, int j, int k, int l, int i1, int j1) {
      try {
         int k1 = this.sprites[i1].getWidth();
         int l1 = this.sprites[i1].getHeight();
         int i2 = 0;
         int j2 = 0;
         int k2 = (k1 << 16) / k;
         int l2 = (l1 << 16) / l;
         if (this.sprites[i1].requiresShift()) {
            int i3 = this.sprites[i1].getSomething1();
            int k3 = this.sprites[i1].getSomething2();
            k2 = (i3 << 16) / k;
            l2 = (k3 << 16) / l;
            i += (this.sprites[i1].getXShift() * k + i3 - 1) / i3;
            j += (this.sprites[i1].getYShift() * l + k3 - 1) / k3;
            if (this.sprites[i1].getXShift() * k % i3 != 0) {
               i2 = (i3 - this.sprites[i1].getXShift() * k % i3 << 16) / k;
            }

            if (this.sprites[i1].getYShift() * l % k3 != 0) {
               j2 = (k3 - this.sprites[i1].getYShift() * l % k3 << 16) / l;
            }

            k = k * (this.sprites[i1].getWidth() - (i2 >> 16)) / i3;
            l = l * (this.sprites[i1].getHeight() - (j2 >> 16)) / k3;
         }

         int j3 = i + j * this.menuDefaultWidth;
         int l3 = this.menuDefaultWidth - k;
         if (j < this.imageY) {
            int i4 = this.imageY - j;
            l -= i4;
            j = 0;
            j3 += i4 * this.menuDefaultWidth;
            j2 += l2 * i4;
         }

         if (j + l >= this.imageHeight) {
            l -= j + l - this.imageHeight + 1;
         }

         if (i < this.imageX) {
            int j4 = this.imageX - i;
            k -= j4;
            i = 0;
            j3 += j4;
            i2 += k2 * j4;
            l3 += j4;
         }

         if (i + k >= this.imageWidth) {
            int k4 = i + k - this.imageWidth + 1;
            k -= k4;
            l3 += k4;
         }

         byte byte0 = 1;
         if (this.f1Toggle) {
            byte0 = 2;
            l3 += this.menuDefaultWidth;
            l2 += l2;
            if ((j & 1) != 0) {
               j3 += this.menuDefaultWidth;
               l--;
            }
         }

         this.tranScale(this.imagePixelArray, this.sprites[i1].getPixels(), 0, i2, j2, j3, l3, k, l, k2, l2, k1, byte0, j1);
      } catch (Exception var16) {
         System.out.println("error in sprite clipping routine");
      }
   }

   public void spriteClip3(int i, int j, int k, int l, int i1, int j1) {
      try {
         int k1 = this.sprites[i1].getWidth();
         int l1 = this.sprites[i1].getHeight();
         int i2 = 0;
         int j2 = 0;
         int k2 = (k1 << 16) / k;
         int l2 = (l1 << 16) / l;
         if (this.sprites[i1].requiresShift()) {
            int i3 = this.sprites[i1].getSomething1();
            int k3 = this.sprites[i1].getSomething2();
            k2 = (i3 << 16) / k;
            l2 = (k3 << 16) / l;
            i += (this.sprites[i1].getXShift() * k + i3 - 1) / i3;
            j += (this.sprites[i1].getYShift() * l + k3 - 1) / k3;
            if (this.sprites[i1].getXShift() * k % i3 != 0) {
               i2 = (i3 - this.sprites[i1].getXShift() * k % i3 << 16) / k;
            }

            if (this.sprites[i1].getYShift() * l % k3 != 0) {
               j2 = (k3 - this.sprites[i1].getYShift() * l % k3 << 16) / l;
            }

            k = k * (this.sprites[i1].getWidth() - (i2 >> 16)) / i3;
            l = l * (this.sprites[i1].getHeight() - (j2 >> 16)) / k3;
         }

         int j3 = i + j * this.menuDefaultWidth;
         int l3 = this.menuDefaultWidth - k;
         if (j < this.imageY) {
            int i4 = this.imageY - j;
            l -= i4;
            j = 0;
            j3 += i4 * this.menuDefaultWidth;
            j2 += l2 * i4;
         }

         if (j + l >= this.imageHeight) {
            l -= j + l - this.imageHeight + 1;
         }

         if (i < this.imageX) {
            int j4 = this.imageX - i;
            k -= j4;
            i = 0;
            j3 += j4;
            i2 += k2 * j4;
            l3 += j4;
         }

         if (i + k >= this.imageWidth) {
            int k4 = i + k - this.imageWidth + 1;
            k -= k4;
            l3 += k4;
         }

         byte byte0 = 1;
         if (this.f1Toggle) {
            byte0 = 2;
            l3 += this.menuDefaultWidth;
            l2 += l2;
            if ((j & 1) != 0) {
               j3 += this.menuDefaultWidth;
               l--;
            }
         }

         this.plotScale2(this.imagePixelArray, this.sprites[i1].getPixels(), 0, i2, j2, j3, l3, k, l, k2, l2, k1, byte0, j1);
      } catch (Exception var16) {
         System.out.println("error in sprite clipping routine");
      }
   }

   private void plotSprite(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1) {
      int i2 = -(l >> 2);
      l = -(l & 3);
      int j2 = -i1;

      while (j2 < 0) {
         for (int k2 = i2; k2 < 0; k2++) {
            i = ai1[j++];
            if (i != 0) {
               ai[k++] = i;
            } else {
               k++;
            }

            i = ai1[j++];
            if (i != 0) {
               ai[k++] = i;
            } else {
               k++;
            }

            i = ai1[j++];
            if (i != 0) {
               ai[k++] = i;
            } else {
               k++;
            }

            i = ai1[j++];
            if (i != 0) {
               ai[k++] = i;
            } else {
               k++;
            }
         }

         for (int l2 = l; l2 < 0; l2++) {
            i = ai1[j++];
            if (i != 0) {
               ai[k++] = i;
            } else {
               k++;
            }
         }

         k += j1;
         j += k1;
         j2 += l1;
      }
   }

   private void plotSale1(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2) {
      try {
         int l2 = j;
         int i3 = -k1;

         while (i3 < 0) {
            int j3 = (k >> 16) * j2;

            for (int k3 = -j1; k3 < 0; k3++) {
               i = ai1[(j >> 16) + j3];
               if (i != 0) {
                  ai[l++] = i;
               } else {
                  l++;
               }

               j += l1;
            }

            k += i2;
            j = l2;
            l += i1;
            i3 += k2;
         }
      } catch (Exception var18) {
         System.out.println("error in plot_scale");
      }
   }

   private void plotSpriteAlpha(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2) {
      int j2 = 256 - i2;
      int k2 = -i1;

      while (k2 < 0) {
         for (int l2 = -l; l2 < 0; l2++) {
            i = ai1[j++];
            if (i != 0) {
               int i3 = ai[k];
               ai[k++] = ((i & 16711935) * i2 + (i3 & 16711935) * j2 & -16711936) + ((i & 0xFF00) * i2 + (i3 & 0xFF00) * j2 & 0xFF0000) >> 8;
            } else {
               k++;
            }
         }

         k += j1;
         j += k1;
         k2 += l1;
      }
   }

   private void tranScale(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
      int i3 = 256 - l2;

      try {
         int j3 = j;
         int k3 = -k1;

         while (k3 < 0) {
            int l3 = (k >> 16) * j2;

            for (int i4 = -j1; i4 < 0; i4++) {
               i = ai1[(j >> 16) + l3];
               if (i != 0) {
                  int j4 = ai[l];
                  ai[l++] = ((i & 16711935) * l2 + (j4 & 16711935) * i3 & -16711936) + ((i & 0xFF00) * l2 + (j4 & 0xFF00) * i3 & 0xFF0000) >> 8;
               } else {
                  l++;
               }

               j += l1;
            }

            k += i2;
            j = j3;
            l += i1;
            k3 += k2;
         }
      } catch (Exception var21) {
         System.out.println("error in tran_scale");
      }
   }

   private void plotScale2(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
      int i3 = l2 >> 16 & 0xFF;
      int j3 = l2 >> 8 & 0xFF;
      int k3 = l2 & 0xFF;

      try {
         int l3 = j;
         int i4 = -k1;

         while (i4 < 0) {
            int j4 = (k >> 16) * j2;

            for (int k4 = -j1; k4 < 0; k4++) {
               i = ai1[(j >> 16) + j4];
               if (i != 0) {
                  int l4 = i >> 16 & 0xFF;
                  int i5 = i >> 8 & 0xFF;
                  int j5 = i & 0xFF;
                  if (l4 == i5 && i5 == j5) {
                     ai[l++] = (l4 * i3 >> 8 << 16) + (i5 * j3 >> 8 << 8) + (j5 * k3 >> 8);
                  } else {
                     ai[l++] = i;
                  }
               } else {
                  l++;
               }

               j += l1;
            }

            k += i2;
            j = l3;
            l += i1;
            i4 += k2;
         }
      } catch (Exception var25) {
         System.out.println("error in plot_scale");
      }
   }

   public void drawMinimapSprite(int i, int j, int k, int l, int i1) {
      int j1 = this.menuDefaultWidth;
      int k1 = this.menuDefaultHeight;
      if (this.sinCosCache == null) {
         this.sinCosCache = new int[512];

         for (int l1 = 0; l1 < 256; l1++) {
            this.sinCosCache[l1] = (int)(Math.sin((double)l1 * 0.02454369) * 32768.0);
            this.sinCosCache[l1 + 256] = (int)(Math.cos((double)l1 * 0.02454369) * 32768.0);
         }
      }

      int i2 = -this.sprites[k].getSomething1() / 2;
      int j2 = -this.sprites[k].getSomething2() / 2;
      if (this.sprites[k].requiresShift()) {
         i2 += this.sprites[k].getXShift();
         j2 += this.sprites[k].getYShift();
      }

      int k2 = i2 + this.sprites[k].getWidth();
      int l2 = j2 + this.sprites[k].getHeight();
      l &= 255;
      int i4 = this.sinCosCache[l] * i1;
      int j4 = this.sinCosCache[l + 256] * i1;
      int k4 = i + (j2 * i4 + i2 * j4 >> 22);
      int l4 = j + (j2 * j4 - i2 * i4 >> 22);
      int i5 = i + (j2 * i4 + k2 * j4 >> 22);
      int j5 = j + (j2 * j4 - k2 * i4 >> 22);
      int k5 = i + (l2 * i4 + k2 * j4 >> 22);
      int l5 = j + (l2 * j4 - k2 * i4 >> 22);
      int i6 = i + (l2 * i4 + i2 * j4 >> 22);
      int j6 = j + (l2 * j4 - i2 * i4 >> 22);
      // Jagex's minimap tamper check. The scale-128 draw is the compass; its
      // rotation is remembered, and every scale-192 draw is scored on whether
      // its rotation agrees. mudclient reads and resets both counters each
      // frame to maintain a run-length of "rotations agree" frames.
      if (i1 == 192 && (l & 63) == (lastSpriteRotation & 63)) {
         spriteRotationMatchCount++;
      } else if (i1 == 128) {
         lastSpriteRotation = l;
      } else {
         spriteRotationMismatchCount++;
      }

      int k6 = l4;
      int l6 = l4;
      if (j5 < l4) {
         k6 = j5;
      } else if (j5 > l4) {
         l6 = j5;
      }

      if (l5 < k6) {
         k6 = l5;
      } else if (l5 > l6) {
         l6 = l5;
      }

      if (j6 < k6) {
         k6 = j6;
      } else if (j6 > l6) {
         l6 = j6;
      }

      if (k6 < this.imageY) {
         k6 = this.imageY;
      }

      if (l6 > this.imageHeight) {
         l6 = this.imageHeight;
      }

      if (this.scanlineStartX == null || this.scanlineStartX.length != k1 + 1) {
         this.scanlineStartX = new int[k1 + 1];
         this.scanlineEndX = new int[k1 + 1];
         this.scanlineStartU = new int[k1 + 1];
         this.scanlineEndU = new int[k1 + 1];
         this.scanlineStartV = new int[k1 + 1];
         this.scanlineEndV = new int[k1 + 1];
      }

      for (int i7 = k6; i7 <= l6; i7++) {
         this.scanlineStartX[i7] = 99999999;
         this.scanlineEndX[i7] = -99999999;
      }

      int i8 = 0;
      int k8 = 0;
      int i9 = 0;
      int j9 = this.sprites[k].getWidth();
      int k9 = this.sprites[k].getHeight();
      int var49 = 0;
      int var50 = 0;
      int i3 = j9 - 1;
      int j3 = 0;
      k2 = j9 - 1;
      l2 = k9 - 1;
      int k3 = 0;
      int l3 = k9 - 1;
      if (j6 != l4) {
         i8 = (i6 - k4 << 8) / (j6 - l4);
         i9 = (l3 - var50 << 8) / (j6 - l4);
      }

      int j7;
      int k7;
      int l7;
      int l8;
      if (l4 > j6) {
         l7 = i6 << 8;
         l8 = l3 << 8;
         j7 = j6;
         k7 = l4;
      } else {
         l7 = k4 << 8;
         l8 = var50 << 8;
         j7 = l4;
         k7 = j6;
      }

      if (j7 < 0) {
         l7 -= i8 * j7;
         l8 -= i9 * j7;
         j7 = 0;
      }

      if (k7 > k1 - 1) {
         k7 = k1 - 1;
      }

      for (int l9 = j7; l9 <= k7; l9++) {
         this.scanlineStartX[l9] = this.scanlineEndX[l9] = l7;
         l7 += i8;
         this.scanlineStartU[l9] = this.scanlineEndU[l9] = 0;
         this.scanlineStartV[l9] = this.scanlineEndV[l9] = l8;
         l8 += i9;
      }

      if (j5 != l4) {
         i8 = (i5 - k4 << 8) / (j5 - l4);
         k8 = (i3 - var49 << 8) / (j5 - l4);
      }

      int j8;
      if (l4 > j5) {
         l7 = i5 << 8;
         j8 = i3 << 8;
         j7 = j5;
         k7 = l4;
      } else {
         l7 = k4 << 8;
         j8 = var49 << 8;
         j7 = l4;
         k7 = j5;
      }

      if (j7 < 0) {
         l7 -= i8 * j7;
         j8 -= k8 * j7;
         j7 = 0;
      }

      if (k7 > k1 - 1) {
         k7 = k1 - 1;
      }

      for (int i10 = j7; i10 <= k7; i10++) {
         if (l7 < this.scanlineStartX[i10]) {
            this.scanlineStartX[i10] = l7;
            this.scanlineStartU[i10] = j8;
            this.scanlineStartV[i10] = 0;
         }

         if (l7 > this.scanlineEndX[i10]) {
            this.scanlineEndX[i10] = l7;
            this.scanlineEndU[i10] = j8;
            this.scanlineEndV[i10] = 0;
         }

         l7 += i8;
         j8 += k8;
      }

      if (l5 != j5) {
         i8 = (k5 - i5 << 8) / (l5 - j5);
         i9 = (l2 - j3 << 8) / (l5 - j5);
      }

      if (j5 > l5) {
         l7 = k5 << 8;
         j8 = k2 << 8;
         l8 = l2 << 8;
         j7 = l5;
         k7 = j5;
      } else {
         l7 = i5 << 8;
         j8 = i3 << 8;
         l8 = j3 << 8;
         j7 = j5;
         k7 = l5;
      }

      if (j7 < 0) {
         l7 -= i8 * j7;
         l8 -= i9 * j7;
         j7 = 0;
      }

      if (k7 > k1 - 1) {
         k7 = k1 - 1;
      }

      for (int j10 = j7; j10 <= k7; j10++) {
         if (l7 < this.scanlineStartX[j10]) {
            this.scanlineStartX[j10] = l7;
            this.scanlineStartU[j10] = j8;
            this.scanlineStartV[j10] = l8;
         }

         if (l7 > this.scanlineEndX[j10]) {
            this.scanlineEndX[j10] = l7;
            this.scanlineEndU[j10] = j8;
            this.scanlineEndV[j10] = l8;
         }

         l7 += i8;
         l8 += i9;
      }

      if (j6 != l5) {
         i8 = (i6 - k5 << 8) / (j6 - l5);
         k8 = (k3 - k2 << 8) / (j6 - l5);
      }

      if (l5 > j6) {
         l7 = i6 << 8;
         j8 = k3 << 8;
         l8 = l3 << 8;
         j7 = j6;
         k7 = l5;
      } else {
         l7 = k5 << 8;
         j8 = k2 << 8;
         l8 = l2 << 8;
         j7 = l5;
         k7 = j6;
      }

      if (j7 < 0) {
         l7 -= i8 * j7;
         j8 -= k8 * j7;
         j7 = 0;
      }

      if (k7 > k1 - 1) {
         k7 = k1 - 1;
      }

      for (int k10 = j7; k10 <= k7; k10++) {
         if (l7 < this.scanlineStartX[k10]) {
            this.scanlineStartX[k10] = l7;
            this.scanlineStartU[k10] = j8;
            this.scanlineStartV[k10] = l8;
         }

         if (l7 > this.scanlineEndX[k10]) {
            this.scanlineEndX[k10] = l7;
            this.scanlineEndU[k10] = j8;
            this.scanlineEndV[k10] = l8;
         }

         l7 += i8;
         j8 += k8;
      }

      int l10 = k6 * j1;
      int[] ai = this.sprites[k].getPixels();

      for (int i11 = k6; i11 < l6; i11++) {
         int j11 = this.scanlineStartX[i11] >> 8;
         int k11 = this.scanlineEndX[i11] >> 8;
         if (k11 - j11 <= 0) {
            l10 += j1;
         } else {
            int l11 = this.scanlineStartU[i11] << 9;
            int i12 = ((this.scanlineEndU[i11] << 9) - l11) / (k11 - j11);
            int j12 = this.scanlineStartV[i11] << 9;
            int k12 = ((this.scanlineEndV[i11] << 9) - j12) / (k11 - j11);
            if (j11 < this.imageX) {
               l11 += (this.imageX - j11) * i12;
               j12 += (this.imageX - j11) * k12;
               j11 = this.imageX;
            }

            if (k11 > this.imageWidth) {
               k11 = this.imageWidth;
            }

            if (!this.f1Toggle || (i11 & 1) == 0) {
               if (!this.sprites[k].requiresShift()) {
                  this.plotRotatedScanline(this.imagePixelArray, ai, 0, l10 + j11, l11, j12, i12, k12, j11 - k11, j9);
               } else {
                  this.plotRotatedScanlineTransparent(this.imagePixelArray, ai, 0, l10 + j11, l11, j12, i12, k12, j11 - k11, j9);
               }
            }

            l10 += j1;
         }
      }
   }

   private void plotRotatedScanline(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1) {
      for (int var11 = k1; var11 < 0; var11++) {
         this.imagePixelArray[j++] = ai1[(k >> 17) + (l >> 17) * l1];
         k += i1;
         l += j1;
      }
   }

   private void plotRotatedScanlineTransparent(int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1) {
      for (int i2 = k1; i2 < 0; i2++) {
         i = ai1[(k >> 17) + (l >> 17) * l1];
         if (i != 0) {
            this.imagePixelArray[j++] = i;
         } else {
            j++;
         }

         k += i1;
         l += j1;
      }
   }

   public void drawSceneSprite(int i, int j, int k, int l, int i1, int j1, int k1) {
      this.spriteClip1(i, j, k, l, i1);
   }

   public void spriteClip4(int i, int j, int k, int l, int i1, int overlay, int k1, int l1, boolean flag) {
      try {
         if (overlay == 0) {
            overlay = 16777215;
         }

         if (k1 == 0) {
            k1 = 16777215;
         }

         int i2 = this.sprites[i1].getWidth();
         int j2 = this.sprites[i1].getHeight();
         int k2 = 0;
         int l2 = 0;
         int i3 = l1 << 16;
         int j3 = (i2 << 16) / k;
         int k3 = (j2 << 16) / l;
         int l3 = -(l1 << 16) / l;
         if (this.sprites[i1].requiresShift()) {
            int i4 = this.sprites[i1].getSomething1();
            int k4 = this.sprites[i1].getSomething2();
            j3 = (i4 << 16) / k;
            k3 = (k4 << 16) / l;
            int j5 = this.sprites[i1].getXShift();
            int k5 = this.sprites[i1].getYShift();
            if (flag) {
               j5 = i4 - this.sprites[i1].getWidth() - j5;
            }

            i += (j5 * k + i4 - 1) / i4;
            int l5 = (k5 * l + k4 - 1) / k4;
            j += l5;
            i3 += l5 * l3;
            if (j5 * k % i4 != 0) {
               k2 = (i4 - j5 * k % i4 << 16) / k;
            }

            if (k5 * l % k4 != 0) {
               l2 = (k4 - k5 * l % k4 << 16) / l;
            }

            k = ((this.sprites[i1].getWidth() << 16) - k2 + j3 - 1) / j3;
            l = ((this.sprites[i1].getHeight() << 16) - l2 + k3 - 1) / k3;
         }

         int j4 = j * this.menuDefaultWidth;
         i3 += i << 16;
         if (j < this.imageY) {
            int l4 = this.imageY - j;
            l -= l4;
            j = this.imageY;
            j4 += l4 * this.menuDefaultWidth;
            l2 += k3 * l4;
            i3 += l3 * l4;
         }

         if (j + l >= this.imageHeight) {
            l -= j + l - this.imageHeight + 1;
         }

         int i5 = j4 / this.menuDefaultWidth & 1;
         if (!this.f1Toggle) {
            i5 = 2;
         }

         if (k1 == 16777215) {
            if (!flag) {
               this.spritePlotTransparent(this.imagePixelArray, this.sprites[i1].getPixels(), 0, k2, l2, j4, k, l, j3, k3, i2, overlay, i3, l3, i5);
            } else {
               this.spritePlotTransparent(
                  this.imagePixelArray,
                  this.sprites[i1].getPixels(),
                  0,
                  (this.sprites[i1].getWidth() << 16) - k2 - 1,
                  l2,
                  j4,
                  k,
                  l,
                  -j3,
                  k3,
                  i2,
                  overlay,
                  i3,
                  l3,
                  i5
               );
            }
         } else if (!flag) {
            this.spritePlotTransparent(this.imagePixelArray, this.sprites[i1].getPixels(), 0, k2, l2, j4, k, l, j3, k3, i2, overlay, k1, i3, l3, i5);
         } else {
            this.spritePlotTransparent(
               this.imagePixelArray,
               this.sprites[i1].getPixels(),
               0,
               (this.sprites[i1].getWidth() << 16) - k2 - 1,
               l2,
               j4,
               k,
               l,
               -j3,
               k3,
               i2,
               overlay,
               k1,
               i3,
               l3,
               i5
            );
         }
      } catch (Exception var23) {
         System.out.println("error in sprite clipping routine");
      }
   }

   private void spritePlotTransparent(
      int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int overlay, int k2, int l2, int i3
   ) {
      int i4 = overlay >> 16 & 0xFF;
      int j4 = overlay >> 8 & 0xFF;
      int k4 = overlay & 0xFF;

      try {
         int l4 = j;

         for (int i5 = -j1; i5 < 0; i5++) {
            int j5 = (k >> 16) * i2;
            int k5 = k2 >> 16;
            int l5 = i1;
            if (k5 < this.imageX) {
               int i6 = this.imageX - k5;
               l5 = i1 - i6;
               k5 = this.imageX;
               j += k1 * i6;
            }

            if (k5 + l5 >= this.imageWidth) {
               int j6 = k5 + l5 - this.imageWidth;
               l5 -= j6;
            }

            i3 = 1 - i3;
            if (i3 != 0) {
               for (int k6 = k5; k6 < k5 + l5; k6++) {
                  i = ai1[(j >> 16) + j5];
                  if (i != 0) {
                     int j3 = i >> 16 & 0xFF;
                     int k3 = i >> 8 & 0xFF;
                     int l3 = i & 0xFF;
                     if (j3 == k3 && k3 == l3) {
                        ai[k6 + l] = (j3 * i4 >> 8 << 16) + (k3 * j4 >> 8 << 8) + (l3 * k4 >> 8);
                     } else {
                        ai[k6 + l] = i;
                     }
                  }

                  j += k1;
               }
            }

            k += l1;
            j = l4;
            l += this.menuDefaultWidth;
            k2 += l2;
         }
      } catch (Exception var28) {
         System.out.println("error in transparent sprite plot routine");
      }
   }

   private void spritePlotTransparent(
      int[] ai, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int overlay, int k2, int l2, int i3, int j3
   ) {
      int j4 = overlay >> 16 & 0xFF;
      int k4 = overlay >> 8 & 0xFF;
      int l4 = overlay & 0xFF;
      int i5 = k2 >> 16 & 0xFF;
      int j5 = k2 >> 8 & 0xFF;
      int k5 = k2 & 0xFF;

      try {
         int l5 = j;

         for (int i6 = -j1; i6 < 0; i6++) {
            int j6 = (k >> 16) * i2;
            int k6 = l2 >> 16;
            int l6 = i1;
            if (k6 < this.imageX) {
               int i7 = this.imageX - k6;
               l6 = i1 - i7;
               k6 = this.imageX;
               j += k1 * i7;
            }

            if (k6 + l6 >= this.imageWidth) {
               int j7 = k6 + l6 - this.imageWidth;
               l6 -= j7;
            }

            j3 = 1 - j3;
            if (j3 != 0) {
               for (int k7 = k6; k7 < k6 + l6; k7++) {
                  i = ai1[(j >> 16) + j6];
                  if (i != 0) {
                     int k3 = i >> 16 & 0xFF;
                     int l3 = i >> 8 & 0xFF;
                     int i4 = i & 0xFF;
                     if (k3 == l3 && l3 == i4) {
                        ai[k7 + l] = (k3 * j4 >> 8 << 16) + (l3 * k4 >> 8 << 8) + (i4 * l4 >> 8);
                     } else if (k3 == 255 && l3 == i4) {
                        ai[k7 + l] = (k3 * i5 >> 8 << 16) + (l3 * j5 >> 8 << 8) + (i4 * k5 >> 8);
                     } else {
                        ai[k7 + l] = i;
                     }
                  }

                  j += k1;
               }
            }

            k += l1;
            j = l5;
            l += this.menuDefaultWidth;
            l2 += i3;
         }
      } catch (Exception var32) {
         System.out.println("error in transparent sprite plot routine");
      }
   }

   private void spritePlotTransparent(
      int[] ai, byte[] abyte0, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int overlay, int k2, int l2, int i3
   ) {
      int i4 = overlay >> 16 & 0xFF;
      int j4 = overlay >> 8 & 0xFF;
      int k4 = overlay & 0xFF;

      try {
         int l4 = j;

         for (int i5 = -j1; i5 < 0; i5++) {
            int j5 = (k >> 16) * i2;
            int k5 = k2 >> 16;
            int l5 = i1;
            if (k5 < this.imageX) {
               int i6 = this.imageX - k5;
               l5 = i1 - i6;
               k5 = this.imageX;
               j += k1 * i6;
            }

            if (k5 + l5 >= this.imageWidth) {
               int j6 = k5 + l5 - this.imageWidth;
               l5 -= j6;
            }

            i3 = 1 - i3;
            if (i3 != 0) {
               for (int k6 = k5; k6 < k5 + l5; k6++) {
                  i = abyte0[(j >> 16) + j5] & 255;
                  if (i != 0) {
                     i = ai1[i];
                     int j3 = i >> 16 & 0xFF;
                     int k3 = i >> 8 & 0xFF;
                     int l3 = i & 0xFF;
                     if (j3 == k3 && k3 == l3) {
                        ai[k6 + l] = (j3 * i4 >> 8 << 16) + (k3 * j4 >> 8 << 8) + (l3 * k4 >> 8);
                     } else {
                        ai[k6 + l] = i;
                     }
                  }

                  j += k1;
               }
            }

            k += l1;
            j = l4;
            l += this.menuDefaultWidth;
            k2 += l2;
         }
      } catch (Exception var29) {
         System.out.println("error in transparent sprite plot routine");
      }
   }

   private void spritePlotTransparent(
      int[] ai, byte[] abyte0, int[] ai1, int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2, int overlay, int k2, int l2, int i3, int j3
   ) {
      int j4 = overlay >> 16 & 0xFF;
      int k4 = overlay >> 8 & 0xFF;
      int l4 = overlay & 0xFF;
      int i5 = k2 >> 16 & 0xFF;
      int j5 = k2 >> 8 & 0xFF;
      int k5 = k2 & 0xFF;

      try {
         int l5 = j;

         for (int i6 = -j1; i6 < 0; i6++) {
            int j6 = (k >> 16) * i2;
            int k6 = l2 >> 16;
            int l6 = i1;
            if (k6 < this.imageX) {
               int i7 = this.imageX - k6;
               l6 = i1 - i7;
               k6 = this.imageX;
               j += k1 * i7;
            }

            if (k6 + l6 >= this.imageWidth) {
               int j7 = k6 + l6 - this.imageWidth;
               l6 -= j7;
            }

            j3 = 1 - j3;
            if (j3 != 0) {
               for (int k7 = k6; k7 < k6 + l6; k7++) {
                  i = abyte0[(j >> 16) + j6] & 255;
                  if (i != 0) {
                     i = ai1[i];
                     int k3 = i >> 16 & 0xFF;
                     int l3 = i >> 8 & 0xFF;
                     int i4 = i & 0xFF;
                     if (k3 == l3 && l3 == i4) {
                        ai[k7 + l] = (k3 * j4 >> 8 << 16) + (l3 * k4 >> 8 << 8) + (i4 * l4 >> 8);
                     } else if (k3 == 255 && l3 == i4) {
                        ai[k7 + l] = (k3 * i5 >> 8 << 16) + (l3 * j5 >> 8 << 8) + (i4 * k5 >> 8);
                     } else {
                        ai[k7 + l] = i;
                     }
                  }

                  j += k1;
               }
            }

            k += l1;
            j = l5;
            l += this.menuDefaultWidth;
            l2 += i3;
         }
      } catch (Exception var33) {
         System.out.println("error in transparent sprite plot routine");
      }
   }

   /*
    * Jagex asked the JRE for "Helvetica" and rasterised the glyphs itself, so
    * every panel in this client is laid out against Helvetica's metrics --
    * the options menu's longest line is 193 pixels wide against a 193 pixel
    * panel, which is not an accident.
    *
    * Windows and Mac map Helvetica to Arial and it fits. Linux has neither,
    * so Java silently substitutes DejaVu Sans, which is about 27% wider, and
    * the text runs off the right of the window. Nothing in the layout is
    * wrong; the font underneath it is.
    *
    * So: ask for Helvetica first, exactly as Jagex did, and only if the JRE
    * cannot supply it fall back through the families that are metrically
    * compatible with it. Liberation Sans and Nimbus Sans are the Arial and
    * Helvetica clones respectively and ship with most distributions.
    */
   private static final String[] HELVETICA_FAMILIES = {
      "Helvetica", "Arial", "Liberation Sans", "Nimbus Sans", "FreeSans", "Nimbus Sans L"
   };
   private static String helveticaFamily;

   private static synchronized String helveticaFamily() {
      if (helveticaFamily == null) {
         java.util.HashSet<String> installed = new java.util.HashSet<String>();
         try {
            String[] names = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
               .getAvailableFontFamilyNames();
            for (int i = 0; i < names.length; i++) {
               installed.add(names[i].toLowerCase());
            }
         } catch (Throwable var4) {
            // A headless or broken graphics environment; take Jagex's name and hope.
         }

         for (int i = 0; i < HELVETICA_FAMILIES.length; i++) {
            if (installed.contains(HELVETICA_FAMILIES[i].toLowerCase())) {
               helveticaFamily = HELVETICA_FAMILIES[i];
               break;
            }
         }

         if (helveticaFamily == null) {
            helveticaFamily = "Helvetica";
         }

         if (!"Helvetica".equals(helveticaFamily)) {
            System.out.println("Helvetica is not installed; using " + helveticaFamily + " for its metrics");
         }
      }

      return helveticaFamily;
   }

   public static Font helvetica(int style, int size) {
      return new Font(helveticaFamily(), style, size);
   }

   /*
    * Installs a font slot from a pre-rendered bake instead of rendering it
    * through AWT -- see GameWindow.loadFonts for why that matters off Windows.
    * The payload is the same bytes loadFont would have left in fontData, so
    * every drawString path below is unchanged and cannot tell the difference.
    * This exists as its own method purely so loadFont stays untouched.
    */
   static void setBakedFont(int fontNumber, byte[] payload, boolean antialiased) {
      fontData[fontNumber] = payload;
      fontAntialiased[fontNumber] = antialiased;
   }

   public static void loadFont(String smallName, int fontNumber, GameWindow gameWindow) {
      boolean flag = false;
      boolean addCharWidth = false;
      smallName = smallName.toLowerCase();
      if (smallName.startsWith("helvetica")) {
         smallName = smallName.substring(9);
      }

      if (smallName.startsWith("h")) {
         smallName = smallName.substring(1);
      }

      if (smallName.startsWith("f")) {
         smallName = smallName.substring(1);
         flag = true;
      }

      if (smallName.startsWith("d")) {
         smallName = smallName.substring(1);
         addCharWidth = true;
      }

      if (smallName.endsWith(".jf")) {
         smallName = smallName.substring(0, smallName.length() - 3);
      }

      int style = 0;
      if (smallName.endsWith("b")) {
         style = 1;
         smallName = smallName.substring(0, smallName.length() - 1);
      }

      if (smallName.endsWith("p")) {
         smallName = smallName.substring(0, smallName.length() - 1);
      }

      int size = Integer.parseInt(smallName);
      Font font = helvetica(style, size);
      FontMetrics fontmetrics = gameWindow.getFontMetrics(font);
      String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";
      // Bitmaps start after the 95 glyph headers of 9 bytes each.
      fontDataOffset = 855;

      for (int charSetOffset = 0; charSetOffset < 95; charSetOffset++) {
         drawLetter(font, fontmetrics, charSet.charAt(charSetOffset), charSetOffset, gameWindow, fontNumber, addCharWidth);
      }

      fontData[fontNumber] = new byte[fontDataOffset];

      for (int i1 = 0; i1 < fontDataOffset; i1++) {
         fontData[fontNumber][i1] = fontDataBuffer[i1];
      }

      if (style == 1 && fontAntialiased[fontNumber]) {
         fontAntialiased[fontNumber] = false;
         loadFont("f" + size + "p", fontNumber, gameWindow);
      }

      if (flag && !fontAntialiased[fontNumber]) {
         fontAntialiased[fontNumber] = false;
         loadFont("d" + size + "p", fontNumber, gameWindow);
      }
   }

   public static void drawLetter(
      Font font, FontMetrics fontmetrics, char letter, int charSetOffset, GameWindow gameWindow, int fontNumber, boolean addCharWidth
   ) {
      int charWidth = fontmetrics.charWidth(letter);
      int oldCharWidth = charWidth;
      if (addCharWidth) {
         try {
            if (letter == '/') {
               addCharWidth = false;
            }

            if (letter == 'f'
               || letter == 't'
               || letter == 'w'
               || letter == 'v'
               || letter == 'k'
               || letter == 'x'
               || letter == 'y'
               || letter == 'A'
               || letter == 'V'
               || letter == 'W') {
               charWidth++;
            }
         } catch (Exception var24) {
         }
      }

      int i1 = fontmetrics.getMaxAscent();
      int j1 = fontmetrics.getMaxAscent() + fontmetrics.getMaxDescent();
      int k1 = fontmetrics.getHeight();
      Image image = gameWindow.createImage(charWidth, j1);
      Graphics g = image.getGraphics();
      g.setColor(Color.black);
      g.fillRect(0, 0, charWidth, j1);
      g.setColor(Color.white);
      g.setFont(font);
      g.drawString(String.valueOf(letter), 0, i1);
      if (addCharWidth) {
         g.drawString(String.valueOf(letter), 1, i1);
      }

      int[] ai = new int[charWidth * j1];
      PixelGrabber pixelgrabber = new PixelGrabber(image, 0, 0, charWidth, j1, ai, 0, charWidth);

      try {
         pixelgrabber.grabPixels();
      } catch (InterruptedException var23) {
         return;
      }

      image.flush();
      Image var25 = null;
      int l1 = 0;
      int i2 = 0;
      int j2 = charWidth;
      int k2 = j1;

      label139:
      for (int l2 = 0; l2 < j1; l2++) {
         for (int i3 = 0; i3 < charWidth; i3++) {
            int k3 = ai[i3 + l2 * charWidth];
            if ((k3 & 16777215) != 0) {
               i2 = l2;
               break label139;
            }
         }
      }

      label127:
      for (int j3 = 0; j3 < charWidth; j3++) {
         for (int l3 = 0; l3 < j1; l3++) {
            int j4 = ai[j3 + l3 * charWidth];
            if ((j4 & 16777215) != 0) {
               l1 = j3;
               break label127;
            }
         }
      }

      label115:
      for (int i4 = j1 - 1; i4 >= 0; i4--) {
         for (int k4 = 0; k4 < charWidth; k4++) {
            int i5 = ai[k4 + i4 * charWidth];
            if ((i5 & 16777215) != 0) {
               k2 = i4 + 1;
               break label115;
            }
         }
      }

      label103:
      for (int l4 = charWidth - 1; l4 >= 0; l4--) {
         for (int j5 = 0; j5 < j1; j5++) {
            int l5 = ai[l4 + j5 * charWidth];
            if ((l5 & 16777215) != 0) {
               j2 = l4 + 1;
               break label103;
            }
         }
      }

      fontDataBuffer[charSetOffset * 9] = (byte)(fontDataOffset / 16384);
      fontDataBuffer[charSetOffset * 9 + 1] = (byte)(fontDataOffset / 128 & 127);
      fontDataBuffer[charSetOffset * 9 + 2] = (byte)(fontDataOffset & 127);
      fontDataBuffer[charSetOffset * 9 + 3] = (byte)(j2 - l1);
      fontDataBuffer[charSetOffset * 9 + 4] = (byte)(k2 - i2);
      fontDataBuffer[charSetOffset * 9 + 5] = (byte)l1;
      fontDataBuffer[charSetOffset * 9 + 6] = (byte)(i1 - i2);
      fontDataBuffer[charSetOffset * 9 + 7] = (byte)oldCharWidth;
      fontDataBuffer[charSetOffset * 9 + 8] = (byte)k1;

      for (int k5 = i2; k5 < k2; k5++) {
         for (int i6 = l1; i6 < j2; i6++) {
            int j6 = ai[i6 + k5 * charWidth] & 0xFF;
            if (j6 > 30 && j6 < 230) {
               fontAntialiased[fontNumber] = true;
            }

            fontDataBuffer[fontDataOffset++] = (byte)j6;
         }
      }
   }

   public void drawBoxTextRight(String s, int i, int j, int k, int l) {
      this.drawString(s, i - this.textWidth(s, k), j, k, l);
   }

   public void drawText(String s, int i, int j, int k, int l) {
      this.drawString(s, i - this.textWidth(s, k) / 2, j, k, l);
   }

   public void drawBoxTextColour(String s, int i, int j, int k, int l, int i1) {
      try {
         int j1 = 0;
         byte[] abyte0 = fontData[k];
         int k1 = 0;
         int l1 = 0;

         for (int i2 = 0; i2 < s.length(); i2++) {
            if (s.charAt(i2) == '@' && i2 + 4 < s.length() && s.charAt(i2 + 4) == '@') {
               i2 += 4;
            } else if (s.charAt(i2) == '~' && i2 + 4 < s.length() && s.charAt(i2 + 4) == '~') {
               i2 += 4;
            } else if (s.charAt(i2) == '~' && i2 + 5 < s.length() && s.charAt(i2 + 5) == '~') {
               i2 += 5;
            } else {
               j1 += abyte0[charIndexes[s.charAt(i2)] + 7];
            }

            if (s.charAt(i2) == ' ') {
               l1 = i2;
            }

            if (s.charAt(i2) == '%') {
               l1 = i2;
               j1 = 1000;
            }

            if (j1 > i1) {
               if (l1 <= k1) {
                  l1 = i2;
               }

               this.drawText(s.substring(k1, l1), i, j, k, l);
               j1 = 0;
               k1 = i2 = l1 + 1;
               j += this.messageFontHeight(k);
            }
         }

         if (j1 > 0) {
            this.drawText(s.substring(k1), i, j, k, l);
            return;
         }
      } catch (Exception var12) {
         System.out.println("centrepara: " + var12);
         var12.printStackTrace();
      }
   }

   public void drawString(String string, int x, int y, int k, int colour) {
      try {
         byte[] abyte0 = fontData[k];

         for (int offset = 0; offset < string.length(); offset++) {
            if (string.charAt(offset) == '@' && offset + 4 < string.length() && string.charAt(offset + 4) == '@') {
               if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("red")) {
                  colour = 16711680;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("lre")) {
                  colour = 16748608;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("yel")) {
                  colour = 16776960;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("gre")) {
                  colour = 65280;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("blu")) {
                  colour = 255;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("cya")) {
                  colour = 65535;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("mag")) {
                  colour = 16711935;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("whi")) {
                  colour = 16777215;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("bla")) {
                  colour = 0;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("dre")) {
                  colour = 12582912;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("ora")) {
                  colour = 16748608;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("ran")) {
                  colour = (int)(Math.random() * 1.6777215E7);
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("or1")) {
                  colour = 16756736;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("or2")) {
                  colour = 16740352;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("or3")) {
                  colour = 16723968;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("gr1")) {
                  colour = 12648192;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("gr2")) {
                  colour = 8453888;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("gr3")) {
                  colour = 4259584;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("pnk")) {
                  colour = 16711935;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("bro")) {
                  colour = 6697728;
               } else if (string.substring(offset + 1, offset + 4).equalsIgnoreCase("gry")) {
                  colour = 12303291;
               }

               offset += 4;
            } else if (string.charAt(offset) == '~' && offset + 4 < string.length() && string.charAt(offset + 4) == '~') {
               char c = string.charAt(offset + 1);
               char c1 = string.charAt(offset + 2);
               char c2 = string.charAt(offset + 3);
               if (c >= '0' && c <= '9' && c1 >= '0' && c1 <= '9' && c2 >= '0' && c2 <= '9') {
                  x = Integer.parseInt(string.substring(offset + 1, offset + 4));
               }

               offset += 4;
            } else if (string.charAt(offset) == '~' && offset + 5 < string.length() && string.charAt(offset + 5) == '~') {
               /* Four-digit variant, for panels sitting past x=999 in a
                  resized window. Jagex's client was 512 wide and never
                  needed it. */
               boolean digits = true;
               for (int d = 1; d <= 4; d++) {
                  char cd = string.charAt(offset + d);
                  if (cd < '0' || cd > '9') {
                     digits = false;
                  }
               }

               if (digits) {
                  x = Integer.parseInt(string.substring(offset + 1, offset + 5));
                  offset += 5;
               }
            } else if (string.charAt(offset) == '#'
               && offset + 4 < string.length()
               && string.charAt(offset + 4) == '#'
               && string.substring(offset + 1, offset + 4).equalsIgnoreCase("adm")) {
               this.spriteClip4(x - 12, y - 16, 30, 20, 2339, -256, 0, 0, false);
               x += 14;
               offset += 4;
            } else if (string.charAt(offset) == '#'
               && offset + 4 < string.length()
               && string.charAt(offset + 4) == '#'
               && string.substring(offset + 1, offset + 4).equalsIgnoreCase("mod")) {
               this.spriteClip4(x - 12, y - 16, 30, 20, 2339, -2302756, 0, 0, false);
               x += 14;
               offset += 4;
            } else if (string.charAt(offset) == '#'
               && offset + 4 < string.length()
               && string.charAt(offset + 4) == '#'
               && string.substring(offset + 1, offset + 4).equalsIgnoreCase("pmd")) {
               this.spriteClip4(x - 12, y - 16, 30, 20, 2339, -13382656, 0, 0, false);
               x += 14;
               offset += 4;
            } else {
               int charIndex = charIndexes[string.charAt(offset)];
               if (this.drawStringShadows && !fontAntialiased[k] && colour != 0) {
                  this.drawCharacter(charIndex, x + 1, y, 0, abyte0, fontAntialiased[k]);
               }

               if (this.drawStringShadows && !fontAntialiased[k] && colour != 0) {
                  this.drawCharacter(charIndex, x, y + 1, 0, abyte0, fontAntialiased[k]);
               }

               this.drawCharacter(charIndex, x, y, colour, abyte0, fontAntialiased[k]);
               x += abyte0[charIndex + 7];
            }
         }
      } catch (Exception var11) {
         System.out.println("drawstring: " + var11);
         var11.printStackTrace();
      }
   }

   private void drawCharacter(int i, int j, int k, int l, byte[] abyte0, boolean flag) {
      int i1 = j + abyte0[i + 5];
      int j1 = k - abyte0[i + 6];
      int k1 = abyte0[i + 3];
      int l1 = abyte0[i + 4];
      int i2 = abyte0[i] * 16384 + abyte0[i + 1] * 128 + abyte0[i + 2];
      int j2 = i1 + j1 * this.menuDefaultWidth;
      int k2 = this.menuDefaultWidth - k1;
      int l2 = 0;
      if (j1 < this.imageY) {
         int i3 = this.imageY - j1;
         l1 -= i3;
         j1 = this.imageY;
         i2 += i3 * k1;
         j2 += i3 * this.menuDefaultWidth;
      }

      if (j1 + l1 >= this.imageHeight) {
         l1 -= j1 + l1 - this.imageHeight + 1;
      }

      if (i1 < this.imageX) {
         int j3 = this.imageX - i1;
         k1 -= j3;
         i1 = this.imageX;
         i2 += j3;
         j2 += j3;
         l2 += j3;
         k2 += j3;
      }

      if (i1 + k1 >= this.imageWidth) {
         int k3 = i1 + k1 - this.imageWidth + 1;
         k1 -= k3;
         l2 += k3;
         k2 += k3;
      }

      if (k1 > 0 && l1 > 0) {
         if (flag) {
            this.plotLetterAlpha(this.imagePixelArray, abyte0, l, i2, j2, k1, l1, k2, l2);
            return;
         }

         this.plotLetter(this.imagePixelArray, abyte0, l, i2, j2, k1, l1, k2, l2);
      }
   }

   private void plotLetter(int[] ai, byte[] abyte0, int i, int j, int k, int l, int i1, int j1, int k1) {
      try {
         int l1 = -(l >> 2);
         l = -(l & 3);

         for (int i2 = -i1; i2 < 0; i2++) {
            for (int j2 = l1; j2 < 0; j2++) {
               if (abyte0[j++] != 0) {
                  ai[k++] = i;
               } else {
                  k++;
               }

               if (abyte0[j++] != 0) {
                  ai[k++] = i;
               } else {
                  k++;
               }

               if (abyte0[j++] != 0) {
                  ai[k++] = i;
               } else {
                  k++;
               }

               if (abyte0[j++] != 0) {
                  ai[k++] = i;
               } else {
                  k++;
               }
            }

            for (int k2 = l; k2 < 0; k2++) {
               if (abyte0[j++] != 0) {
                  ai[k++] = i;
               } else {
                  k++;
               }
            }

            k += j1;
            j += k1;
         }
      } catch (Exception var13) {
         System.out.println("plotletter: " + var13);
         var13.printStackTrace();
      }
   }

   private void plotLetterAlpha(int[] ai, byte[] abyte0, int i, int j, int k, int l, int i1, int j1, int k1) {
      for (int l1 = -i1; l1 < 0; l1++) {
         for (int i2 = -l; i2 < 0; i2++) {
            int j2 = abyte0[j++] & 255;
            if (j2 > 30) {
               if (j2 >= 230) {
                  ai[k++] = i;
               } else {
                  int k2 = ai[k];
                  ai[k++] = ((i & 16711935) * j2 + (k2 & 16711935) * (256 - j2) & -16711936) + ((i & 0xFF00) * j2 + (k2 & 0xFF00) * (256 - j2) & 0xFF0000) >> 8;
               }
            } else {
               k++;
            }
         }

         k += j1;
         j += k1;
      }
   }

   public int messageFontHeight(int messageType) {
      if (messageType == 0) {
         return 12;
      } else if (messageType == 1) {
         return 14;
      } else if (messageType == 2) {
         return 14;
      } else if (messageType == 3) {
         return 15;
      } else if (messageType == 4) {
         return 15;
      } else if (messageType == 5) {
         return 19;
      } else if (messageType == 6) {
         return 24;
      } else {
         return messageType == 7 ? 29 : this.fontHeight(messageType);
      }
   }

   public int fontHeight(int i) {
      return i == 0 ? fontData[i][8] - 2 : fontData[i][8] - 1;
   }

   public int textWidth(String s, int i) {
      int j = 0;
      byte[] abyte0 = fontData[i];

      for (int k = 0; k < s.length(); k++) {
         if (s.charAt(k) == '@' && k + 4 < s.length() && s.charAt(k + 4) == '@') {
            k += 4;
         } else if (s.charAt(k) == '~' && k + 4 < s.length() && s.charAt(k + 4) == '~') {
            k += 4;
         } else if (s.charAt(k) == '~' && k + 5 < s.length() && s.charAt(k + 5) == '~') {
            k += 5;
         } else {
            j += abyte0[charIndexes[s.charAt(k)] + 7];
         }
      }

      return j;
   }

   @Override
   public boolean imageUpdate(Image image, int i, int j, int k, int l, int i1) {
      return true;
   }

   static {
      String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";

      for (int i = 0; i < 256; i++) {
         int j = s.indexOf(i);
         if (j == -1) {
            j = 74;
         }

         charIndexes[i] = j * 9;
      }
   }
}
