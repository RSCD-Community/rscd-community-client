package org.rscdaemon.client.model;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class Sector {
   public static final short WIDTH = 48;
   public static final short HEIGHT = 48;
   private Tile[] tiles = new Tile[2304];

   public Sector() {
      for (int i = 0; i < this.tiles.length; i++) {
         this.tiles[i] = new Tile();
      }
   }

   public void setTile(int x, int y, Tile t) {
      this.setTile(x * 48 + y, t);
   }

   public void setTile(int i, Tile t) {
      this.tiles[i] = t;
   }

   public Tile getTile(int x, int y) {
      return this.getTile(x * 48 + y);
   }

   public Tile getTile(int i) {
      return this.tiles[i];
   }

   public ByteBuffer pack() throws IOException {
      ByteBuffer out = ByteBuffer.allocate(10 * this.tiles.length);

      for (int i = 0; i < this.tiles.length; i++) {
         out.put(this.tiles[i].pack());
      }

      ((Buffer)out).flip();
      return out;
   }

   public static Sector unpack(ByteBuffer in) throws IOException {
      int length = 2304;
      if (in.remaining() < 10 * length) {
         throw new IOException("Provided buffer too short");
      } else {
         Sector sector = new Sector();

         for (int i = 0; i < length; i++) {
            sector.setTile(i, Tile.unpack(in));
         }

         return sector;
      }
   }
}
