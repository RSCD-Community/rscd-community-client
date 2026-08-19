package org.rscdaemon.client.entityhandling.defs;

public class GameObjectDef extends EntityDef {
   public String command1;
   public String command2;
   public int type;
   public int width;
   public int height;
   public int groundItemVar;
   public String objectModel;
   public int modelID;

   public String getObjectModel() {
      return this.objectModel;
   }

   public String getCommand1() {
      return this.command1.toLowerCase();
   }

   public String getCommand2() {
      return this.command2.toLowerCase();
   }

   public int getType() {
      return this.type;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public int getGroundItemVar() {
      return this.groundItemVar;
   }
}
