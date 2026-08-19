package org.rscdaemon.client.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/*
 * settings.ini, read and written back without destroying it.
 *
 * Config reads the same file through java.util.Properties, which is fine for
 * reading and useless for writing: Properties.store() discards every comment,
 * reorders every key and escapes characters that were never escaped going in.
 * The player's settings.ini is a file a human is expected to open and edit, so
 * a save that scrambles it is worse than no save at all.
 *
 * So this keeps the file as the list of lines it actually is. A set() rewrites
 * the value in place on the line that already carries the key -- comments,
 * blank lines, key order and the player's own formatting all survive. A key
 * that is not present yet is appended at the end.
 *
 * Nothing here throws on a missing or unwritable file. The client has to keep
 * running when settings.ini cannot be written (read-only install directory, a
 * jar run from a mounted image), it just cannot remember anything across
 * launches -- so save() reports that as a boolean rather than an exception.
 */
public final class Settings {
   private final File file;
   private final List<String> lines = new ArrayList<String>();
   /* False once a save has failed, so the client stops retrying every change. */
   private boolean writable = true;

   private Settings(File file) {
      this.file = file;
   }

   /*
    * Reads the file if it is there. A file that does not exist yet is not an
    * error -- it is a fresh install, and the first save() creates it.
    */
   public static Settings load(File file) {
      Settings settings = new Settings(file);
      if (file != null && file.isFile()) {
         try {
            FileInputStream stream = new FileInputStream(file);
            try {
               BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));

               String line;
               while ((line = reader.readLine()) != null) {
                  settings.lines.add(line);
               }
            } finally {
               stream.close();
            }
         } catch (IOException var4) {
            System.out.println("Could not read " + file.getPath() + ": " + var4.getMessage());
         }
      }

      return settings;
   }

   public File getFile() {
      return this.file;
   }

   public String get(String key, String fallback) {
      for (int i = 0; i < this.lines.size(); ++i) {
         if (keyOf(this.lines.get(i)) != null && keyOf(this.lines.get(i)).equals(key)) {
            return valueOf(this.lines.get(i));
         }
      }

      return fallback;
   }

   public int getInt(String key, int fallback) {
      try {
         String value = this.get(key, null);
         return value == null || value.length() == 0 ? fallback : Integer.parseInt(value.trim());
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   public boolean getBoolean(String key, boolean fallback) {
      String value = this.get(key, null);
      if (value == null) {
         return fallback;
      } else {
         value = value.trim();
         return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1");
      }
   }

   /*
    * Sets a value in memory. Call save() to put it on disk -- callers that
    * change several keys at once should not write the file several times.
    */
   public void set(String key, String value) {
      String line = key + "=" + (value == null ? "" : value);

      for (int i = 0; i < this.lines.size(); ++i) {
         String existing = this.lines.get(i);
         if (keyOf(existing) != null && keyOf(existing).equals(key)) {
            this.lines.set(i, line);
            return;
         }
      }

      this.lines.add(line);
   }

   public void set(String key, int value) {
      this.set(key, Integer.toString(value));
   }

   public void set(String key, boolean value) {
      this.set(key, value ? "true" : "false");
   }

   /*
    * Writes the file, returning whether it worked. Once it has failed the
    * settings stay live for this session and are simply not persisted; the
    * caller does not have to care which, so there is nothing to handle beyond
    * telling the player their choice will not be remembered.
    */
   public boolean save() {
      if (this.file == null || !this.writable) {
         return false;
      } else {
         try {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
               parent.mkdirs();
            }

            FileOutputStream stream = new FileOutputStream(this.file);

            try {
               Writer writer = new OutputStreamWriter(stream, "UTF-8");

               for (int i = 0; i < this.lines.size(); ++i) {
                  writer.write(this.lines.get(i));
                  writer.write("\n");
               }

               writer.flush();
            } finally {
               stream.close();
            }

            return true;
         } catch (IOException var7) {
            System.out.println("Could not write " + this.file.getPath() + ": " + var7.getMessage());
            this.writable = false;
            return false;
         }
      }
   }

   public boolean isWritable() {
      return this.writable;
   }

   /*
    * The key a line carries, or null if it carries none. Properties treats
    * '#' and '!' as comments and accepts '=' or ':' as the separator, and this
    * file is still read by Properties elsewhere, so match that rather than
    * inventing a second dialect for the same file.
    */
   private static String keyOf(String line) {
      String trimmed = line.trim();
      if (trimmed.length() == 0 || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') {
         return null;
      } else {
         int split = separator(trimmed);
         return split < 0 ? null : trimmed.substring(0, split).trim();
      }
   }

   private static String valueOf(String line) {
      String trimmed = line.trim();
      int split = separator(trimmed);
      return split < 0 ? "" : trimmed.substring(split + 1).trim();
   }

   private static int separator(String trimmed) {
      for (int i = 0; i < trimmed.length(); ++i) {
         char c = trimmed.charAt(i);
         if (c == '=' || c == ':') {
            return i;
         }
      }

      return -1;
   }
}
