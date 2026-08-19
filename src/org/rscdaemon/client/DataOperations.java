package org.rscdaemon.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DataOperations {
   private static int[] baseLengthArray = new int[]{
      0,
      1,
      3,
      7,
      15,
      31,
      63,
      127,
      255,
      511,
      1023,
      2047,
      4095,
      8191,
      16383,
      32767,
      65535,
      131071,
      262143,
      524287,
      1048575,
      2097151,
      4194303,
      8388607,
      16777215,
      33554431,
      67108863,
      134217727,
      268435455,
      536870911,
      1073741823,
      Integer.MAX_VALUE,
      -1
   };

   public static InputStream streamFromPath(String path) throws IOException {
      return new BufferedInputStream(new FileInputStream(path));
   }

   public static void readFromPath(String path, byte[] abyte0, int length) throws IOException {
      InputStream inputstream = streamFromPath(path);
      DataInputStream datainputstream = new DataInputStream(inputstream);

      try {
         datainputstream.readFully(abyte0, 0, length);
      } catch (EOFException var6) {
      }

      datainputstream.close();
   }

   public static int getUnsignedByte(byte byte0) {
      return byte0 & 0xFF;
   }

   public static int getUnsigned2Bytes(byte[] abyte0, int i) {
      return ((abyte0[i] & 0xFF) << 8) + (abyte0[i + 1] & 0xFF);
   }

   public static int getUnsigned4Bytes(byte[] abyte0, int i) {
      return ((abyte0[i] & 0xFF) << 24) + ((abyte0[i + 1] & 0xFF) << 16) + ((abyte0[i + 2] & 0xFF) << 8) + (abyte0[i + 3] & 0xFF);
   }

   public static long getUnsigned8Bytes(byte[] abyte0, int i) {
      return (((long)getUnsigned4Bytes(abyte0, i) & 4294967295L) << 32) + ((long)getUnsigned4Bytes(abyte0, i + 4) & 4294967295L);
   }

   public static int readInt(byte[] abyte0, int i) {
      return (abyte0[i] & 0xFF) << 24 | (abyte0[i + 1] & 0xFF) << 16 | (abyte0[i + 2] & 0xFF) << 8 | abyte0[i + 3] & 0xFF;
   }

   public static int getSigned2Bytes(byte[] abyte0, int i) {
      int j = getUnsignedByte(abyte0[i]) * 256 + getUnsignedByte(abyte0[i + 1]);
      if (j > 32767) {
         j -= 65536;
      }

      return j;
   }

   public static int getSigned4Bytes(byte[] abyte0, int i) {
      return (abyte0[i] & 0xFF) < 128
         ? abyte0[i]
         : ((abyte0[i] & 0xFF) - 128 << 24) + ((abyte0[i + 1] & 0xFF) << 16) + ((abyte0[i + 2] & 0xFF) << 8) + (abyte0[i + 3] & 0xFF);
   }

   public static int getIntFromByteArray(byte[] byteArray, int offset, int length) {
      int bitOffset = offset >> 3;
      int bitMod = 8 - (offset & 7);

      int i1;
      for (i1 = 0; length > bitMod; bitMod = 8) {
         i1 += (byteArray[bitOffset++] & baseLengthArray[bitMod]) << length - bitMod;
         length -= bitMod;
      }

      if (length == bitMod) {
         i1 += byteArray[bitOffset] & baseLengthArray[bitMod];
      } else {
         i1 += byteArray[bitOffset] >> bitMod - length & baseLengthArray[length];
      }

      return i1;
   }

   /**
    * Pad to a fixed width, folding anything that is not a letter or a digit
    * into an underscore.
    *
    * This is Jagex's own login formatter (_reference/rsclassic-src/b.java:383)
    * and it is still exactly right for a *username*: the name is looked up by
    * its base-37 hash, and DataConversions.usernameToHash on the server folds
    * every non-alphanumeric to the same symbol anyway, so the substitution
    * changes nothing that is ever compared.
    *
    * It is not right for a password -- see padCharacters. Passwords used to go
    * through here too, which quietly turned every piece of punctuation in one
    * into an underscore. Since a password is only ever compared as a digest,
    * that is not a weaker password, it is a *different* one, and nothing in the
    * client or on the site ever said so.
    */
   public static String addCharacters(String s, int i) {
      String s1 = "";

      for (int j = 0; j < i; j++) {
         if (j >= s.length()) {
            s1 = s1 + " ";
         } else {
            char c = s.charAt(j);
            if (c >= 'a' && c <= 'z') {
               s1 = s1 + c;
            } else if (c >= 'A' && c <= 'Z') {
               s1 = s1 + c;
            } else if (c >= '0' && c <= '9') {
               s1 = s1 + c;
            } else {
               s1 = s1 + '_';
            }
         }
      }

      return s1;
   }

   /**
    * Pad to a fixed width and change nothing else.
    *
    * What a password needs. The login block is a fixed-width record, so the
    * padding has to happen, but every character the login screen let the player
    * type has to survive it or the digest the server checks is not the digest
    * of the password they chose.
    *
    * Truncates at i for the same reason addCharacters does: the field is that
    * wide and a longer string would run past it into the next one.
    */
   public static String padCharacters(String s, int i) {
      StringBuilder padded = new StringBuilder(i);

      for (int j = 0; j < i; j++) {
         padded.append(j >= s.length() ? ' ' : s.charAt(j));
      }

      return padded.toString();
   }

   public static long stringLength12ToLong(String s) {
      String s1 = "";

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c >= 'a' && c <= 'z') {
            s1 = s1 + c;
         } else if (c >= 'A' && c <= 'Z') {
            s1 = s1 + (char)(c + 'a' - 65);
         } else if (c >= '0' && c <= '9') {
            s1 = s1 + c;
         } else {
            s1 = s1 + ' ';
         }
      }

      s1 = s1.trim();
      if (s1.length() > 12) {
         s1 = s1.substring(0, 12);
      }

      long l = 0L;

      for (int j = 0; j < s1.length(); j++) {
         char c1 = s1.charAt(j);
         l *= 37L;
         if (c1 >= 'a' && c1 <= 'z') {
            l += (long)(1 + c1 - 97);
         } else if (c1 >= '0' && c1 <= '9') {
            l += (long)(27 + c1 - 48);
         }
      }

      return l;
   }

   public static String longToString(long l) {
      if (l < 0L) {
         return "invalid_name";
      } else {
         String s = "";

         while (l != 0L) {
            int i = (int)(l % 37L);
            l /= 37L;
            if (i == 0) {
               s = " " + s;
            } else if (i < 27) {
               if (l % 37L == 0L) {
                  s = (char)(i + 65 - 1) + s;
               } else {
                  s = (char)(i + 97 - 1) + s;
               }
            } else {
               s = (char)(i + 48 - 27) + s;
            }
         }

         return s;
      }
   }

   public static int getEntryOffset(String s, byte[] abyte0) {
      int i = getUnsigned2Bytes(abyte0, 0);
      int j = 0;
      s = s.toUpperCase();

      for (int k = 0; k < s.length(); k++) {
         j = j * 61 + s.charAt(k) - 32;
      }

      int l = 2 + i * 10;

      for (int i1 = 0; i1 < i; i1++) {
         int j1 = (abyte0[i1 * 10 + 2] & 255) * 16777216
            + (abyte0[i1 * 10 + 3] & 255) * 65536
            + (abyte0[i1 * 10 + 4] & 255) * 256
            + (abyte0[i1 * 10 + 5] & 255);
         int k1 = (abyte0[i1 * 10 + 9] & 255) * 65536 + (abyte0[i1 * 10 + 10] & 255) * 256 + (abyte0[i1 * 10 + 11] & 255);
         if (j1 == j) {
            return l;
         }

         l += k1;
      }

      return 0;
   }

   public static int getEntrySize(String s, byte[] abyte0) {
      int i = getUnsigned2Bytes(abyte0, 0);
      int j = 0;
      s = s.toUpperCase();

      for (int k = 0; k < s.length(); k++) {
         j = j * 61 + s.charAt(k) - 32;
      }

      int l = 2 + i * 10;

      for (int i1 = 0; i1 < i; i1++) {
         int j1 = (abyte0[i1 * 10 + 2] & 255) * 16777216
            + (abyte0[i1 * 10 + 3] & 255) * 65536
            + (abyte0[i1 * 10 + 4] & 255) * 256
            + (abyte0[i1 * 10 + 5] & 255);
         int k1 = (abyte0[i1 * 10 + 6] & 255) * 65536 + (abyte0[i1 * 10 + 7] & 255) * 256 + (abyte0[i1 * 10 + 8] & 255);
         int l1 = (abyte0[i1 * 10 + 9] & 255) * 65536 + (abyte0[i1 * 10 + 10] & 255) * 256 + (abyte0[i1 * 10 + 11] & 255);
         if (j1 == j) {
            return k1;
         }

         l += l1;
      }

      return 0;
   }
}
