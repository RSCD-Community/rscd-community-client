package org.rscdaemon.client.entityhandling.defs.extras;

public class AnimationDef {
   public String name;
   public int charColour;
   public int genderModel;
   public boolean hasA;
   public boolean hasF;
   public int number;

   public String getName() {
      return this.name;
   }

   public int getCharColour() {
      return this.charColour;
   }

   public int getGenderModel() {
      return this.genderModel;
   }

   public boolean hasA() {
      return this.hasA;
   }

   public boolean hasF() {
      return this.hasF;
   }

   public int getNumber() {
      return this.number;
   }
}
