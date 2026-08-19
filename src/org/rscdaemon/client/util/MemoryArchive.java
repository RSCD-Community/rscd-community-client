package org.rscdaemon.client.util;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/*
 * Random access into a zip archive held in memory.
 *
 * java.util.zip gives you a choice of ZipFile, which needs a real file on disk,
 * or ZipInputStream, which can only go forwards. Neither works once the assets
 * stop being files: Landscape.xml.data is 1764 sectors and the client pulls
 * four of them out whenever the player crosses a region boundary, so it needs
 * lookup by name, on demand, for the life of the process.
 *
 * So this reads the central directory once and remembers where each entry
 * starts, then inflates one entry at a time on request. What stays resident is
 * the archive exactly as downloaded -- 914 KB for Landscape, against the 39 MB
 * it would take to hold every sector unpacked.
 *
 * Only the parts of the format these archives actually use are handled: stored
 * and deflated entries, no zip64, no encryption. Anything else is refused
 * loudly rather than silently mis-read.
 */
public final class MemoryArchive {
   private static final int EOCD_SIGNATURE = 0x06054B50;
   private static final int CENTRAL_SIGNATURE = 0x02014B50;
   private static final int LOCAL_SIGNATURE = 0x04034B50;
   private static final int STORED = 0;
   private static final int DEFLATED = 8;
   /* End of central directory record, before any trailing comment. */
   private static final int EOCD_LENGTH = 22;
   private static final int MAX_COMMENT = 0xFFFF;

   private final byte[] archive;
   private final Map<String, int[]> entries = new HashMap<String, int[]>();

   public MemoryArchive(byte[] archive) {
      this.archive = archive;
      this.readCentralDirectory();
   }

   public boolean has(String name) {
      return this.entries.containsKey(name);
   }

   /** The named entry, inflated, or null if the archive does not hold it. */
   public byte[] get(String name) {
      int[] entry = this.entries.get(name);
      if (entry == null) {
         return null;
      }

      int method = entry[0];
      int compressedSize = entry[1];
      int size = entry[2];
      int localHeader = entry[3];

      if (this.u32(localHeader) != LOCAL_SIGNATURE) {
         throw new IllegalStateException("Bad local header for " + name);
      }

      // The extra field is allowed to differ in length between the central
      // directory and the local header, so the data offset has to come from
      // the local header rather than being computed from the central one.
      int start = localHeader + 30 + this.u16(localHeader + 26) + this.u16(localHeader + 28);
      if (method == STORED) {
         byte[] out = new byte[size];
         System.arraycopy(this.archive, start, out, 0, size);
         return out;
      }

      byte[] out = new byte[size];
      // nowrap: the archive holds a raw deflate stream with no zlib header.
      Inflater inflater = new Inflater(true);

      try {
         inflater.setInput(this.archive, start, compressedSize);
         int written = 0;

         while (written < size) {
            int n = inflater.inflate(out, written, size - written);
            if (n == 0) {
               if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()) {
                  throw new IllegalStateException("Truncated entry " + name + " (" + written + " of " + size + ")");
               }
            }

            written += n;
         }
      } catch (DataFormatException var13) {
         throw new IllegalStateException("Corrupt entry " + name, var13);
      } finally {
         inflater.end();
      }

      return out;
   }

   private void readCentralDirectory() {
      int eocd = this.findEndOfCentralDirectory();
      int count = this.u16(eocd + 10);
      int offset = this.u32(eocd + 16);

      for (int i = 0; i < count; i++) {
         if (this.u32(offset) != CENTRAL_SIGNATURE) {
            throw new IllegalStateException("Bad central directory entry " + i);
         }

         int method = this.u16(offset + 10);
         if (method != STORED && method != DEFLATED) {
            throw new IllegalStateException("Unsupported compression method " + method + " in entry " + i);
         }

         int compressedSize = this.u32(offset + 20);
         int size = this.u32(offset + 24);
         int nameLength = this.u16(offset + 28);
         int extraLength = this.u16(offset + 30);
         int commentLength = this.u16(offset + 32);
         int localHeader = this.u32(offset + 42);

         String name = new String(this.archive, offset + 46, nameLength);
         this.entries.put(name, new int[]{method, compressedSize, size, localHeader});
         offset += 46 + nameLength + extraLength + commentLength;
      }
   }

   /*
    * The record is at a fixed distance from the end unless the archive carries
    * a comment, which is variable length and has no length prefix ahead of it,
    * so the only way to find the record is to scan back for its signature.
    */
   private int findEndOfCentralDirectory() {
      int earliest = Math.max(0, this.archive.length - EOCD_LENGTH - MAX_COMMENT);

      for (int i = this.archive.length - EOCD_LENGTH; i >= earliest; i--) {
         if (this.u32(i) == EOCD_SIGNATURE) {
            return i;
         }
      }

      throw new IllegalStateException("Not a zip archive (no end of central directory record)");
   }

   private int u16(int offset) {
      return this.archive[offset] & 255 | (this.archive[offset + 1] & 255) << 8;
   }

   private int u32(int offset) {
      return this.archive[offset] & 255
         | (this.archive[offset + 1] & 255) << 8
         | (this.archive[offset + 2] & 255) << 16
         | (this.archive[offset + 3] & 255) << 24;
   }
}
