package org.rscdaemon.client.entityhandling.defs;

import org.rscdaemon.client.entityhandling.defs.extras.ItemDropDef;

/**
 * What the client knows about an NPC type. The sprites array is the twelve
 * appearance layers -- the same slots a player avatar is drawn from, each
 * an animation index or -1 for empty -- with the colour fields tinting the
 * hair/top/bottom/skin layers, which is how NPCs and players share one
 * renderer. The def format is shared with the server, so the server-side
 * fields (drops, respawnTime, aggressive, the combat stats beyond what the
 * examine/attack menus show) ride along here unused by the renderer.
 */
public class NPCDef extends EntityDef {
   public String command;
   public int[] sprites;
   public int hairColour;
   public int topColour;
   public int bottomColour;
   public int skinColour;
   public int camera1;
   public int camera2;
   public int walkModel;
   public int combatModel;
   public int combatSprite;
   public int hits;
   public int attack;
   public int defense;
   public int strength;
   public boolean attackable;
   public int respawnTime;
   public boolean aggressive;
   public ItemDropDef[] drops;

   public String getCommand() {
      return this.command;
   }

   public int getSprite(int index) {
      return this.sprites[index];
   }

   public int getHairColour() {
      return this.hairColour;
   }

   public int getTopColour() {
      return this.topColour;
   }

   public int getBottomColour() {
      return this.bottomColour;
   }

   public int getSkinColour() {
      return this.skinColour;
   }

   public int getCamera1() {
      return this.camera1;
   }

   public int getCamera2() {
      return this.camera2;
   }

   public int getWalkModel() {
      return this.walkModel;
   }

   public int getCombatModel() {
      return this.combatModel;
   }

   public int getCombatSprite() {
      return this.combatSprite;
   }

   public int getHits() {
      return this.hits;
   }

   public int getAtt() {
      return this.attack;
   }

   public int getDef() {
      return this.defense;
   }

   public int getStr() {
      return this.strength;
   }

   public int[] getStats() {
      return new int[]{this.attack, this.defense, this.strength};
   }

   public boolean isAttackable() {
      return this.attackable;
   }

   public int respawnTime() {
      return this.respawnTime;
   }

   public boolean isAggressive() {
      return this.aggressive;
   }
}
