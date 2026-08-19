package org.rscdaemon.client.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/*
 * Loads the gzipped entity-definition documents out of the cache.
 *
 * This was XStream. It is now XmlObjects, which reads the same documents and
 * produces the same objects -- see that class for why, and for the shapes the
 * cache actually contains. The alias table below is unchanged from the one
 * XStream was given, minus one entry: org.rscdaemon.spriteeditor.Sprite, which
 * mapped an old sprite-editor document onto client.model.Sprite. No file in the
 * cache uses it (Sprites.xml.data is one of Jagex's packed zip archives, read by
 * MemoryArchive, and never went through here at all).
 *
 * The writer went with it. XStream's toXML had exactly one caller,
 * Sprite.serializeTo, and that had none of its own -- along with
 * Sprite.deserializeFrom, it was sprite-editor API that nothing in the client
 * reaches. Reimplementing an XML writer to keep a method nobody calls would have
 * been the largest part of this change and the least useful.
 */
public class PersistenceManager {

   /** Reads an asset held in memory -- the path EntityHandler uses. */
   public static Object load(byte[] data) {
      try {
         return read(new GZIPInputStream(new ByteArrayInputStream(data)));
      } catch (IOException e) {
         System.err.println(e.getMessage());
         return null;
      }
   }

   /** Reads one off disk. Kept for anything driving the client from a folder. */
   public static Object load(File file) {
      try {
         return read(new GZIPInputStream(new FileInputStream(file)));
      } catch (IOException e) {
         System.err.println(e.getMessage());
         return null;
      }
   }

   private static Object read(InputStream in) throws IOException {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
         byte[] buffer = new byte[8192];

         int count;
         while ((count = in.read(buffer)) > 0) {
            out.write(buffer, 0, count);
         }

         return XmlObjects.read(new String(out.toByteArray(), "UTF-8"));
      } finally {
         in.close();
      }
   }

   static {
      XmlObjects.alias("NPCDef", "org.rscdaemon.client.entityhandling.defs.NPCDef");
      XmlObjects.alias("ItemDef", "org.rscdaemon.client.entityhandling.defs.ItemDef");
      XmlObjects.alias("TextureDef", "org.rscdaemon.client.entityhandling.defs.extras.TextureDef");
      XmlObjects.alias("AnimationDef", "org.rscdaemon.client.entityhandling.defs.extras.AnimationDef");
      XmlObjects.alias("ItemDropDef", "org.rscdaemon.client.entityhandling.defs.extras.ItemDropDef");
      XmlObjects.alias("SpellDef", "org.rscdaemon.client.entityhandling.defs.SpellDef");
      XmlObjects.alias("PrayerDef", "org.rscdaemon.client.entityhandling.defs.PrayerDef");
      XmlObjects.alias("TileDef", "org.rscdaemon.client.entityhandling.defs.TileDef");
      XmlObjects.alias("DoorDef", "org.rscdaemon.client.entityhandling.defs.DoorDef");
      XmlObjects.alias("ElevationDef", "org.rscdaemon.client.entityhandling.defs.ElevationDef");
      XmlObjects.alias("GameObjectDef", "org.rscdaemon.client.entityhandling.defs.GameObjectDef");
   }
}
