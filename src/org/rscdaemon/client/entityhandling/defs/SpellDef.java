package org.rscdaemon.client.entityhandling.defs;

import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

public class SpellDef extends EntityDef {
   public int reqLevel;
   public int type;
   public int runeCount;
   public HashMap<Integer, Integer> requiredRunes;
   public int exp;

   public int getReqLevel() {
      return this.reqLevel;
   }

   public int getSpellType() {
      return this.type;
   }

   public int getRuneCount() {
      return this.runeCount;
   }

   public Set<Entry<Integer, Integer>> getRunesRequired() {
      return this.requiredRunes.entrySet();
   }

   public int getExp() {
      return this.exp;
   }
}
