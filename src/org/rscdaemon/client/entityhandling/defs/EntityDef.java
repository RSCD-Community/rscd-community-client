package org.rscdaemon.client.entityhandling.defs;

public abstract class EntityDef {
   public String name;
   public String description;

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }
}
