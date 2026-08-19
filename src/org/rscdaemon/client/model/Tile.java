package org.rscdaemon.client.model;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class Tile {
   public byte groundElevation = 0;
   public byte groundTexture = 0;
   public byte roofTexture = 0;
   public byte horizontalWall = 0;
   public byte verticalWall = 0;
   public int diagonalWalls = 0;
   public byte groundOverlay = 0;

   public ByteBuffer pack() throws IOException {
      ByteBuffer out = ByteBuffer.allocate(10);
      out.put(this.groundElevation);
      out.put(this.groundTexture);
      out.put(this.groundOverlay);
      out.put(this.roofTexture);
      out.put(this.horizontalWall);
      out.put(this.verticalWall);
      out.putInt(this.diagonalWalls);
      ((Buffer)out).flip();
      return out;
   }

   public static Tile unpack(ByteBuffer in) throws IOException {
      if (in.remaining() < 10) {
         throw new IOException("Provided buffer too short");
      } else {
         Tile tile = new Tile();
         tile.groundElevation = in.get();
         tile.groundTexture = in.get();
         tile.groundOverlay = in.get();
         tile.roofTexture = in.get();
         tile.horizontalWall = in.get();
         tile.verticalWall = in.get();
         tile.diagonalWalls = in.getInt();
         return tile;
      }
   }
}
