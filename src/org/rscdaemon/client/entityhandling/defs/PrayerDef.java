package org.rscdaemon.client.entityhandling.defs;

public class PrayerDef extends EntityDef {
   public int reqLevel;
   public int drainRate;

   public int getReqLevel() {
      return this.reqLevel;
   }

   public int getDrainRate() {
      return this.drainRate;
   }
}
