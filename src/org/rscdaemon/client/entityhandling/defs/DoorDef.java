package org.rscdaemon.client.entityhandling.defs;

public class DoorDef extends EntityDef {
   public String command1;
   public String command2;
   public int doorType;
   public int unknown;
   public int modelVar1;
   public int modelVar2;
   public int modelVar3;

   public String getCommand1() {
      return this.command1.toLowerCase();
   }

   public String getCommand2() {
      return this.command2.toLowerCase();
   }

   public int getDoorType() {
      return this.doorType;
   }

   public int getUnknown() {
      return this.unknown;
   }

   public int getModelVar1() {
      return this.modelVar1;
   }

   public int getModelVar2() {
      return this.modelVar2;
   }

   public int getModelVar3() {
      return this.modelVar3;
   }
}
