package org.rscdaemon.client;

import java.io.IOException;

/**
 * The client half of the wire protocol's framing -- the exact mirror of the
 * server's RSCCodec, including its famous length trick. A frame under 160
 * bytes spends one byte on the length and smuggles the LAST payload byte
 * into the second header slot (see formatPacket, and readPacket undoing it
 * inbound at data[length - 1]); 160 bytes and over pay for a two-byte
 * length (160 + high, low). The 159/160 boundary is exactly where the
 * server-side decoder bugs lived.
 *
 * Outbound packets are built in place in packetData (createPacket writes
 * the opcode, add* append, finalisePacket seals the header) and several
 * frames batch into one socket write. The subclass (StreamClass) supplies
 * the actual I/O; errors surface through the error/errorText flags rather
 * than exceptions into the game loop.
 */
public class PacketConstruction {
   protected int length;
   public int packetReadCount;
   public int maxPacketReadCount;
   public int packetStart;
   private int packetOffset;
   private int skip8Offset;
   public byte[] packetData;
   public static int[] packetCommandCount = new int[256];
   protected String errorText;
   protected int maxPacketLength;
   protected int packetCount;
   public static int[] packetCommandLength = new int[256];
   protected boolean error;

   public void closeStream() {
   }

   public void readInputStream(int length, byte[] abyte0) throws IOException {
      this.readInputStream(length, 0, abyte0);
   }

   public void formatPacket() {
      if (this.skip8Offset != 8) {
         this.packetOffset++;
      }

      int j = this.packetOffset - this.packetStart - 2;
      if (j >= 160) {
         this.packetData[this.packetStart] = (byte)(160 + j / 256);
         this.packetData[this.packetStart + 1] = (byte)(j & 0xFF);
      } else {
         this.packetData[this.packetStart] = (byte)j;
         this.packetOffset--;
         this.packetData[this.packetStart + 1] = this.packetData[this.packetOffset];
      }

      if (this.maxPacketLength <= 10000) {
         int k = this.packetData[this.packetStart + 2] & 255;
         packetCommandCount[k]++;
         packetCommandLength[k] = packetCommandLength[k] + (this.packetOffset - this.packetStart);
      }

      this.packetStart = this.packetOffset;
   }

   public void addByte(int i) {
      this.packetData[this.packetOffset++] = (byte)i;
   }

   public void addTwo4ByteInts(long l) {
      this.add4ByteInt((int)(l >> 32));
      this.add4ByteInt((int)(l & -1L));
   }

   public void add4ByteInt(int i) {
      this.packetData[this.packetOffset++] = (byte)(i >> 24);
      this.packetData[this.packetOffset++] = (byte)(i >> 16);
      this.packetData[this.packetOffset++] = (byte)(i >> 8);
      this.packetData[this.packetOffset++] = (byte)i;
   }

   public boolean containsData() {
      return this.packetStart > 0;
   }

   public void add2ByteInt(int i) {
      this.packetData[this.packetOffset++] = (byte)(i >> 8);
      this.packetData[this.packetOffset++] = (byte)i;
   }

   public int readByte() throws IOException {
      return this.readInputStream();
   }

   public int readPacket(byte[] data) {
      try {
         this.packetReadCount++;
         if (this.maxPacketReadCount > 0 && this.packetReadCount > this.maxPacketReadCount) {
            this.error = true;
            this.errorText = "time-out";
            this.maxPacketReadCount = this.maxPacketReadCount + this.maxPacketReadCount;
            return 0;
         }

         if (this.length == 0 && this.inputStreamAvailable() >= 2) {
            this.length = this.readInputStream();
            if (this.length >= 160) {
               this.length = (this.length - 160) * 256 + this.readInputStream();
            }
         }

         if (this.length > 0 && this.inputStreamAvailable() >= this.length) {
            if (this.length >= 160) {
               this.readInputStream(this.length, data);
            } else {
               data[this.length - 1] = (byte)this.readInputStream();
               if (this.length > 1) {
                  this.readInputStream(this.length - 1, data);
               }
            }

            int readBytes = this.length;
            this.length = 0;
            this.packetReadCount = 0;
            return readBytes;
         }
      } catch (IOException var3) {
         this.error = true;
         this.errorText = var3.getMessage();
      }

      return 0;
   }

   public void readInputStream(int length, int offset, byte[] abyte0) throws IOException {
   }

   public int inputStreamAvailable() throws IOException {
      return 0;
   }

   public void finalisePacket() throws IOException {
      this.formatPacket();
      this.writePacket(0);
   }

   public long read8ByteLong() throws IOException {
      long l = (long)this.read2ByteInt();
      long l1 = (long)this.read2ByteInt();
      long l2 = (long)this.read2ByteInt();
      long l3 = (long)this.read2ByteInt();
      return (l << 48) + (l1 << 32) + (l2 << 16) + l3;
   }

   public int read2ByteInt() throws IOException {
      int i = this.readByte();
      int j = this.readByte();
      return i * 256 + j;
   }

   public void addBytes(byte[] bytes, int offset, int length) {
      for (int k = 0; k < length; k++) {
         this.packetData[this.packetOffset++] = bytes[offset + k];
      }
   }

   public void writePacket(int i) throws IOException {
      if (this.error) {
         this.packetStart = 0;
         this.packetOffset = 3;
         this.error = false;
         throw new IOException(this.errorText);
      } else {
         this.packetCount++;
         if (this.packetCount >= i) {
            if (this.packetStart > 0) {
               this.packetCount = 0;
               this.writeToOutputBuffer(this.packetData, 0, this.packetStart);
            }

            this.packetStart = 0;
            this.packetOffset = 3;
         }
      }
   }

   public void addString(String s) {
      s.getBytes(0, s.length(), this.packetData, this.packetOffset);
      this.packetOffset = this.packetOffset + s.length();
   }

   public void writeToOutputBuffer(byte[] abyte0, int i, int j) throws IOException {
   }

   public void createPacket(int i) {
      if (this.packetStart > this.maxPacketLength * 4 / 5) {
         try {
            this.writePacket(0);
         } catch (IOException var3) {
            this.error = true;
            this.errorText = var3.getMessage();
         }
      }

      if (this.packetData == null) {
         this.packetData = new byte[this.maxPacketLength];
      }

      this.packetData[this.packetStart + 2] = (byte)i;
      this.packetData[this.packetStart + 3] = 0;
      this.packetOffset = this.packetStart + 3;
      this.skip8Offset = 8;
   }

   public int readInputStream() throws IOException {
      return 0;
   }

   public PacketConstruction() {
      this.packetOffset = 3;
      this.skip8Offset = 8;
      this.errorText = "";
      this.maxPacketLength = 5000;
      this.error = false;
   }

}
