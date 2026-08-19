package org.rscdaemon.client.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * The client's copy of the wire text encodings -- the frequency-ordered
 * chat compression table and the base-37 username hash -- kept
 * byte-identical to the server's DataConversions (rscd-server,
 * org.rscdaemon.server.util), which carries the full commentary on how
 * each scheme works. Change one side only and chat or the friends list
 * silently garbles.
 */
public final class DataConversions {
   private static Random rand = new Random();
   private static char[] characters = new char[]{
      ' ',
      'e',
      't',
      'a',
      'o',
      'i',
      'h',
      'n',
      's',
      'r',
      'd',
      'l',
      'u',
      'm',
      'w',
      'c',
      'y',
      'f',
      'g',
      'p',
      'b',
      'v',
      'k',
      'x',
      'j',
      'q',
      'z',
      '0',
      '1',
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
      '8',
      '9',
      ' ',
      '!',
      '?',
      '.',
      ',',
      ':',
      ';',
      '(',
      ')',
      '-',
      '&',
      '*',
      '\\',
      '\'',
      '@',
      '#',
      '+',
      '=',
      '£',
      '$',
      '%',
      '"',
      '[',
      ']'
   };

   public static final ByteBuffer streamToBuffer(BufferedInputStream in) throws IOException {
      byte[] buffer = new byte[in.available()];
      in.read(buffer, 0, buffer.length);
      return ByteBuffer.wrap(buffer);
   }

   public static int average(int[] values) {
      int total = 0;

      for (int c = 0; c < values.length; c++) {
         total += values[c];
      }

      return total / values.length;
   }

   private static int getCharCode(char c) {
      for (int x = 0; x < characters.length; x++) {
         if (c == characters[x]) {
            return x;
         }
      }

      return 0;
   }

   public static byte[] stringToByteArray(String message) {
      byte[] buffer = new byte[100];
      if (message.length() > 80) {
         message = message.substring(0, 80);
      }

      message = message.toLowerCase();
      int length = 0;
      int j = -1;

      for (int k = 0; k < message.length(); k++) {
         int code = getCharCode(message.charAt(k));
         if (code > 12) {
            code += 195;
         }

         if (j == -1) {
            if (code < 13) {
               j = code;
            } else {
               buffer[length++] = (byte)code;
            }
         } else if (code < 13) {
            buffer[length++] = (byte)((j << 4) + code);
            j = -1;
         } else {
            buffer[length++] = (byte)((j << 4) + (code >> 4));
            j = code & 15;
         }
      }

      if (j != -1) {
         buffer[length++] = (byte)(j << 4);
      }

      byte[] string = new byte[length];
      System.arraycopy(buffer, 0, string, 0, length);
      return string;
   }

   public static String byteToString(byte[] data, int offset, int length) {
      char[] buffer = new char[100];

      try {
         int k = 0;
         int l = -1;

         for (int i1 = 0; i1 < length; i1++) {
            int j1 = data[offset++] & 255;
            int k1 = j1 >> 4 & 15;
            if (l == -1) {
               if (k1 < 13) {
                  buffer[k++] = characters[k1];
               } else {
                  l = k1;
               }
            } else {
               buffer[k++] = characters[(l << 4) + k1 - 195];
               l = -1;
            }

            k1 = j1 & 15;
            if (l == -1) {
               if (k1 < 13) {
                  buffer[k++] = characters[k1];
               } else {
                  l = k1;
               }
            } else {
               buffer[k++] = characters[(l << 4) + k1 - 195];
               l = -1;
            }
         }

         boolean flag = true;

         for (int l1 = 0; l1 < k; l1++) {
            char c = buffer[l1];
            if (l1 > 4 && c == '@') {
               buffer[l1] = ' ';
            }

            if (c == '%') {
               buffer[l1] = ' ';
            }

            if (flag && c >= 'a' && c <= 'z') {
               buffer[l1] += '￠';
               flag = false;
            }

            if (c == '.' || c == '!' || c == ':') {
               flag = true;
            }
         }

         return new String(buffer, 0, k);
      } catch (Exception var9) {
         return ".";
      }
   }

   public static boolean inArray(int[] haystack, int needle) {
      for (int c = 0; c < haystack.length; c++) {
         if (needle == haystack[c]) {
            return true;
         }
      }

      return false;
   }

   public static int max(int i1, int i2) {
      return i1 > i2 ? i1 : i2;
   }

   public static int random(int low, int high) {
      return low + rand.nextInt(high - low + 1);
   }

   public static int randomWeighted(int low, int dip, int peak, int max) {
      int total = 0;
      int probability = 100;
      int[] probArray = new int[max + 1];

      for (int x = 0; x < probArray.length; x++) {
         total += probArray[x] = probability;
         if (x >= dip && x <= peak) {
            probability += 3;
         } else {
            probability -= 3;
         }
      }

      int hit = random(0, total);
      total = 0;

      for (int xx = 0; xx < probArray.length; xx++) {
         if (hit >= total && hit < total + probArray[xx]) {
            return xx;
         }

         total += probArray[xx];
      }

      return 0;
   }

   public static long usernameToHash(String s) {
      s = s.toLowerCase();
      String s1 = "";

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c >= 'a' && c <= 'z') {
            s1 = s1 + c;
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

   public static String hashToUsername(long l) {
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

}
