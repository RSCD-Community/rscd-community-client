/*
 * The crafting skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every craftable item with how many you would need to make -- green
 * for the ones your level can already do, red for the ones it cannot yet.
 *
 * The items and their experience rates are tip.it Classic's own crafting
 * calculator's (Craftexp, by Silverion), kept as that community recorded
 * them.
 */
public class Crafting extends Calculator {

   Input currentExp = number("Current exp").def(exp(CRAFTING)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(CRAFTING)) + 1)).range(2, 99);

   /* name, level required, experience per item. */
   private static final Object[][] ITEMS = {
      { "Molten glass", 1, 20.0 },
      { "Beer Glass", 3, 17.5 },
      { "Vial", 33, 35.0 },
      { "Orb", 46, 52.5 },
      { "Spining Wool ball", 1, 2.5 },
      { "Flax - Bow string", 10, 15.0 },
      { "Pot", 1, 13.0 },
      { "Spin bow string", 1, 15.0 },
      { "Pie Dish", 4, 25.0 },
      { "Bowl", 7, 25.0 },
      { "Leather Gloves", 1, 13.5 },
      { "Leather Boots", 7, 16.5 },
      { "Leather Armor", 14, 25.0 },
      { "Holy Symbol", 16, 50.0 },
      { "Cut Sapphire", 20, 55.0 },
      { "Cut Emerald", 27, 67.5 },
      { "Cut Ruby", 34, 85.0 },
      { "Cut Diamond", 43, 107.5 },
      { "Gold ring", 5, 15.0 },
      { "Gold necklace", 6, 20.0 },
      { "Gold amulet", 8, 30.0 },
      { "Sapphire ring", 8, 40.0 },
      { "Sapphire Necklace", 10, 55.0 },
      { "Sapphire Amulet", 13, 65.0 },
      { "Emerald Ring", 18, 55.0 },
      { "Emerald Necklace", 24, 60.0 },
      { "Emerald Amulet", 30, 70.0 },
      { "Ruby Ring", 30, 70.0 },
      { "Ruby Necklace", 40, 75.0 },
      { "Ruby Amulet", 50, 85.0 },
      { "Diamond ring", 42, 85.0 },
      { "Diamond necklace", 56, 90.0 },
      { "Diamond Amulet", 70, 100.0 },
      { "Cut Dragonstone", 55, 127.5 },
      { "Dragonstone Amulet", 80, 150.0 }
   };

   public Crafting(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Craftexp.";
   }

   public void compute(Output out) {
      int have = currentExp.num();
      int goal = target.num();
      int need = xpForLevel(goal) - have;
      int level = levelForXp(have);

      if (need <= 0) {
         out.text("You already have level " + goal + " -- "
            + comma(have) + " exp is level " + level, GOOD);
         return;
      }

      out.text("For level " + goal + " you need " + comma(need) + " more exp", GOLD);
      out.text("You are level " + level + " with " + comma(have) + " exp", DIM);
      out.gap();

      Table t = out.table("Number", "Name", "Lv.", "Exp");
      for (int i = 0; i < ITEMS.length; i++) {
         String name = (String)ITEMS[i][0];
         int req = ((Integer)ITEMS[i][1]).intValue();
         double per = ((Double)ITEMS[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
