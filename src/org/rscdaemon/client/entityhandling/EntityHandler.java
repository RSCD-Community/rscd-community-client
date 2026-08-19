package org.rscdaemon.client.entityhandling;
import org.rscdaemon.client.util.Assets;

import java.io.File;
import java.util.ArrayList;
import org.rscdaemon.client.entityhandling.defs.DoorDef;
import org.rscdaemon.client.entityhandling.defs.ElevationDef;
import org.rscdaemon.client.entityhandling.defs.GameObjectDef;
import org.rscdaemon.client.entityhandling.defs.ItemDef;
import org.rscdaemon.client.entityhandling.defs.NPCDef;
import org.rscdaemon.client.entityhandling.defs.PrayerDef;
import org.rscdaemon.client.entityhandling.defs.SpellDef;
import org.rscdaemon.client.entityhandling.defs.TileDef;
import org.rscdaemon.client.entityhandling.defs.extras.AnimationDef;
import org.rscdaemon.client.entityhandling.defs.extras.TextureDef;
import org.rscdaemon.client.util.PersistenceManager;

public class EntityHandler {
   private static NPCDef[] npcs;
   private static ItemDef[] items;
   private static TextureDef[] textures;
   private static AnimationDef[] animations;
   private static SpellDef[] spells;
   private static PrayerDef[] prayers;
   private static TileDef[] tiles;
   private static DoorDef[] doors;
   private static ElevationDef[] elevation;
   private static GameObjectDef[] objects;
   private static ArrayList<String> models = new ArrayList<>();
   private static int invPictureCount = 0;

   public static int getModelCount() {
      return models.size();
   }

   public static String getModelName(int id) {
      return id >= 0 && id < models.size() ? models.get(id) : null;
   }

   public static int invPictureCount() {
      return invPictureCount;
   }

   public static int npcCount() {
      return npcs.length;
   }

   public static NPCDef getNpcDef(int id) {
      return id >= 0 && id < npcs.length ? npcs[id] : null;
   }

   public static int itemCount() {
      return items.length;
   }

   public static ItemDef getItemDef(int id) {
      return id >= 0 && id < items.length ? items[id] : null;
   }

   public static int textureCount() {
      return textures.length;
   }

   public static TextureDef getTextureDef(int id) {
      return id >= 0 && id < textures.length ? textures[id] : null;
   }

   public static int animationCount() {
      return animations.length;
   }

   public static AnimationDef getAnimationDef(int id) {
      return id >= 0 && id < animations.length ? animations[id] : null;
   }

   public static int spellCount() {
      return spells.length;
   }

   public static SpellDef getSpellDef(int id) {
      return id >= 0 && id < spells.length ? spells[id] : null;
   }

   /*
    * The spellbook and prayer panels show these lists sorted by required
    * level, not by def id. The two orders used to coincide, so the panels
    * just walked the def arrays -- but defs are only ever APPENDED (existing
    * ids are wire format: prayer toggles and spell casts send the id), so
    * any added def would otherwise render at the bottom of the list
    * regardless of its level. The sort is stable: same-level entries keep
    * their def order.
    */
   private static int[] spellOrder;
   private static int[] prayerOrder;

   private static int[] buildLevelOrder(int count, boolean forSpells) {
      Integer[] ids = new Integer[count];
      for (int x = 0; x < count; x++) {
         ids[x] = x;
      }
      java.util.Arrays.sort(ids, (a, b) -> {
         int la = forSpells ? spells[a].getReqLevel() : prayers[a].getReqLevel();
         int lb = forSpells ? spells[b].getReqLevel() : prayers[b].getReqLevel();
         return la != lb ? la - lb : a - b;
      });
      int[] order = new int[count];
      for (int x = 0; x < count; x++) {
         order[x] = ids[x];
      }
      return order;
   }

   /** Def id to show at each list position, sorted by required level. */
   public static int[] spellDisplayOrder() {
      if (spellOrder == null || spellOrder.length != spells.length) {
         spellOrder = buildLevelOrder(spells.length, true);
      }
      return spellOrder;
   }

