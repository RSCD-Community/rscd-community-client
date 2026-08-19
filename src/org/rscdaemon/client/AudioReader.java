package org.rscdaemon.client;

import java.io.InputStream;

public class AudioReader extends InputStream {
   private byte[] dataArray;
   private int offset;
   private int length;

   public void stopAudio() {
   }

   public void loadData(byte[] abyte0, int i, int j) {
      this.dataArray = abyte0;
      this.offset = i;
      this.length = i + j;
   }

   @Override
   public int read(byte[] abyte0, int i, int j) {
      for (int k = 0; k < j; k++) {
         if (this.offset < this.length) {
            abyte0[i + k] = this.dataArray[this.offset++];
         } else {
            abyte0[i + k] = -1;
         }
      }

      return j;
   }

   @Override
   public int read() {
      byte[] abyte0 = new byte[1];
      this.read(abyte0, 0, 1);
      return abyte0[0];
   }
}
