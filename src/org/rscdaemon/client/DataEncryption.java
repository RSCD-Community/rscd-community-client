package org.rscdaemon.client;

import java.math.BigInteger;
import java.util.zip.CRC32;

public class DataEncryption {
   public byte[] packet;
   public int offset;
   static CRC32 crc = new CRC32();

   public DataEncryption(byte[] abyte0) {
      this.packet = abyte0;
      this.offset = 0;
   }

   public void addByte(int i) {
      this.packet[this.offset++] = (byte)i;
   }

   public void add4ByteInt(int i) {
      this.packet[this.offset++] = (byte)(i >> 24);
      this.packet[this.offset++] = (byte)(i >> 16);
      this.packet[this.offset++] = (byte)(i >> 8);
      this.packet[this.offset++] = (byte)i;
   }

   public void addString(String s) {
      s.getBytes(0, s.length(), this.packet, this.offset);
      this.offset = this.offset + s.length();
      this.packet[this.offset++] = 10;
   }

   public void addBytes(byte[] abyte0, int i, int j) {
      for (int k = i; k < i + j; k++) {
         this.packet[this.offset++] = abyte0[k];
      }
   }

   public int getByte() {
      return this.packet[this.offset++] & 0xFF;
   }

   public int get2ByteInt() {
      this.offset += 2;
      return ((this.packet[this.offset - 2] & 0xFF) << 8) + (this.packet[this.offset - 1] & 0xFF);
   }

   public int get4ByteInt() {
      this.offset += 4;
      return ((this.packet[this.offset - 4] & 0xFF) << 24)
         + ((this.packet[this.offset - 3] & 0xFF) << 16)
         + ((this.packet[this.offset - 2] & 0xFF) << 8)
         + (this.packet[this.offset - 1] & 0xFF);
   }

   public void getBytes(byte[] abyte0, int i, int j) {
      for (int k = i; k < i + j; k++) {
         abyte0[k] = this.packet[this.offset++];
      }
   }

   public void encryptPacketWithKeys(BigInteger biginteger, BigInteger biginteger1) {
      int i = this.offset;
      this.offset = 0;
      byte[] dummyPacket = new byte[i];
      this.getBytes(dummyPacket, 0, i);
      BigInteger biginteger3 = new BigInteger(dummyPacket).modPow(biginteger, biginteger1);
      byte[] encryptedPacket = biginteger3.toByteArray();
      this.offset = 0;
      this.addByte(encryptedPacket.length);
      this.addBytes(encryptedPacket, 0, encryptedPacket.length);
   }
}
