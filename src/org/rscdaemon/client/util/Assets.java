package org.rscdaemon.client.util;

import java.util.HashMap;
import java.util.Map;

/*
 * The game's assets, held in memory for the life of the process.
 *
 * The client used to keep a cache directory on disk and read every asset out
 * of it. It now downloads the lot from Config.CACHE_URL on each
 * launch and keeps it here instead, so there is nothing on disk to go stale,
 * nothing to MD5 against the server, and no half-updated install to support.
 *
 * The cost of that is one download per launch, which is what the numbers make
 * affordable: 4.7 MB compressed. Note the *decompressed* total is 57 MB, so the
 * two zip archives (Landscape and Sprites) are deliberately kept packed and
 * inflated entry by entry -- see MemoryArchive. Store the bytes as downloaded
 * and nothing else, or the footprint goes up by an order of magnitude.
 */
public final class Assets {
   private static final Map<String, byte[]> FILES = new HashMap<String, byte[]>();

   private Assets() {
   }

   public static void put(String name, byte[] data) {
      FILES.put(name, data);
   }

   public static boolean has(String name) {
      return FILES.containsKey(name);
   }

   public static byte[] get(String name) {
      byte[] data = FILES.get(name);
      if (data == null) {
         throw new IllegalStateException("Game asset was never loaded: " + name);
      }

      return data;
   }

   /*
    * Release one asset once whatever needed it has finished. Worth doing for
    * the 2.3 MB loading splash, which is a quarter of the download and is only
    * ever handed to Toolkit.createImage once.
    */
   public static void drop(String name) {
      FILES.remove(name);
   }
}
