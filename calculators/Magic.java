/*
 * The magic skill calculator, in tip.it's classic shape: your current
 * experience (preloaded from your character, editable), a target level, and
 * then every spell with how many casts you would need -- green for the ones
 * your level can already do, red for the ones it cannot yet.
 *
 * The spells and their experience rates are tip.it Classic's own magic
 * calculator's (Magicexp, by Silverion), kept as that community recorded
 * them. An "M" suffix marks a members' spell, exactly as tip.it marked it.
 */
public class Magic extends Calculator {

   Input currentExp = number("Current exp").def(exp(MAGIC)).range(0, 999999999);
   Input target = number("Target level").def(Math.min(99, levelForXp(exp(MAGIC)) + 1)).range(2, 99);

   /* name, level required, experience per cast. */
   private static final Object[][] SPELLS = {
      { "Wind Strike", 1, 22.0 },
      { "Confuse", 3, 26.0 },
      { "Water Strike", 5, 30.0 },
      { "Enchant lvl 1 amulet", 7, 35.0 },
      { "Earth Strike", 9, 38.0 },
      { "Weaken", 11, 42.0 },
      { "Fire Strike", 13, 46.0 },
      { "Bones to Bananas", 15, 50.0 },
      { "Wind Bolt", 17, 54.0 },
      { "Curse", 19, 58.0 },
      { "Low Level Alchemy", 21, 62.0 },
      { "Water Bolt", 23, 66.0 },
      { "Varrock Teleport", 25, 70.0 },
      { "Enchant a lvl 2 amulet", 27, 74.0 },
      { "Earth Bolt", 29, 78.0 },
      { "Lumbridge Teleport", 31, 82.0 },
      { "Telekinetic grab", 33, 86.0 },
      { "Fire Bolt", 35, 90.0 },
      { "Falador Teleport", 37, 94.0 },
      { "Crumble Undead", 39, 98.0 },
      { "Wind Blast", 41, 102.0 },
      { "Superheat Item", 43, 106.0 },
      { "Camelot Teleport M", 45, 110.0 },
      { "Water Blast", 47, 114.0 },
      { "Enchant Level 3 Amulet", 49, 118.0 },
      { "Iban Blast M", 50, 120.0 },
      { "Ardougne teleport M", 51, 122.0 },
      { "Earth Blast", 53, 126.0 },
      { "High Level Alchemy", 55, 130.0 },
      { "Charge Water Orb M", 56, 132.0 },
      { "Enchant Level 4 Amulet", 57, 134.0 },
      { "Fire Blast", 59, 138.0 },
      { "Charge Earth Orb M", 60, 140.0 },
      { "Claws of Guthix M", 60, 140.0 },
      { "Sardomin Strike M", 60, 140.0 },
      { "Flames of Zamorak M", 60, 140.0 },
      { "Wind Wave M", 62, 144.0 },
      { "Charge Fire Orb M", 63, 146.0 },
      { "Water Wave M", 65, 150.0 },
      { "Charge Air Orb M", 66, 152.0 },
      { "Vulnerability M", 66, 152.0 },
      { "Enchant Level 5 Amulet M", 68, 156.0 },
      { "Earth Wave M", 70, 160.0 },
      { "Enfeeble M", 73, 166.0 },
      { "Fire Wave M", 75, 170.0 },
      { "Charge M", 80, 180.0 },
      { "Stun M", 80, 180.0 }
   };

   public Magic(mudclient mc) {
      super(mc);
   }

   public String about() {
      return "Rates from tip.it Classic's Magicexp.";
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

      Table t = out.table("Casts", "Name", "Lv.", "Exp");
      for (int i = 0; i < SPELLS.length; i++) {
         String name = (String)SPELLS[i][0];
         int req = ((Integer)SPELLS[i][1]).intValue();
         double per = ((Double)SPELLS[i][2]).doubleValue();
         long count = (long)Math.ceil(need / per);
         t.row(level >= req).cell(count).cell(name).cell(req).cell(per);
      }
   }
}