   /** Def id to show at each list position, sorted by required level. */
   public static int[] prayerDisplayOrder() {
      if (prayerOrder == null || prayerOrder.length != prayers.length) {
         prayerOrder = buildLevelOrder(prayers.length, false);
      }
      return prayerOrder;
   }

   public static int prayerCount() {
      return prayers.length;
   }

   public static PrayerDef getPrayerDef(int id) {
      return id >= 0 && id < prayers.length ? prayers[id] : null;
   }

   public static int tileCount() {
      return tiles.length;
   }

   public static TileDef getTileDef(int id) {
      return id >= 0 && id < tiles.length ? tiles[id] : null;
   }

   public static int doorCount() {
      return doors.length;
   }

   public static DoorDef getDoorDef(int id) {
      return id >= 0 && id < doors.length ? doors[id] : null;
   }

   public static int elevationCount() {
      return elevation.length;
   }

   public static ElevationDef getElevationDef(int id) {
      return id >= 0 && id < elevation.length ? elevation[id] : null;
   }

   public static int objectCount() {
      return objects.length;
   }

   public static GameObjectDef getObjectDef(int id) {
      return id >= 0 && id < objects.length ? objects[id] : null;
   }

   /*
    * Was a File under the client's cache directory. There is no cache directory
    * any more -- assets are downloaded straight into memory each launch -- so
    * this hands back the bytes instead. The name is kept because it is all over
    * this class and renaming it is noise in an unrelated diff.
    */
   public static final byte[] FetchMahFile(String s) {
      return Assets.get(s);
   }

   public static void load() {
      npcs = (NPCDef[])PersistenceManager.load(FetchMahFile("NPCs.xml.data"));
      items = (ItemDef[])PersistenceManager.load(FetchMahFile("ItemDef.xml.data"));
      textures = (TextureDef[])PersistenceManager.load(FetchMahFile("Textures.xml.data"));
      animations = (AnimationDef[])PersistenceManager.load(FetchMahFile("Animations.xml.data"));
      spells = (SpellDef[])PersistenceManager.load(FetchMahFile("SpellDef.xml.data"));
      prayers = (PrayerDef[])PersistenceManager.load(FetchMahFile("Prayers.xml.data"));
      tiles = (TileDef[])PersistenceManager.load(FetchMahFile("Tiles.xml.data"));
      doors = (DoorDef[])PersistenceManager.load(FetchMahFile("Doors.xml.data"));
      elevation = (ElevationDef[])PersistenceManager.load(FetchMahFile("Elevation.xml.data"));
      objects = (GameObjectDef[])PersistenceManager.load(FetchMahFile("Objects.xml.data"));
      /*
       * models36.jag and sounds1.mem used to be read here too. Every other file
       * above is gzipped XStream XML; those two are Jagex's own packed archive
       * format, so PersistenceManager.load() -- which opens a GZIPInputStream
       * -- could only ever throw "Not in GZIP format", print it, and return
       * null. The result was assigned to nothing, so the two lines did no work
       * beyond the two warnings every launch.
       *
       * The real reads are mudclient.loadModels() and mudclient.loadSounds(),
       * which go through GameWindow.load(byte[]) and actually unpack them.
       */

      for (int id = 0; id < items.length; id++) {
         if (items[id].getSprite() + 1 > invPictureCount) {
            invPictureCount = items[id].getSprite() + 1;
         }
      }

      for (int idx = 0; idx < objects.length; idx++) {
         objects[idx].modelID = storeModel(objects[idx].getObjectModel());
      }
   }

   public static int storeModel(String name) {
      if (name.equalsIgnoreCase("na")) {
         return 0;
      } else {
         int index = models.indexOf(name);
         if (index < 0) {
            models.add(name);
            return models.size() - 1;
         } else {
            return index;
         }
      }
   }
}
